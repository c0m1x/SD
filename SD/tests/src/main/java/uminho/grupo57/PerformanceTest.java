package uminho.grupo57;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Testes de Desempenho do Sistema de Séries Temporais
 * 
 * TODO: Implementar os seguintes cenários de teste conforme requisitos do enunciado:
 * 
 * 1. TESTE DE ESCALABILIDADE
 *    - Objetivo: Verificar desempenho com aumento do número de clientes
 *    - Implementar: Começar com 1 cliente e ir aumentando (5, 10, 20, 50, 100 clientes)
 *    - Medir: throughput (operações/segundo), latência média, latência p95, latência máxima
 *    - Cada cliente deve fazer múltiplas operações (ex: 100 ADD_EVENT + 50 QUERY_PRODUCT)
 *    - Anotar quando começam a aparecer degradações de desempenho
 * 
 * 2. TESTE DE ROBUSTEZ
 *    - Objetivo: Verificar o que acontece quando cliente não consome respostas
 *    - Implementar: Cliente que envia requests mas não lê responses do socket
 *    - Verificar: Sistema continua a funcionar? Buffers enchem? Outros clientes afetados?
 *    - Testar com 1 cliente "mau" e vários clientes "bons" em simultâneo
 *    - Documentar comportamento observado
 * 
 * 3. TESTE DE DIFERENTES CARGAS DE TRABALHO
 *    - Cenário A: Apenas escritas (ADD_EVENT intensivo)
 *    - Cenário B: Apenas leituras (QUERY_PRODUCT, AGGREGATE_RANGE)
 *    - Cenário C: Misto (70% leituras, 30% escritas)
 *    - Cenário D: Agregações pesadas (AGGREGATE_RANGE com d=D máximo)
 *    - Cenário E: Filtros pesados (FILTER_EVENTS com muitos produtos)
 *    - Comparar desempenho entre cenários
 * 
 * 4. TESTE DE NOTIFICAÇÕES BLOQUEANTES
 *    - Testar WAIT_SIMULTANEOUS com múltiplas threads
 *    - Testar WAIT_CONSECUTIVE com diferentes valores de n
 *    - Verificar que notificações funcionam corretamente sob carga
 *    - Medir overhead das notificações no sistema
 * 
 * 5. TESTE DE PERSISTÊNCIA E MEMÓRIA
 *    - Testar com S pequeno (ex: S=3) e muitos dias D (ex: D=30)
 *    - Forçar carregamento de séries do disco
 *    - Verificar que limite S é respeitado
 *    - Medir tempo de carregamento do disco vs memória
 * 
 * ESTRUTURA SUGERIDA:
 *    - Cada teste deve ter setup (iniciar servidor, criar clientes)
 *    - Executar operações e medir tempos
 *    - Calcular estatísticas (média, mediana, p95, max)
 *    - Cleanup (fechar clientes, parar servidor)
 *    - Imprimir resultados em formato tabela
 * 
 * IMPORTANTE: Documentar TODOS os resultados no relatório!
 */
public class PerformanceTest {
    
    // Configuração dos testes
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final int D = 30; // Dias históricos
    private static final int S = 10; // Séries em memória
    
    // Métricas
    private static class Metrics {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final AtomicLong totalLatency = new AtomicLong(0);
        private final List<Long> latencies = new CopyOnWriteArrayList<>();
        
        void recordSuccess(long latencyMs) {
            successCount.incrementAndGet();
            totalLatency.addAndGet(latencyMs);
            latencies.add(latencyMs);
        }
        
        void recordError() {
            errorCount.incrementAndGet();
        }
        
        void printReport(String testName, long durationMs) {
            System.out.println("\n=== " + testName + " ===");
            System.out.println("Duração total: " + durationMs + "ms");
            System.out.println("Operações bem-sucedidas: " + successCount.get());
            System.out.println("Operações com erro: " + errorCount.get());
            
            if (successCount.get() > 0) {
                double avgLatency = totalLatency.get() / (double) successCount.get();
                double throughput = (successCount.get() * 1000.0) / durationMs;
                
                System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/s");
                System.out.println("Latência média: " + String.format("%.2f", avgLatency) + "ms");
                
                // Calcular percentis
                List<Long> sorted = new ArrayList<>(latencies);
                Collections.sort(sorted);
                
                if (!sorted.isEmpty()) {
                    int p50Idx = Math.max(0, (int) (sorted.size() * 0.50) - 1);
                    int p95Idx = Math.max(0, (int) (sorted.size() * 0.95) - 1);
                    int p99Idx = Math.max(0, (int) (sorted.size() * 0.99) - 1);
                    
                    System.out.println("Latência p50: " + sorted.get(p50Idx) + "ms");
                    System.out.println("Latência p95: " + sorted.get(p95Idx) + "ms");
                    System.out.println("Latência p99: " + sorted.get(p99Idx) + "ms");
                    System.out.println("Latência max: " + sorted.get(sorted.size() - 1) + "ms");
                }
            }
        }
        
        void reset() {
            successCount.set(0);
            errorCount.set(0);
            totalLatency.set(0);
            latencies.clear();
        }
    }
    
    // TODO: Implementar teste de escalabilidade
    public static void testScalability() throws Exception {
        System.out.println("\n### TESTE DE ESCALABILIDADE ###");
        System.out.println("TODO: Implementar teste com 1, 5, 10, 20, 50, 100 clientes concorrentes");
        System.out.println("      Medir throughput e latência para cada configuração");
        System.out.println("      Gerar gráfico mostrando escalabilidade do sistema");
        
        int[] clientCounts = {1, 5, 10, 20, 50};
        int operationsPerClient = 100;
        
        for (int numClients : clientCounts) {
            System.out.println("\n--- Testando com " + numClients + " clientes ---");
            
            Metrics metrics = new Metrics();
            ExecutorService executor = Executors.newFixedThreadPool(numClients);
            CountDownLatch latch = new CountDownLatch(numClients);
            
            long startTime = System.currentTimeMillis();
            
            // TODO: Implementar lógica aqui
            // 1. Criar numClients instâncias de TimeSeriesClient
            // 2. Cada cliente faz N operações em paralelo
            // 3. Medir tempo total e calcular métricas
            // 4. Imprimir resultados
            
            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                executor.submit(() -> {
                    try {
                        // TODO: Criar TimeSeriesClient e executar operações
                        // Exemplo:
                        // TimeSeriesClient client = new TimeSeriesClient(SERVER_HOST, SERVER_PORT);
                        // for (int op = 0; op < operationsPerClient; op++) {
                        //     long opStart = System.currentTimeMillis();
                        //     client.addEvent(...);
                        //     long opEnd = System.currentTimeMillis();
                        //     metrics.recordSuccess(opEnd - opStart);
                        // }
                        // client.close();
                        
                        System.out.println("Cliente " + clientId + " completado (placeholder)");
                    } catch (Exception e) {
                        metrics.recordError();
                        System.err.println("Cliente " + clientId + " falhou: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            long endTime = System.currentTimeMillis();
            
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            
            metrics.printReport("Escalabilidade com " + numClients + " clientes", endTime - startTime);
        }
    }
    
    // TODO: Implementar teste de robustez
    public static void testRobustness() throws Exception {
        System.out.println("\n### TESTE DE ROBUSTEZ ###");
        System.out.println("TODO: Criar cliente que envia requests mas não lê responses");
        System.out.println("      Verificar se sistema continua funcional");
        System.out.println("      Testar com 1 cliente problemático + N clientes normais");
        System.out.println("      Documentar comportamento: buffers enchem? timeout? outros afetados?");
        
        // TODO: Implementar cliente "mau"
        // Thread badClient = new Thread(() -> {
        //     try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
        //         // Enviar muitos requests sem ler responses
        //         OutputStream out = socket.getOutputStream();
        //         for (int i = 0; i < 1000; i++) {
        //             // Enviar comando sem ler resposta
        //             out.write("ADD_EVENT ...\n".getBytes());
        //             Thread.sleep(10);
        //         }
        //     } catch (Exception e) {
        //         e.printStackTrace();
        //     }
        // });
        // badClient.start();
        
        // TODO: Criar clientes normais em paralelo
        // Verificar se continuam funcionando
        
        System.out.println("Teste de robustez não implementado ainda.");
    }
    
    // TODO: Implementar testes de diferentes cargas
    public static void testWorkloadMix() throws Exception {
        System.out.println("\n### TESTE DE DIFERENTES CARGAS DE TRABALHO ###");
        System.out.println("TODO: Implementar cenários A, B, C, D, E descritos acima");
        System.out.println("      Comparar desempenho entre workloads");
        System.out.println("      Identificar operações mais custosas");
        
        // Cenário A: Write-heavy
        System.out.println("\n--- Cenário A: Apenas Escritas ---");
        // TODO: 100% ADD_EVENT
        
        // Cenário B: Read-heavy
        System.out.println("\n--- Cenário B: Apenas Leituras ---");
        // TODO: 100% QUERY_PRODUCT
        
        // Cenário C: Mixed
        System.out.println("\n--- Cenário C: Misto (70% leituras, 30% escritas) ---");
        // TODO: Mix de operações
        
        // Cenário D: Agregações pesadas
        System.out.println("\n--- Cenário D: Agregações Pesadas ---");
        // TODO: AGGREGATE_RANGE com d máximo
        
        // Cenário E: Filtros pesados
        System.out.println("\n--- Cenário E: Filtros Pesados ---");
        // TODO: FILTER_EVENTS com muitos produtos
    }
    
    // TODO: Implementar teste de notificações
    public static void testNotifications() throws Exception {
        System.out.println("\n### TESTE DE NOTIFICAÇÕES BLOQUEANTES ###");
        System.out.println("TODO: Testar WAIT_SIMULTANEOUS e WAIT_CONSECUTIVE");
        System.out.println("      Verificar comportamento com múltiplas threads esperando");
        System.out.println("      Medir overhead das notificações");
        System.out.println("      Testar edge cases: dia termina antes de condição satisfeita");
    }
    
    // TODO: Implementar teste de persistência
    public static void testPersistenceAndMemory() throws Exception {
        System.out.println("\n### TESTE DE PERSISTÊNCIA E GESTÃO DE MEMÓRIA ###");
        System.out.println("TODO: Iniciar servidor com S pequeno (ex: 3) e D grande (ex: 30)");
        System.out.println("      Adicionar eventos em múltiplos dias");
        System.out.println("      Forçar leitura do disco com agregações");
        System.out.println("      Verificar que limite S é respeitado");
        System.out.println("      Medir diferença de tempo: memória vs disco");
    }
    
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TESTES DE DESEMPENHO - SÉRIES TEMPORAIS");
        System.out.println("=================================================");
        System.out.println("\nNOTA: Antes de executar, certifique-se que:");
        System.out.println("  1. Servidor está rodando em " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println("  2. Parâmetros: D=" + D + ", S=" + S);
        System.out.println("\nATENÇÃO: Todos os TODOs devem ser implementados!");
        System.out.println("          Resultados devem ir para o RELATÓRIO!\n");
        
        try {
            // Descomentar à medida que implementa
            testScalability();
            // testRobustness();
            // testWorkloadMix();
            // testNotifications();
            // testPersistenceAndMemory();
            
            System.out.println("\n=================================================");
            System.out.println("  TESTES CONCLUÍDOS - Analisar resultados!");
            System.out.println("=================================================");
            
        } catch (Exception e) {
            System.err.println("Erro durante testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}