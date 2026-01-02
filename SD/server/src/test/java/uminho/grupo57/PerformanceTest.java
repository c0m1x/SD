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
 * TODO: Implementar os cenários descritos no ficheiro original.
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
            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                executor.submit(() -> {
                    try {
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
        
        System.out.println("Teste de robustez não implementado ainda.");
    }
    
    // TODO: Implementar testes de diferentes cargas
    public static void testWorkloadMix() throws Exception {
        System.out.println("\n### TESTE DE DIFERENTES CARGAS DE TRABALHO ###");
        System.out.println("TODO: Implementar cenários A, B, C, D, E descritos acima");
        System.out.println("      Comparar desempenho entre workloads");
        System.out.println("      Identificar operações mais custosas");
    }
    
    // TODO: Implementar teste de notificações
    public static void testNotifications() throws Exception {
        System.out.println("\n### TESTE DE NOTIFICAÇÕES BLOQUEANTES ###");
        System.out.println("TODO: Testar WAIT_SIMULTANEOUS e WAIT_CONSECUTIVE");
    }
    
    // TODO: Implementar teste de persistência
    public static void testPersistenceAndMemory() throws Exception {
        System.out.println("\n### TESTE DE PERSISTÊNCIA E GESTÃO DE MEMÓRIA ###");
        System.out.println("TODO: Iniciar servidor com S pequeno (ex: 3) e D grande (ex: 30)");
    }
    
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TESTES DE DESEMPENHO - SÉRIES TEMPORAIS");
        System.out.println("=================================================");
        
        try {
            testScalability();
            System.out.println("\n=================================================");
            System.out.println("  TESTES CONCLUÍDOS - Analisar resultados!");
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("Erro durante testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
