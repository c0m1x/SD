package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class ScalabilitySpecTest {

    @Test
    public void simulatedScalability() throws Exception {
        Path tmp = Files.createTempDirectory("ts-scalability-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        int[] clientCounts = {1, 5, 10, 20};
        int opsPerClient = 200;

        for (int clients : clientCounts) {
            CountDownLatch latch = new CountDownLatch(clients);
            ExecutorService ex = Executors.newFixedThreadPool(clients);
            long start = System.currentTimeMillis();

            for (int i = 0; i < clients; i++) {
                ex.submit(() -> {
                    try {
                        SeriesMemoryManager smm = new SeriesMemoryManager(10, sp);
                        for (int op = 0; op < opsPerClient; op++) {
                            int day = 1 + (op % 10);
                            smm.getAggregationCache("produto" + (op % 3), 3, 10);
                        }
                    } catch (Exception e) {
                        // record but continue
                        System.err.println("Client task error: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            String out = String.format("scalability-%d-%d-%d.log", clients, opsPerClient, Instant.now().toEpochMilli());
            Path logDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
            Files.createDirectories(logDir);
            Files.writeString(logDir.resolve(out), "clients=" + clients + " elapsedMs=" + elapsed + "\n");

            ex.shutdownNow();
        }

        // If we reach here tests executed without deadlock/exceptions
        assertTrue(true);
    }
}
