package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.entities.TimeSeries;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class NotificationSemanticsTest {

    @Test
    public void concurrentReadersGetUnblockedWhenWriterAddsData() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-notify-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 5;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        // ensure day 1 does not exist yet
        int day = 1;

        int readers = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers);
        ExecutorService ex = Executors.newFixedThreadPool(readers + 1);

        for (int i = 0; i < readers; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    // attempt to read repeatedly until writer writes
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    boolean saw = false;
                    while (System.nanoTime() < deadline && !saw) {
                        TimeSeries ts = smm.getDayData(day, 100);
                        if (ts != null && !ts.isEmpty()) {
                            saw = true;
                        } else {
                            Thread.sleep(20);
                        }
                    }
                    assertTrue(saw, "Reader did not see data after writer added it");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        // start readers
        start.countDown();

        // small pause to ensure readers are waiting
        Thread.sleep(100);

        // writer thread writes the event
        ex.submit(() -> {
            try {
                Event e = new Event("NotifyProd", 1, 2.5f, day);
                sp.saveEvento(e, "NotifyProd", day);
                // touch memory manager to ensure load
                smm.getDayData(day, 100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        boolean finished = done.await(6, TimeUnit.SECONDS);
        ex.shutdownNow();
        assertTrue(finished, "Not all readers observed the writer's update in time");
    }
}
