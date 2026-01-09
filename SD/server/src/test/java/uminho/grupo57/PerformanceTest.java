package uminho.grupo57;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

/**
 * Testes de Desempenho do Sistema de Séries Temporais
 *
 * Implementa cenários de carga/robustez/notificações/persistência em modo
 * determinístico para execução rápida em CI.
 */
public class PerformanceTest {
    
    // Configuração dos testes
    private static final int D = 30; // Dias históricos
    private static final int S = 10; // Séries em memória

    private static void cleanupDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
        }
    }

    private static void waitServerUp(int port) throws InterruptedException {
        boolean up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                up = true;
                break;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        assertTrue(up, "Server did not start in time");
    }
    
    // Métricas
    private static class Metrics {
        private final Lock lock = new ReentrantLock();
        private int successCount = 0;
        private int errorCount = 0;
        private long totalLatency = 0;
        private final List<Long> latencies = new ArrayList<>();
        
        void recordSuccess(long latencyMs) {
            lock.lock();
            try {
                successCount++;
                totalLatency += latencyMs;
                latencies.add(latencyMs);
            } finally {
                lock.unlock();
            }
        }
        
        void recordError() {
            lock.lock();
            try {
                errorCount++;
            } finally {
                lock.unlock();
            }
        }
        
        void printReport(String testName, long durationMs) {
            final int success;
            final int error;
            final long total;
            final List<Long> latenciesSnapshot;
            lock.lock();
            try {
                success = successCount;
                error = errorCount;
                total = totalLatency;
                latenciesSnapshot = new ArrayList<>(latencies);
            } finally {
                lock.unlock();
            }

            System.out.println("\n=== " + testName + " ===");
            System.out.println("Duração total: " + durationMs + "ms");
            System.out.println("Operações bem-sucedidas: " + success);
            System.out.println("Operações com erro: " + error);
            
            if (success > 0) {
                double avgLatency = total / (double) success;
                double throughput = (success * 1000.0) / durationMs;
                
                System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/s");
                System.out.println("Latência média: " + String.format("%.2f", avgLatency) + "ms");
                
                // Calcular percentis
                List<Long> sorted = new ArrayList<>(latenciesSnapshot);
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
            lock.lock();
            try {
                successCount = 0;
                errorCount = 0;
                totalLatency = 0;
                latencies.clear();
            } finally {
                lock.unlock();
            }
        }
    }
    
    public static void testScalability() throws Exception {
        System.out.println("\n### TESTE DE ESCALABILIDADE (simulado) ###");
        int[] clientCounts = {1, 5, 10, 20};
        int operationsPerClient = 200;

        for (int numClients : clientCounts) {
            System.out.println("\n--- Testando com " + numClients + " clientes ---");

            Metrics metrics = new Metrics();
            ExecutorService executor = Executors.newFixedThreadPool(numClients);
            CountDownLatch latch = new CountDownLatch(numClients);

            long startTime = System.currentTimeMillis();

            Path tmp = Files.createTempDirectory("ts-perf-scale-");

            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                executor.submit(() -> {
                    try {
                        // Each simulated client performs repeated lightweight aggregations
                        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
                        SeriesMemoryManager smm = new SeriesMemoryManager(10, sp);
                        long localStart = System.nanoTime();
                        for (int op = 0; op < operationsPerClient; op++) {
                            // alternate days and products
                            smm.getAggregationCache("produto" + (op % 3), 3, 10);
                        }
                        long localDur = System.nanoTime() - localStart;
                        metrics.recordSuccess(localDur / 1000000);
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

            cleanupDir(tmp);
        }
    }
    
    public static void testRobustness() throws Exception {
        System.out.println("\n### TESTE DE ROBUSTEZ (socket) ###");

        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path tempDir = Files.createTempDirectory("ts-perf-robust-");
        final int runnerThreads = 24;
        final Server srv = new Server(port, D, S, runnerThreads, 4, tempDir.toString());
        Thread st = new Thread(() -> {
            try {
                srv.start(runnerThreads, tempDir.toString());
            } catch (IOException ignored) {
            }
        });
        st.start();
        waitServerUp(port);

        // Bad client: sends requests but does not read responses.
        Thread bad = new Thread(() -> {
            try (Socket sock = new Socket("127.0.0.1", port);
                 TaggedConnection conn = new TaggedConnection(sock)) {
                String user = "bad" + System.nanoTime();
                conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
                conn.receive();
                conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
                conn.receive();

                // Send some requests without reading back
                for (int i = 0; i < 25; i++) {
                    conn.send(1000 + i, new Protocol.Message(Protocol.ADD_EVENT, "R", "1", "1.0"));
                }
                // Keep the socket open briefly so server can flush responses.
                Thread.sleep(750);
            } catch (Exception ignored) {
            }
        });
        bad.start();

        // Good clients should still complete promptly
        int goodClients = 5;
        ExecutorService ex = Executors.newFixedThreadPool(goodClients);
        CountDownLatch done = new CountDownLatch(goodClients);

        for (int i = 0; i < goodClients; i++) {
            ex.submit(() -> {
                try (Socket sock = new Socket("127.0.0.1", port);
                     TaggedConnection conn = new TaggedConnection(sock)) {
                    String user = "g" + System.nanoTime();
                    conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
                    TaggedConnection.Frame r1 = conn.receive();
                    assertEquals(Protocol.OK, r1.data.type);

                    conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
                    TaggedConnection.Frame r2 = conn.receive();
                    assertEquals(Protocol.OK, r2.data.type);

                    conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "G", "1", "2.0"));
                    TaggedConnection.Frame r3 = conn.receive();
                    assertEquals(Protocol.OK, r3.data.type);

                    conn.send(4, new Protocol.Message(Protocol.QUERY_PRODUCT, "G"));
                    TaggedConnection.Frame r4 = conn.receive();
                    assertEquals(Protocol.OK, r4.data.type);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean finished = done.await(10, TimeUnit.SECONDS);
        ex.shutdownNow();

        // Let the bad client linger a bit before shutdown
        try { bad.join(1500); } catch (InterruptedException ignored) {}

        try { srv.stop(); } catch (IOException ignored) {}
        st.join(2000);
        cleanupDir(tempDir);

        assertTrue(finished, "Good clients did not finish in time under bad client pressure");
    }
    
    public static void testWorkloadMix() throws Exception {
        System.out.println("\n### TESTE DE DIFERENTES CARGAS DE TRABALHO (simulado) ###");

        Path tmp = Files.createTempDirectory("ts-perf-workload-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // Seed some history for reads (days 1..10)
        for (int d = 1; d <= 10; d++) {
            for (int i = 0; i < 25; i++) {
                sp.saveEvento(new Event("P" + (i % 5), 1 + (i % 3), 1.0f + (i % 7), d), "P" + (i % 5), d);
            }
        }

        runWorkloadScenario("Write-heavy", sp, 8, 150, 0);
        runWorkloadScenario("Read-heavy", sp, 8, 150, 100);
        runWorkloadScenario("Mixed 70% reads", sp, 8, 150, 70);

        // Heavy aggregation: more days and more iterations
        runHeavyAggregationScenario(sp);

        // Heavy filter: many products
        runHeavyFilterScenario(sp);

        cleanupDir(tmp);
    }
    
    public static void testNotifications() throws Exception {
        System.out.println("\n### TESTE DE NOTIFICAÇÕES BLOQUEANTES (server) ###");

        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path tempDir = Files.createTempDirectory("ts-perf-notify-");
        final int runnerThreads = 24;
        final Server srv = new Server(port, 10, 10, runnerThreads, 4, tempDir.toString());
        Thread st = new Thread(() -> {
            try {
                srv.start(runnerThreads, tempDir.toString());
            } catch (IOException ignored) {
            }
        });
        st.start();
        waitServerUp(port);

        String user = "u" + System.nanoTime();
        // register once
        try (Socket sock = new Socket("127.0.0.1", port);
             TaggedConnection conn = new TaggedConnection(sock)) {
            conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
            TaggedConnection.Frame r = conn.receive();
            assertEquals(Protocol.OK, r.data.type);
        }

        ExecutorService ex = Executors.newFixedThreadPool(3);
        CountDownLatch waitingSent = new CountDownLatch(2);

        Future<Boolean> sim = ex.submit(() -> {
            try (Socket sock = new Socket("127.0.0.1", port);
                 TaggedConnection conn = new TaggedConnection(sock)) {
                conn.send(1, new Protocol.Message(Protocol.LOGIN, user, "p"));
                TaggedConnection.Frame login = conn.receive();
                assertEquals(Protocol.OK, login.data.type);
                conn.send(2, new Protocol.Message(Protocol.WAIT_SIMULTANEOUS, "A", "B"));
                waitingSent.countDown();
                TaggedConnection.Frame r = conn.receive();
                assertEquals(Protocol.OK, r.data.type);
                return r.data.args.length >= 1 && "true".equals(r.data.args[0]);
            }
        });

        Future<Boolean> cons = ex.submit(() -> {
            try (Socket sock = new Socket("127.0.0.1", port);
                 TaggedConnection conn = new TaggedConnection(sock)) {
                conn.send(1, new Protocol.Message(Protocol.LOGIN, user, "p"));
                TaggedConnection.Frame login = conn.receive();
                assertEquals(Protocol.OK, login.data.type);
                conn.send(2, new Protocol.Message(Protocol.WAIT_CONSECUTIVE, "C", "2"));
                waitingSent.countDown();
                TaggedConnection.Frame r = conn.receive();
                assertEquals(Protocol.OK, r.data.type);
                return r.data.args.length >= 1 && "true".equals(r.data.args[0]);
            }
        });

        Future<?> writer = ex.submit(() -> {
            try {
                boolean ready = waitingSent.await(4, TimeUnit.SECONDS);
                assertTrue(ready, "Wait requests were not sent in time");

                try (Socket sock = new Socket("127.0.0.1", port);
                     TaggedConnection conn = new TaggedConnection(sock)) {
                    conn.send(1, new Protocol.Message(Protocol.LOGIN, user, "p"));
                    TaggedConnection.Frame login = conn.receive();
                    assertEquals(Protocol.OK, login.data.type);

                    conn.send(2, new Protocol.Message(Protocol.ADD_EVENT, "A", "1", "1.0"));
                    conn.receive();
                    conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "B", "1", "1.0"));
                    conn.receive();
                    conn.send(4, new Protocol.Message(Protocol.ADD_EVENT, "C", "1", "1.0"));
                    conn.receive();
                    conn.send(5, new Protocol.Message(Protocol.ADD_EVENT, "C", "1", "1.0"));
                    conn.receive();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Ensure writer didn't crash
        writer.get(8, TimeUnit.SECONDS);
        boolean simOk = sim.get(8, TimeUnit.SECONDS);
        boolean consOk = cons.get(8, TimeUnit.SECONDS);

        ex.shutdownNow();

        try { srv.stop(); } catch (IOException ignored) {}
        st.join(2000);
        cleanupDir(tempDir);

        assertTrue(simOk && consOk, "Notification waits returned false");
    }
    
    public static void testPersistenceAndMemory() throws Exception {
        System.out.println("\n### TESTE DE PERSISTÊNCIA E GESTÃO DE MEMÓRIA (simulado) ###");

        Path tmp = Files.createTempDirectory("ts-perf-persist-mem-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // Persist 12 days of data to disk
        for (int d = 1; d <= 12; d++) {
            for (int i = 0; i < 10; i++) {
                String prod = "M" + (i % 3);
                sp.saveEvento(new Event(prod, 1, 1.0f, d), prod, d);
            }
        }

        int maxInMemory = 3;
        SeriesMemoryManager smm = new SeriesMemoryManager(maxInMemory, sp);

        // Touch many days; this should force disk loads and evictions
        for (int d = 1; d <= 12; d++) {
            smm.getDayData(d, 12);
        }

        assertTrue(smm.getLoadedSeriesCount() <= maxInMemory, "Loaded series should not exceed S");
        assertTrue(smm.getLoadFromDiskCount() > 0, "Should load at least one day from disk");
        assertTrue(smm.getEvictions() > 0, "Should evict when loading more than S days");

        // Access an older day again; it may trigger another disk load depending on LRU
        int before = smm.getLoadFromDiskCount();
        smm.getDayData(1, 12);
        assertTrue(smm.getLoadFromDiskCount() >= before, "Disk load count should be monotonic");

        cleanupDir(tmp);
    }

    private static void runWorkloadScenario(String name, SeriesPersistence sp, int clients, int opsPerClient, int readPercent)
            throws InterruptedException {
        Metrics metrics = new Metrics();
        ExecutorService ex = Executors.newFixedThreadPool(clients);
        CountDownLatch latch = new CountDownLatch(clients);

        long start = System.currentTimeMillis();

        for (int i = 0; i < clients; i++) {
            ex.submit(() -> {
                try {
                    SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);
                    long t0 = System.nanoTime();
                    for (int op = 0; op < opsPerClient; op++) {
                        int r = (int) (Math.random() * 100);
                        if (r < readPercent) {
                            smm.getAggregationCache("P" + (op % 5), 3, 10);
                        } else {
                            smm.addEventToCurrentDay(new Event("W" + (op % 4), 1, 1.0f, 10), 10);
                        }
                    }
                    metrics.recordSuccess((System.nanoTime() - t0) / 1_000_000);
                } catch (Exception e) {
                    metrics.recordError();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        ex.shutdownNow();
        assertTrue(finished, "Workload scenario did not finish in time: " + name);
        metrics.printReport(name, System.currentTimeMillis() - start);
    }

    private static void runHeavyAggregationScenario(SeriesPersistence sp) throws InterruptedException {
        runWorkloadScenario("Heavy aggregation", sp, 6, 200, 100);
    }

    private static void runHeavyFilterScenario(SeriesPersistence sp) throws Exception {
        Metrics metrics = new Metrics();
        long start = System.currentTimeMillis();
        SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);

        long t0 = System.nanoTime();
        for (int i = 0; i < 60; i++) {
            smm.getEventsForProducts(Set.of("P0", "P1", "P2", "P3", "P4"), 10, 10);
        }
        metrics.recordSuccess((System.nanoTime() - t0) / 1_000_000);
        metrics.printReport("Heavy filter", System.currentTimeMillis() - start);
        assertTrue(true);
    }
    
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TESTES DE DESEMPENHO - SÉRIES TEMPORAIS");
        System.out.println("=================================================");
        
        try {
            testScalability();
            testRobustness();
            testWorkloadMix();
            testNotifications();
            testPersistenceAndMemory();
            System.out.println("\n=================================================");
            System.out.println("  TESTES CONCLUÍDOS - Analisar resultados!");
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("Erro durante testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
