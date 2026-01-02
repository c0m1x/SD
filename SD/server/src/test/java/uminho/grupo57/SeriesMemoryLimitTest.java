package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class SeriesMemoryLimitTest {

    @Test
    public void testSeriesMemoryLimitInstrumentation() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-memlimit2-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        // create persisted data for days 1..8
        for (int d = 1; d <= 8; d++) {
            Event e = new Event("produto", 1, 1.0f, d);
            sp.saveEvento(e, "produto", d);
        }

        // create memory manager with S = 5
        SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);

        // load days 1..8 into memory (will cause evictions)
        for (int d = 1; d <= 8; d++) {
            smm.getDayData(d, 8);
        }

        int loaded = smm.getLoadedSeriesCount();
        assertTrue(loaded <= 5, "Número de séries em memória deve ser <= S (5). Encontrado: " + loaded);
    }
}
