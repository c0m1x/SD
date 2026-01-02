package uminho.grupo57;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class LimitsTest {

    @Test
    public void testDayLimit() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-limits-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        // Create persisted data for days 1..15 directly using SeriesPersistence
        for (int d = 1; d <= 15; d++) {
            Event e = new Event("Produto", 1, 1.0f, d);
            sp.saveEvento(e, "Produto", d);
            // simulate server housekeeping after day advance
            sp.deleteOldestDayIfLowerThanMax(10, d);
        }

        // After housekeeping, oldest 5 days (1..5) should have been deleted
        for (int d = 1; d <= 5; d++) {
            assertFalse(sp.exists(d), "Dia " + d + " deveria ter sido removido do disco");
        }

        for (int d = 6; d <= 15; d++) {
            assertTrue(sp.exists(d), "Dia " + d + " deveria existir no disco");
        }

        // cleanup
        try { Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); } catch (Exception ignored) {}
    }

    @Test
    public void testSeriesMemoryLimit() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-memlimit-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        // create persisted data for days 1..8
        for (int d = 1; d <= 8; d++) {
            Event e = new Event("produto", 1, 1.0f, d);
            sp.saveEvento(e, "produto", d);
        }

        // create memory manager with S = 5
        SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);

        // load days 1..8 into memory (will cause evictions)
        // fixed debug file path for easier retrieval when terminal is unavailable
        Path debugDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
        Files.createDirectories(debugDir);
        Path debugFile = debugDir.resolve("limits-debug.txt");
        Files.writeString(debugFile, "LimitsTest debug log\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        for (int d = 1; d <= 8; d++) {
            smm.getDayData(d, 8);
            String step = String.format("After loading day %d -> %s\n", d, smm.getMemoryStats());
            Files.writeString(debugFile, step, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }

        String stats = smm.getMemoryStats(); // format: Series=X | Aggregations=Y
        String seriesPart = stats.split("\\|")[0];
        int loaded = Integer.parseInt(seriesPart.replaceAll("[^0-9]", ""));

        String finalLine = String.format("Final memory stats: %s\nAssertion: loaded(%d) <= 5\n", stats, loaded);
        Files.writeString(debugFile, finalLine, StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        // If the assertion fails, the debug file will contain step-by-step state to inspect.
        assertTrue(loaded <= 5, "Número de séries em memória deve ser <= S (5). Encontrado: " + loaded + ". Ver ficheiro de debug: " + debugFile.toString());

        // cleanup temp directory but keep debug logs in ~/sd-test-logs for inspection
        try { Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); } catch (Exception ignored) {}
    }
}
