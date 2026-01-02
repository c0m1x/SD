package uminho.grupo57;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesPersistence;

public class DayLimitTest {

    @Test
    public void testDayLimit() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-daylimit-");
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
}
