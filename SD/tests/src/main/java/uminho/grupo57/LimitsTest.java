package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;
/**
 * Teste de Limites D (Dias Históricos) e S (Séries em Memória)
 * 
 * TODO: Validar que os limites D e S funcionam corretamente conforme enunciado:
 * 
 * CONTEXTO DO ENUNCIADO (Secção 7):
 * "Admita que cada série pode ter biliões de eventos e não é viável ter todas as 
 * séries dos D dias anteriores em memória. Persista a série de cada dia para disco 
 * e mantenha em memória, para além do dia corrente, no máximo S séries, com S < D 
 * um parâmetro de inicialização do servidor."
 * 
 * IMPLEMENTAÇÃO ATUAL:
 * - SeriesMemoryManager.java implementa LRU para limitar S séries
 * - SeriesPersistence.java persiste séries para disco
 * - Parâmetros D e S são configuráveis na inicialização
 * 
 * TESTES A IMPLEMENTAR:
 * 
 * 1. TESTE DE LIMITE D (Dias Históricos)
 *    - Iniciar servidor com D=10
 *    - Adicionar eventos e avançar 15 dias
 *    - Verificar que apenas últimos 10 dias são mantidos
 *    - Tentar agregar dia 11+ → deve falhar ou retornar vazio
 *    - Verificar que ficheiros de dias antigos são removidos
 * 
 * 2. TESTE DE LIMITE S (Séries em Memória)
 *    - Iniciar servidor com D=30, S=5
 *    - Criar 10 utilizadores diferentes
 *    - Adicionar eventos para cada user em vários dias
 *    - Forçar agregações que precisam carregar séries do disco
 *    - Verificar que no máximo S séries estão em memória
 *    - Usar instrumentação ou logs para contar séries carregadas
 * 
 * 3. TESTE DE LRU (Least Recently Used)
 *    - Carregar S+3 séries diferentes
 *    - Fazer operações que acessam apenas 2 séries repetidamente
 *    - Verificar que essas 2 ficam em memória (hot)
 *    - Verificar que séries não usadas são evicted
 *    - Re-acesso a série evicted deve recarregar do disco
 * 
 * 4. TESTE DE PERFORMANCE: MEMÓRIA vs DISCO
 *    - Medir tempo de agregação quando série está em memória
 *    - Medir tempo de agregação quando série precisa ser carregada do disco
 *    - Documentar diferença (esperado: disco 10-100x mais lento)
 *    - Verificar que cache funciona (2ª leitura é rápida)
 * 
 * 5. TESTE DE CONCORRÊNCIA COM LIMITE S
 *    - Múltiplos clientes fazendo agregações simultaneamente
 *    - Cada agregação requer séries diferentes (forçar evictions)
 *    - Verificar que limite S nunca é ultrapassado
 *    - Verificar que não há race conditions no carregamento
 * 
 * 6. TESTE DE PERSISTÊNCIA APÓS NEXT_DAY
 *    - Adicionar eventos no dia N
 *    - Chamar nextDay() → avança para dia N+1
 *    - Verificar que dia N foi persistido para disco
 *    - Parar e reiniciar servidor
 *    - Verificar que dados do dia N ainda existem
 * 
 * 7. TESTE DE LIMITE COM SÉRIES GRANDES
 *    - Criar séries com milhares de eventos (simular "biliões")
 *    - Verificar que agregações on-demand funcionam
 *    - Séries grandes devem poder ser processadas sem carregar tudo
 *    - Documentar uso de memória durante processamento
 * 
 * IMPLEMENTAÇÃO SUGERIDA:
 * - Usar servidor de teste isolado (não produção)
 * - Configurar D e S pequenos para testes rápidos
 * - Adicionar logs/instrumentação em SeriesMemoryManager
 * - Contar chamadas a loadFromDisk() vs hits em memória
 * - Verificar tamanho do Map seriesInMemory
 * 
 * MÉTRICAS A COLETAR:
 * - Hit rate da cache (acessos em memória / total acessos)
 * - Número de evictions por teste
 * - Tempo médio: acesso memória vs disco
 * - Memória usada (heap size) durante testes
 * 
 * DOCUMENTAR NO RELATÓRIO:
 * - Estratégia de gestão de memória (LRU)
 * - Performance: memória vs disco
 * - Validação dos limites D e S
 * - Comportamento sob concorrência
 */
public class LimitsTest {
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    
    @Test
    public void testDayLimit() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-day-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // create events for days 1..15
        for (int d = 1; d <= 15; d++) {
            Event e = new Event("ProdutoA", 1, 1.0f, d);
            sp.saveEvento(e, "ProdutoA", d);
        }

        // enforce max 10 days — currentDay = 15
        sp.deleteOldestDayIfLowerThanMax(10, 15);

        // days 1..5 should be removed
        for (int d = 1; d <= 5; d++)
            assertFalse(sp.exists(d), "Old day should be deleted: " + d);

        // days 6..15 should exist
        for (int d = 6; d <= 15; d++)
            assertTrue(sp.exists(d), "Recent day should exist: " + d);
    }
    
    @Test
    public void testSeriesMemoryLimit() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-s-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 5;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        // create and persist 10 days of data
        for (int d = 1; d <= 10; d++) {
            Event e = new Event("Prod" + d, 1, 1.0f, d);
            sp.saveEvento(e, "Prod" + d, d);
        }

        // access all 10 days to force loads
        for (int d = 1; d <= 10; d++)
            smm.getDayData(d, 10);

        assertTrue(smm.getLoadedSeriesCount() <= S, "Loaded series should not exceed S");
    }
    
    @Test
    public void testLRUEviction() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-lru-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 5;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        // persist S+3 days
        for (int d = 1; d <= S + 3; d++) {
            Event e = new Event("P" + d, 1, 1.0f, d);
            sp.saveEvento(e, "P" + d, d);
        }

        // access first two repeatedly to keep them hot
        for (int i = 0; i < 5; i++) {
            smm.getDayData(1, S + 3);
            smm.getDayData(2, S + 3);
        }

        // access the remaining to force eviction
        for (int d = 3; d <= S + 3; d++)
            smm.getDayData(d, S + 3);

        // There should have been at least one eviction
        assertTrue(smm.getEvictions() > 0, "There should be evictions");
        // Hot days should be present (access again should not increase loadFromDisk)
        int before = smm.getLoadFromDiskCount();
        smm.getDayData(1, S + 3);
        smm.getDayData(2, S + 3);
        assertEquals(before, smm.getLoadFromDiskCount(), "Hot days should not trigger disk loads");
    }
    
    // TODO: Implementar comparação memória vs disco
    public static void testMemoryVsDiskPerformance() {
        System.out.println("\n=== TESTE: Performance Memória vs Disco ===");
        System.out.println("TODO: Medir diferença de tempo entre acessos");
        System.out.println("      Passos:");
        System.out.println("      1. Carregar série em memória");
        System.out.println("      2. Fazer 100 agregações → medir tempo (memória)");
        System.out.println("      3. Forçar eviction da série");
        System.out.println("      4. Fazer 1 agregação → medir tempo (disco)");
        System.out.println("      5. Calcular ratio: tempo_disco / tempo_memória");
        System.out.println("      6. Esperado: disco 10-100x mais lento");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    // TODO: Implementar teste de concorrência
    public static void testConcurrentAccessWithLimitS() {
        System.out.println("\n=== TESTE: Concorrência com Limite S ===");
        System.out.println("TODO: Múltiplos clientes acessando séries diferentes");
        System.out.println("      Passos:");
        System.out.println("      1. Criar 20 threads, cada uma acessa série diferente");
        System.out.println("      2. Todas executam agregações simultaneamente");
        System.out.println("      3. Total de séries > S (forçar evictions)");
        System.out.println("      4. Verificar que limite S é sempre respeitado");
        System.out.println("      5. Verificar que não há race conditions");
        System.out.println("      6. Todas threads devem completar sem erros");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    // TODO: Implementar teste de persistência
    public static void testPersistenceAfterNextDay() {
        System.out.println("\n=== TESTE: Persistência Após nextDay() ===");
        System.out.println("TODO: Verificar que dados são persistidos corretamente");
        System.out.println("      Passos:");
        System.out.println("      1. Adicionar 1000 eventos no dia corrente");
        System.out.println("      2. Chamar nextDay()");
        System.out.println("      3. Verificar que ficheiro do dia anterior existe");
        System.out.println("      4. Verificar tamanho do ficheiro > 0");
        System.out.println("      5. Fazer agregação do dia anterior (carrega do disco)");
        System.out.println("      6. Verificar que dados estão corretos");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    // TODO: Implementar teste com séries grandes
    public static void testLargeSeriesProcessing() {
        System.out.println("\n=== TESTE: Processamento de Séries Grandes ===");
        System.out.println("TODO: Validar que séries muito grandes podem ser processadas");
        System.out.println("      Passos:");
        System.out.println("      1. Adicionar 100.000 eventos num único dia");
        System.out.println("      2. Avançar dia (persiste para disco)");
        System.out.println("      3. Fazer agregação que processa a série");
        System.out.println("      4. Monitorar uso de memória durante processamento");
        System.out.println("      5. Verificar que agregação completa sem OutOfMemory");
        System.out.println("      6. Documentar tempo de processamento");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("  TESTES DE LIMITES D e S");
        System.out.println("=====================================================");
        System.out.println("\nOBJETIVO: Validar gestão de memória e persistência");
        System.out.println("          conforme requisitos do enunciado (Secção 7)");
        System.out.println("\nPRÉ-REQUISITOS:");
        System.out.println("  1. Servidor rodando com D e S configuráveis");
        System.out.println("  2. Acesso admin para nextDay()");
        System.out.println("  3. Instrumentação em SeriesMemoryManager (opcional)");
        System.out.println("\nMÉTRICAS A COLETAR:");
        System.out.println("  - Cache hit rate");
        System.out.println("  - Número de evictions");
        System.out.println("  - Tempo médio: memória vs disco");
        System.out.println("  - Uso de memória heap");
        System.out.println("\nDOCUMENTAR NO RELATÓRIO:");
        System.out.println("  - Estratégia LRU funciona corretamente");
        System.out.println("  - Limites D e S são respeitados");
        System.out.println("  - Performance: quantificar diferença memória/disco");
        System.out.println("  - Sistema suporta séries grandes\n");
        
        try {
            // Descomentar à medida que implementa
            // testDayLimit();
            // testSeriesMemoryLimit();
            // testLRUEviction();
            // testMemoryVsDiskPerformance();
            // testConcurrentAccessWithLimitS();
            // testPersistenceAfterNextDay();
            // testLargeSeriesProcessing();
            
            System.out.println("\n=====================================================");
            System.out.println("  TESTES CONCLUÍDOS - Analisar resultados!");
            System.out.println("=====================================================");
            
        } catch (Exception e) {
            System.err.println("Erro durante testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
