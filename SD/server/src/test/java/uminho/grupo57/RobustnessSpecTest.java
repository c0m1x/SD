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

public class RobustnessSpecTest {

    @Test
    public void simulatedBadClientDoesNotBringDownSystem() throws Exception {
        Path tmp = Files.createTempDirectory("ts-robustness-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // Start a "bad" writer thread that continuously writes events
        volatileWriteStarter(sp);

        // Meanwhile run multiple clients doing reads; they should complete
        int clients = 6;
        int opsPerClient = 200;
        CountDownLatch latch = new CountDownLatch(clients);
        ExecutorService ex = Executors.newFixedThreadPool(clients);

        for (int i = 0; i < clients; i++) {
            ex.submit(() -> {
                try {
                    SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);
                    for (int op = 0; op < opsPerClient; op++) {
                        smm.getAggregationCache("produto" + (op % 4), 3, 10);
                    }
                } catch (Exception e) {
                    System.err.println("Reader task error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        ex.shutdownNow();

        Path logDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
        Files.createDirectories(logDir);
        Path out = logDir.resolve("robustness-" + (finished ? "ok" : "timeout") + "-" + Instant.now().toEpochMilli() + ".log");
        Files.writeString(out, "finished=" + finished + "\n");

        assertTrue(finished, "Reader clients should complete despite bad writer");
    }

    private void volatileWriteStarter(SeriesPersistence sp) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    sp.saveEvento(new Event("Bad" + i, 1, 1.0f, 1), "Bad" + i, 1);
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // ignore
            }
        }, "bad-writer").start();
    }
}
