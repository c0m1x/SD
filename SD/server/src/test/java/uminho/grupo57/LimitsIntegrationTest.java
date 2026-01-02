package uminho.grupo57;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integração dos testes de limites (D/S) movidos do diretório `tests`.
 * Placeholders desativados para execução manual quando necessário.
 */
@Disabled("Testes de limites interativos; habilitar manualmente quando necessário")
public class LimitsIntegrationTest {

    @Test
    public void placeholder() {
        // Smoke test
    }

    public void testDayLimit() {
        LimitsTest.testDayLimit();
    }

    public void testSeriesMemoryLimit() {
        LimitsTest.testSeriesMemoryLimit();
    }

    public void testLRUEviction() {
        LimitsTest.testLRUEviction();
    }

    public void testMemoryVsDiskPerformance() {
        LimitsTest.testMemoryVsDiskPerformance();
    }

    public void testConcurrentAccessWithLimitS() {
        LimitsTest.testConcurrentAccessWithLimitS();
    }

    public void testPersistenceAfterNextDay() {
        LimitsTest.testPersistenceAfterNextDay();
    }

    public void testLargeSeriesProcessing() {
        LimitsTest.testLargeSeriesProcessing();
    }
}
