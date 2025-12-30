package uminho.grupo57.storage;

import uminho.grupo57.entities.Event;
import uminho.grupo57.entities.TimeSeries;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SeriesMemoryManager {

    private final int maxSeriesInMemory;
    private final SeriesPersistence persistence;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Integer, TimeSeries> seriesInMemory; // dia -> TimeSeries
    private final Map<Integer, Map<Integer, AggregationCache>> aggregationCache; // produtoHash -> (dia -> AggregationCache)
    private final Map<Integer, Long> accessOrder; // LRU key = dia

    public SeriesMemoryManager(int maxSeriesInMemory, SeriesPersistence persistence)
    {
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.persistence = persistence;

        this.seriesInMemory = new ConcurrentHashMap<>();
        this.aggregationCache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }

    public TimeSeries getDayData(int dia, int currentDay) throws IOException
    {
        if(maxSeriesInMemory == 0)
            return persistence.loadDayData(dia);
        if(dia == currentDay)
            return getOrCreateInMemory(dia);

        lock.readLock().lock();
        try{
            TimeSeries ts = seriesInMemory.get(dia);
            if(ts != null)
            {
                updateAccessOrder(dia);
                return ts;
            }else{
                return loadFromDiskAndManageMemory(dia);
            }
        }finally{
            lock.readLock().unlock();
        }
    }

    public void addEventToCurrentDay(Event event, int currentDay)
    {
        if(maxSeriesInMemory == 0)
            return;

        lock.writeLock().lock();
        try{
            TimeSeries dayData = getOrCreateInMemory(currentDay);
            dayData.addEvent(event);

            Map<Integer, AggregationCache> productCache = aggregationCache.get(event.getNome().hashCode());
            if(productCache != null) //Torna a cache inválida se o produto já existe
            {
                AggregationCache cache = productCache.get(currentDay);
                if(cache != null)
                    cache.invalidate();
            }
        }finally{
            lock.writeLock().unlock();
        }
    }

    public void saveEvent(Event evento, String produto, int dia) throws IOException
    {
        persistence.saveEvento(evento, produto, dia);
    }

    public AggregationCache getAggregationCache(String produto, int dias, int currentDay) throws IOException
    {
        lock.writeLock().lock();
        try{
            int produtoHash = produto.hashCode();
            Map<Integer, AggregationCache> productCache = aggregationCache.computeIfAbsent(produtoHash, p -> new HashMap<>());

            AggregationCache cached = productCache.get(dias);
            if(cached != null && cached.isCalculated())
                return cached;

            AggregationCache result = new AggregationCache(produto, dias);
            int startDay = Math.max(1, currentDay - dias);

            for(int d = startDay; d <= currentDay; d++)
            {
                TimeSeries ts = getDayData(d, currentDay);
                if(ts == null || ts.isEmpty())
                    continue;

                AggregationCache daily = new AggregationCache(produto, d);
                daily.calculate(ts.getEventosProduto(produto));
                result.merge(daily);
            }

            if(!result.isCalculated())
                result.calculate(List.of());

            productCache.put(dias, result);
            return result;
        }finally{
            lock.writeLock().unlock();
        }
    }

    public Map<String, List<Event>> getEventsForProducts(Set<String> products, int dias, int currentDay) throws IOException
    {
        int startDay = Math.max(1, currentDay - dias);
        Map<String, List<Event>> result = new HashMap<>();

        for(String p : products)
            result.put(p, new ArrayList<>());

        for(int d = startDay; d <= currentDay; d++)
        {
            TimeSeries ts = getDayData(d, currentDay);
            if(ts == null || ts.isEmpty())
                continue;

            for(String produto : products)
                result.get(produto).addAll(ts.getEventosProduto(produto));
        }

        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    public void saveCurrentDay(int currentDay)
    {
        persistence.saveCurrentDay(currentDay);
    }

    public int getSavedDay()
    {
        return persistence.getSavedDay();
    }

    public void onDayAdvance(int oldDay)
    {
        if(maxSeriesInMemory == 0)
            return;

        evict(oldDay);
    }

    public String getMemoryStats()
    {
        lock.readLock().lock();
        try {
            int totalSeries = seriesInMemory.size();
            int totalAggregations = aggregationCache.values()
                    .stream()
                    .mapToInt(Map::size)
                    .sum();

            return String.format(
                    "Series=%d | Aggregations=%d",
                    totalSeries, totalAggregations);

        } finally {
            lock.readLock().unlock();
        }
    }

    public void deleteOldestDayIfLowerThanMax(int maxDias, int curDay)
    {
        persistence.deleteOldestDayIfLowerThanMax(maxDias, curDay);
    }

    private TimeSeries getOrCreateInMemory(int dia)
    {
        TimeSeries ts = seriesInMemory.get(dia);
        if(ts != null)
            return ts;

        if(countSeries() >= maxSeriesInMemory)
            evictLeastRecentlyUsed();

        ts = new TimeSeries(dia, new HashMap<>());
        seriesInMemory.put(dia, ts);
        updateAccessOrder(dia);
        return ts;
    }

    private TimeSeries loadFromDiskAndManageMemory(int dia) throws IOException
    {
        TimeSeries diskData = persistence.loadDayData(dia);
        if(diskData == null)
            diskData = new TimeSeries(dia, new HashMap<>());

        lock.writeLock().lock();
        try{
            if(seriesInMemory.containsKey(dia))
            {
                updateAccessOrder(dia);
                return seriesInMemory.get(dia);
            }

            if(countSeries() >= maxSeriesInMemory)
                evictLeastRecentlyUsed();

            seriesInMemory.put(dia, diskData);
            updateAccessOrder(dia);
            return diskData;

        }finally{
            lock.writeLock().unlock();
        }
    }

    private void evictLeastRecentlyUsed()
    {
        if(accessOrder.isEmpty())
            return;

        Integer key = accessOrder.keySet().iterator().next();
        evict(key);
    }

    private void evict(int dia)
    {

        seriesInMemory.remove(dia);
        accessOrder.remove(dia);

        aggregationCache.values().forEach(m -> m.remove(dia));
    }

    private int countSeries()
    {
        return seriesInMemory.size();
    }

    private void updateAccessOrder(int dia)
    {
        accessOrder.put(dia, System.currentTimeMillis());
    }
}