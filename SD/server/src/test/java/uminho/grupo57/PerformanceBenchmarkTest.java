package uminho.grupo57;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class PerformanceBenchmarkTest {

    @Test
    public void testMemoryVsDiskAggregation() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-perf-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        final String product = "p0";
        final int events = 2000; // events in the day
        final int iterations = 50; // repeated aggregations

        // create a single day's data with many events
        int day = 1;
        for (int i = 0; i < events; i++) {
            Event e = new Event(product, 1 + (i % 5), 1.0f + (i % 10), day);
            sp.saveEvento(e, product, day);
        }

        // Prepare log directory
        Path logDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
        Files.createDirectories(logDir);
        String ts = String.valueOf(Instant.now().toEpochMilli());
        Path out = logDir.resolve("perf-" + ts + ".csv");
        Files.writeString(out, "mode,iteration,duration_ns\n", StandardCharsets.UTF_8);

        // Case 1: memory (S large so day stays in memory)
        SeriesMemoryManager smmMemory = new SeriesMemoryManager(5, sp);
        // load into memory
        smmMemory.getDayData(day, day);

        List<Long> memTimes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            smmMemory.getAggregationCache(product, 1, day);
            long dt = System.nanoTime() - t0;
            memTimes.add(dt);
            Files.writeString(out, String.format("memory,%d,%d\n", i, dt), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        }

        // Case 2: disk (S=0 forces read from disk every time)
        SeriesMemoryManager smmDisk = new SeriesMemoryManager(0, sp);
        List<Long> diskTimes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            smmDisk.getAggregationCache(product, 1, day);
            long dt = System.nanoTime() - t0;
            diskTimes.add(dt);
            Files.writeString(out, String.format("disk,%d,%d\n", i, dt), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        }

        double avgMem = memTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double avgDisk = diskTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        String summary = String.format("Average memory ns=%.2f, disk ns=%.2f\n", avgMem, avgDisk);
        Files.writeString(out, "#summary\n" + summary, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        System.out.println("Performance summary: " + summary);
        System.out.println("Results written to: " + out.toString());

        // sanity check: disk should be slower than memory
        assertTrue(avgDisk > avgMem, "Esperado: acesso via disco mais lento que acesso em memória");
    }
}
