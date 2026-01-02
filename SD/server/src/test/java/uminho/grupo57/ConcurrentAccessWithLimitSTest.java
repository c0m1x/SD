package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class ConcurrentAccessWithLimitSTest {

    @Test
    public void testConcurrentAccessRespectsLimitS() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-concurrent-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        // prepare persisted days/products: 1..20
        for (int d = 1; d <= 20; d++) {
            for (int p = 0; p < 3; p++) {
                Event e = new Event("produto" + p, 1, 1.0f, d);
                sp.saveEvento(e, "produto" + p, d);
            }
        }

        final int S = 3;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        int numThreads = 10;
        ExecutorService ex = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Each task performs many accesses across different days to force evictions
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Callable<Boolean> task = () -> {
                try {
                    startLatch.await();
                    int base = threadId % 5;
                    for (int i = 0; i < 50; i++) {
                        int day = 1 + ((base + i) % 20);
                        // access getDayData and aggregation to mix workloads
                        smm.getDayData(day, 20);
                        smm.getAggregationCache("produto" + (i % 3), 5, 20);
                    }
                    return true;
                } finally {
                    doneLatch.countDown();
                }
            };
            futures.add(ex.submit(task));
        }

        // start
        startLatch.countDown();
        doneLatch.await();

        // ensure all tasks completed successfully
        for (Future<Boolean> f : futures)
            f.get();

        // At the end, verify series in memory <= S
        int loaded = smm.getLoadedSeriesCount();
        assertTrue(loaded <= S, "Número de séries em memória deve ser <= S (" + S + "). Encontrado: " + loaded);

        ex.shutdownNow();
    }
}
