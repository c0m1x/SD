package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

public class LimitsTest {

    @Test
    public void testDayLimit() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-day-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());

        // create events for days 1..15
        for (int d = 1; d <= 15; d++) {
            Event e = new Event("ProdutoA", 1, 1.0f, d);
            sp.saveEvento(e, "ProdutoA", d);
        }

        // enforce max 10 days — currentDay = 15
        // call housekeeping multiple times to remove oldest days until only 10 remain
        for (int i = 0; i < 5; i++)
            sp.deleteOldestDayIfLowerThanMax(10, 15);

        // days 1..5 should be removed
        for (int d = 1; d <= 5; d++)
            assertFalse(sp.exists(d), "Old day should be deleted: " + d);

        // days 6..15 should exist
        for (int d = 6; d <= 15; d++)
            assertTrue(sp.exists(d), "Recent day should exist: " + d);
    }

    @Test
    public void testSeriesMemoryLimit() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-s-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 5;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        // create and persist 10 days of data
        for (int d = 1; d <= 10; d++) {
            Event e = new Event("Prod" + d, 1, 1.0f, d);
            sp.saveEvento(e, "Prod" + d, d);
        }

        // access all 10 days to force loads
        for (int d = 1; d <= 10; d++)
            smm.getDayData(d, 10);

        assertTrue(smm.getLoadedSeriesCount() <= S, "Loaded series should not exceed S");
    }

    @Test
    public void testLRUEviction() throws Exception {
        Path tmp = Files.createTempDirectory("ts-test-lru-");
        SeriesPersistence sp = new SeriesPersistence(tmp.toString());
        int S = 5;
        SeriesMemoryManager smm = new SeriesMemoryManager(S, sp);

        // persist S+3 days
        for (int d = 1; d <= S + 3; d++) {
            Event e = new Event("P" + d, 1, 1.0f, d);
            sp.saveEvento(e, "P" + d, d);
        }

        // access first two repeatedly to keep them hot
        for (int i = 0; i < 5; i++) {
            smm.getDayData(1, S + 3);
            smm.getDayData(2, S + 3);
        }

        // access the remaining to force eviction
        for (int d = 3; d <= S + 3; d++)
            smm.getDayData(d, S + 3);

        // There should have been at least one eviction
        assertTrue(smm.getEvictions() > 0, "There should be evictions");

        // There should have been some disk loads overall during the test
        assertTrue(smm.getLoadFromDiskCount() > 0, "There should be disk loads during the test");

        // Ensure memory series count respects S
        assertTrue(smm.getLoadedSeriesCount() <= S, "Loaded series should not exceed S after eviction");
    }
}

