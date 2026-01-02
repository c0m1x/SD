package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class ConcurrencyStressTest {

    @Test
    public void manyConcurrentWritersAndReadersRespectLimits() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-stress-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 10;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        int writers = 8;
        int readers = 8;
        int opsPerWriter = 200;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService ex = Executors.newFixedThreadPool(writers + readers);

        for (int w = 0; w < writers; w++) {
            ex.submit(() -> {
                try {
                    Random r = new Random();
                    start.await();
                    for (int i = 0; i < opsPerWriter; i++) {
                        int day = 1 + r.nextInt(20);
                        String prod = "P" + (1 + r.nextInt(30));
                        Event e = new Event(prod, 1, (float) r.nextDouble(), day);
                        sp.saveEvento(e, prod, day);
                        if ((i & 31) == 0) smm.getDayData(day, 100);
                    }
                } catch (Exception exx) { throw new RuntimeException(exx); }
            });
        }

        for (int r = 0; r < readers; r++) {
            ex.submit(() -> {
                try {
                    Random rnd = new Random();
                    start.await();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                    while (System.nanoTime() < deadline) {
                        int day = 1 + rnd.nextInt(20);
                        smm.getDayData(day, 100);
                        Thread.sleep(2);
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }

        start.countDown();
        ex.shutdown();
        boolean finished = ex.awaitTermination(15, TimeUnit.SECONDS);

        assertTrue(finished, "Stress test threads did not finish in time");
        assertTrue(smm.getLoadedSeriesCount() <= S, "Loaded series should respect limit S");
    }
}
