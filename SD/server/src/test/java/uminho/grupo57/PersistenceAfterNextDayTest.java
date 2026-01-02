package uminho.grupo57;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.SeriesPersistence;

public class PersistenceAfterNextDayTest {

    @Test
    public void testPersistenceAfterNextDay() throws Exception {
        Path tempDir = Files.createTempDirectory("ts-persist-");
        SeriesPersistence sp1 = new SeriesPersistence(tempDir.toString());

        // day N = 1
        Event e = new Event("ProdutoX", 2, 3.5f, 1);
        sp1.saveEvento(e, "ProdutoX", 1);
        sp1.saveCurrentDay(1);

        // simulate restart: new persistence instance
        SeriesPersistence sp2 = new SeriesPersistence(tempDir.toString());

        assertTrue(sp2.exists(1), "Dia 1 deveria existir no disco após nextDay()/saveCurrentDay");

        // cleanup left to OS (temp dir)
    }
}
