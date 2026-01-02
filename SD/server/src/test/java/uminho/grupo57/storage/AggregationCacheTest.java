package uminho.grupo57.storage;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;

public class AggregationCacheTest {

    @Test
    public void calculateAggregatesEmptyList() {
        AggregationCache cache = new AggregationCache("Arroz", 1);
        cache.calculate(Arrays.asList());

        assertTrue(cache.isCalculated());
        assertEquals(0, cache.getQuantidadeTotal());
        assertEquals(0f, cache.getVolumeTotal());
        assertEquals(0f, cache.getPrecoMaximo());
        assertEquals(0f, cache.getPrecoMinimo());
        assertEquals(0f, cache.getPrecoMedio());
    }

    @Test
    public void calculateAggregatesNonEmpty() {
        AggregationCache cache = new AggregationCache("Feijao", 2);
        Event e1 = new Event("Feijao", 2, 1.5f, 2);
        Event e2 = new Event("Feijao", 3, 2.0f, 2);

        cache.calculate(Arrays.asList(e1, e2));

        assertTrue(cache.isCalculated());
        assertEquals(5, cache.getQuantidadeTotal());
        assertEquals(2*1.5f + 3*2.0f, cache.getVolumeTotal());
        assertEquals(2.0f, cache.getPrecoMaximo());
        assertEquals(1.5f, cache.getPrecoMinimo());
        assertEquals((2*1.5f + 3*2.0f) / 5, cache.getPrecoMedio());
    }

    @Test
    public void mergeCaches() {
        AggregationCache a = new AggregationCache("A", 1);
        AggregationCache b = new AggregationCache("A", 1);
        Event e1 = new Event("A", 1, 1.0f, 1);
        Event e2 = new Event("A", 2, 3.0f, 1);

        a.calculate(Arrays.asList(e1));
        b.calculate(Arrays.asList(e2));

        a.merge(b);

        assertTrue(a.isCalculated());
        assertEquals(3, a.getQuantidadeTotal());
        assertEquals(1*1.0f + 2*3.0f, a.getVolumeTotal());
        assertEquals(3.0f, a.getPrecoMaximo());
        assertEquals(1.0f, a.getPrecoMinimo());
        assertEquals((1*1.0f + 2*3.0f) / 3, a.getPrecoMedio());
    }

    @Test
    public void invalidateClears() {
        AggregationCache cache = new AggregationCache("X", 5);
        Event e = new Event("X", 4, 2.5f, 5);
        cache.calculate(Arrays.asList(e));
        assertTrue(cache.isCalculated());

        cache.invalidate();
        assertFalse(cache.isCalculated());
        assertNull(cache.getQuantidadeTotal());
        assertNull(cache.getVolumeTotal());
        assertNull(cache.getPrecoMaximo());
        assertNull(cache.getPrecoMinimo());
        assertNull(cache.getPrecoMedio());
    }
}
