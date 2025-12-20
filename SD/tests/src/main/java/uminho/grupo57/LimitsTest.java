package uminho.grupo57;

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
    
    // TODO: Implementar teste de limite D
    public static void testDayLimit() {
        System.out.println("\n=== TESTE: Limite D (Dias Históricos) ===");
        System.out.println("TODO: Validar que apenas últimos D dias são mantidos");
        System.out.println("      Passos:");
        System.out.println("      1. Iniciar servidor com D=10");
        System.out.println("      2. Adicionar eventos e avançar 15 dias");
        System.out.println("      3. Verificar que dia 1-5 não existem mais");
        System.out.println("      4. Verificar que agregações de dias antigos falham");
        System.out.println("      5. Verificar ficheiros em disco (apenas D mais recentes)");
        
        // Exemplo de implementação:
        // TimeSeriesClient client = new TimeSeriesClient();
        // client.connect(SERVER_HOST, SERVER_PORT);
        // client.login("user1", "pass");
        // 
        // for (int day = 0; day < 15; day++) {
        //     client.addEvent("Produto1", 10, 5.0f);
        //     client.nextDay(); // Apenas admin pode
        // }
        // 
        // // Tentar agregar dia muito antigo (deve falhar)
        // Map<String, Object> stats = client.aggregateRange("Produto1", 12);
        // assert stats.isEmpty() || stats.get("quantidade_total") == 0;
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    // TODO: Implementar teste de limite S
    public static void testSeriesMemoryLimit() {
        System.out.println("\n=== TESTE: Limite S (Séries em Memória) ===");
        System.out.println("TODO: Validar que no máximo S séries ficam em memória");
        System.out.println("      Passos:");
        System.out.println("      1. Iniciar servidor com D=30, S=5");
        System.out.println("      2. Criar 10 users, adicionar eventos em 10 dias");
        System.out.println("      3. Fazer agregações que carregam > S séries");
        System.out.println("      4. Instrumentar SeriesMemoryManager para contar séries");
        System.out.println("      5. Verificar que tamanho do Map nunca > S");
        
        System.out.println("\n   INSTRUMENTAÇÃO NECESSÁRIA:");
        System.out.println("   - Adicionar método getLoadedSeriesCount() em SeriesMemoryManager");
        System.out.println("   - Adicionar contador de cache hits/misses");
        System.out.println("   - Logar quando série é evicted do cache");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
    }
    
    // TODO: Implementar teste de LRU
    public static void testLRUEviction() {
        System.out.println("\n=== TESTE: Política LRU (Least Recently Used) ===");
        System.out.println("TODO: Verificar que séries menos usadas são evicted");
        System.out.println("      Passos:");
        System.out.println("      1. Carregar S+3 séries diferentes");
        System.out.println("      2. Fazer múltiplas agregações nas mesmas 2 séries");
        System.out.println("      3. Verificar que essas 2 séries ficam 'hot' em memória");
        System.out.println("      4. Verificar que outras são evicted");
        System.out.println("      5. Re-acesso a série evicted deve recarregar (miss)");
        
        System.out.println("✗ NÃO IMPLEMENTADO - implementar lógica acima");
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
