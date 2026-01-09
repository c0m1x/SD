package uminho.grupo57;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integração dos testes de Performance movidos do diretório `tests`.
 */
public class PerformanceIntegrationTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void testScalability() throws Exception {
        PerformanceTest.testScalability();
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testRobustness() throws Exception {
        PerformanceTest.testRobustness();
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testWorkloadMix() throws Exception {
        PerformanceTest.testWorkloadMix();
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testNotifications() throws Exception {
        PerformanceTest.testNotifications();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void testPersistenceAndMemory() throws Exception {
        PerformanceTest.testPersistenceAndMemory();
    }
}
