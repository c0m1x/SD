package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class LRUEvictionTest {

    @Test
    public void testLRUEvictionBehavior() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-lru-");
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());

        // create persisted data for days 1..8
        for (int d = 1; d <= 8; d++) {
            Event e = new Event("produto" + d, 1, 1.0f, d);
            sp.saveEvento(e, "produto" + d, d);
        }

        // create memory manager with S = 5
        SeriesMemoryManager smm = new SeriesMemoryManager(5, sp);

        // access days 1..8 sequentially -> should cause evictions
        for (int d = 1; d <= 8; d++) {
            smm.getDayData(d, 8);
        }

        int loaded = smm.getLoadedSeriesCount();
        assertTrue(loaded <= 5, "Número de séries em memória deve ser <= S (5). Encontrado: " + loaded);

        int evictions = smm.getEvictions();
        assertTrue(evictions > 0, "Devem ocorrer evictions ao carregar > S dias");

        // access a previously-evicted day (e.g., day 1) and verify it loads from disk (loadFromDiskCount increases)
        int beforeLoads = smm.getLoadFromDiskCount();
        smm.getDayData(1, 8);
        int afterLoads = smm.getLoadFromDiskCount();
        assertTrue(afterLoads >= beforeLoads + 1, "Reacesso a dia evicted deve recarregar do disco");
    }
}
