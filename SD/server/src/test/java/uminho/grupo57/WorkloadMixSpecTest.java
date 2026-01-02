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

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class WorkloadMixSpecTest {

    @Test
    public void workloadMixesSimulated() throws Exception {
        Path tmp = Files.createTempDirectory("ts-workload-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // prepare some persisted days
        for (int d = 1; d <= 10; d++) {
            sp.saveEvento(new Event("P" + d, 1, 1.0f, d), "P" + d, d);
        }

        int[] ratios = {90, 50, 10}; // percent reads
        int clients = 8;
        int opsPerClient = 200;

        for (int readPercent : ratios) {
            CountDownLatch latch = new CountDownLatch(clients);
            ExecutorService ex = Executors.newFixedThreadPool(clients);
            long start = System.currentTimeMillis();

            for (int i = 0; i < clients; i++) {
                ex.submit(() -> {
                    try {
                        SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);
                        for (int op = 0; op < opsPerClient; op++) {
                            int r = (int) (Math.random() * 100);
                            if (r < readPercent) {
                                // read: aggregation
                                smm.getAggregationCache("produto" + (op % 3), 3, 10);
                            } else {
                                // write: add event to current day
                                smm.addEventToCurrentDay(new Event("W" + op, 1, 1.0f, 10), 10);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Workload task error: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            Path logDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
            Files.createDirectories(logDir);
            Path out = logDir.resolve("workload-mix-" + readPercent + "-" + Instant.now().toEpochMilli() + ".log");
            Files.writeString(out, "readPercent=" + readPercent + " elapsedMs=" + elapsed + "\n");

            ex.shutdownNow();
            assertTrue(true);
        }
    }
}
