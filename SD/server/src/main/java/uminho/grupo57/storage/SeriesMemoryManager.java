package uminho.grupo57.storage;

import uminho.grupo57.entities.Event;
import uminho.grupo57.entities.TimeSeries;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SeriesMemoryManager
{

    private final int maxSeriesInMemory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Map<Integer, TimeSeries>> seriesInMemory; // username -> dia -> TimeSeries
    private final Map<String, Map<Integer, Map<Integer, AggregationCache>>> aggregationCache; // username -> produtoHash -> dia -> AggregationCache
    private final Map<String, Long> accessOrder; // LRU

    private final SeriesPersistence persistence;

    public SeriesMemoryManager(int maxSeriesInMemory, SeriesPersistence persistence)
    {
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.persistence = persistence;
        this.seriesInMemory = new ConcurrentHashMap<>();
        this.aggregationCache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }

    public TimeSeries getDayData(String username, int dia, int currentDay) throws IOException
    {
        String key = buildKey(username, dia);
        if(dia == currentDay)
            return getOrCreateInMemory(username, dia);

        // First check if it's in memory with read lock
        lock.readLock().lock();
        try{
            Map<Integer, TimeSeries> userSeries = seriesInMemory.get(username);
            if(userSeries != null && userSeries.containsKey(dia))
            {
                updateAccessOrder(key);
                return userSeries.get(dia);
            }
        }finally{
            lock.readLock().unlock();
        }

        // Not in memory - load from disk WITHOUT holding any locks during I/O
        return loadFromDiskAndManageMemory(username, dia);
    }

    public void addEventToCurrentDay(String username, Event event, int currentDay)
    {
        lock.writeLock().lock();
        try{
            TimeSeries dayData = getOrCreateInMemory(username, currentDay);
            dayData.addEvent(event);

            Map<Integer, Map<Integer, AggregationCache>> userCache = aggregationCache.get(username);
            if(userCache != null)
            {
                Map<Integer, AggregationCache> productCache = userCache.get(event.getNome().hashCode());
                if(productCache != null)
                {
                    AggregationCache cache = productCache.get(currentDay);
                    if(cache != null)
                        cache.invalidate();
                }
            }
        }finally{
            lock.writeLock().unlock();
        }
    }

    public AggregationCache getAggregationCache(String username, String produto, int dias, int currentDay) throws IOException
    {
        lock.writeLock().lock();
        try{
            Map<Integer, Map<Integer, AggregationCache>> userCache = aggregationCache.computeIfAbsent(username, u -> new HashMap<>());
            Map<Integer, AggregationCache> productCache = userCache.computeIfAbsent(produto.hashCode(), p -> new HashMap<>());

            AggregationCache cached = productCache.get(dias);
            if(cached != null && cached.isCalculated())
                return cached;

            AggregationCache result = new AggregationCache(produto, dias);
            int startDay = Math.max(1, currentDay - dias);

            for(int d = startDay; d <= currentDay; d++)
            {
                TimeSeries ts = getDayData(username, d, currentDay);
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

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void persistAndEvict(String username, int dia) throws IOException
    {
        lock.writeLock().lock();
        try{
            Map<Integer, TimeSeries> userSeries = seriesInMemory.get(username);
            if(userSeries != null)
            {
                TimeSeries dayData = userSeries.remove(dia);
                if(dayData != null && !dayData.isEmpty())
                    persistence.saveDayData(username, dia, dayData);
            }
            accessOrder.remove(buildKey(username, dia));

            Map<Integer, Map<Integer, AggregationCache>> userCache = aggregationCache.get(username);
            if(userCache != null)
                userCache.values().forEach(m -> m.remove(dia));
        }finally{
            lock.writeLock().unlock();
        }
    }

    public void onDayAdvance(String username, int oldDay) throws IOException
    {
        persistAndEvict(username, oldDay);
    }

    private TimeSeries getOrCreateInMemory(String username, int dia)
    {
        Map<Integer, TimeSeries> userSeries = seriesInMemory.computeIfAbsent(username, k -> new HashMap<>());
        return userSeries.computeIfAbsent(dia, TimeSeries::new);
    }

    private TimeSeries loadFromDiskAndManageMemory(String username, int dia) throws IOException
    {
        // Load from disk OUTSIDE of lock to avoid blocking
        TimeSeries diskData = persistence.loadDayData(username, dia);
        if(diskData == null)
            diskData = new TimeSeries(dia);

        // Now acquire lock to update memory structures
        lock.writeLock().lock();
        try{
            // Check again if someone else loaded it while we were reading from disk
            Map<Integer, TimeSeries> userSeries = seriesInMemory.get(username);
            if(userSeries != null && userSeries.containsKey(dia))
            {
                updateAccessOrder(buildKey(username, dia));
                return userSeries.get(dia);
            }

            // Check if we need to evict
            if(countSeries() >= maxSeriesInMemory)
                evictLeastRecentlyUsed();

            // Add to memory
            seriesInMemory.computeIfAbsent(username, k -> new HashMap<>()).put(dia, diskData);
            updateAccessOrder(buildKey(username, dia));
            return diskData;
        }finally{
            lock.writeLock().unlock();
        }
    }

    private void evictLeastRecentlyUsed() throws IOException
    {
        if(accessOrder.isEmpty())
            return;

        String key = accessOrder.keySet().iterator().next();
        String[] parts = key.split(":");
        persistAndEvict(parts[0], Integer.parseInt(parts[1]));
    }


    private void removeDaysOlderThan(String username, int diaMinimo) throws IOException
    {
        List<Integer> diasParaRemover = new ArrayList<>();

        lock.readLock().lock();
        try{
            Map<Integer, TimeSeries> userSeries = seriesInMemory.get(username);
            if(userSeries != null)
            {
                for(Integer dia : userSeries.keySet())
                {
                    if(dia < diaMinimo)
                        diasParaRemover.add(dia);
                }
            }
        }finally{
            lock.readLock().unlock();
        }

        for(Integer dia : diasParaRemover)
            persistAndEvict(username, dia);
    }

    public void removeDaysOlderThanAll(int diaMinimo) throws IOException
    {
        if(diaMinimo <= 0)
            return;

        Set<String> usernames;

        lock.readLock().lock();
        try{
            usernames = new HashSet<>(seriesInMemory.keySet());
        }finally{
            lock.readLock().unlock();
        }

        for (String user : usernames)
            removeDaysOlderThan(user, diaMinimo);
    }

    private int countSeries()
    {
        return seriesInMemory.values().stream().mapToInt(Map::size).sum();
    }

    private void updateAccessOrder(String key)
    {
        // This is called from within write lock already
        accessOrder.put(key, System.currentTimeMillis());
    }

    private String buildKey(String username, int dia)
    {
        return username + ":" + dia;
    }

    public String getMemoryStats()
    {
        lock.readLock().lock();
        try{
            int totalSeries = countSeries();
            int totalUsers = seriesInMemory.size();

            int totalAggregations = aggregationCache.values().stream()
                    .mapToInt(userMap ->
                            userMap.values().stream()
                                    .mapToInt(Map::size).sum()).sum();

            return String.format("Series=%d | Users=%d | Aggregations=%d", totalSeries, totalUsers, totalAggregations);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void persistAll(int currentDay, boolean saveCurrentDay) throws IOException
    {
        List<String> users;
        Map<String, List<Integer>> toPersist = new HashMap<>();

        lock.readLock().lock();
        try {
            users = new ArrayList<>(seriesInMemory.keySet());
            for(String user : users)
            {
                Map<Integer, TimeSeries> userSeries = seriesInMemory.get(user);
                if(userSeries == null)
                    continue;

                for(Integer dia : userSeries.keySet())
                {
                    if(dia != currentDay || saveCurrentDay)
                        toPersist.computeIfAbsent(user, u -> new ArrayList<>()).add(dia);
                }
            }
        }finally{
            lock.readLock().unlock();
        }

        for(Map.Entry<String, List<Integer>> entry : toPersist.entrySet())
        {
            String user = entry.getKey();
            for(Integer dia : entry.getValue())
            {
                if(dia == currentDay && saveCurrentDay) //Se for para guardar dia atual não eliminar da memória
                {
                    lock.writeLock().lock();
                    try{
                        Map<Integer, TimeSeries> userSeries = seriesInMemory.get(user);
                        if(userSeries != null)
                        {
                            TimeSeries dayData = userSeries.get(dia);
                            if(dayData != null && !dayData.isEmpty())
                                persistence.saveDayData(user, dia, dayData);
                        }
                    }finally{
                        lock.writeLock().unlock();
                    }
                }else{ //Para os outros dias normal
                    persistAndEvict(user, dia);
                }
            }
        }
    }

    public Map<String, List<Event>> getEventsForProducts(String username, Set<String> products, int dia, int currentDay) throws IOException
    {
        int startDay = Math.max(1, currentDay - dia);

        Map<String, List<Event>> result = new HashMap<>();
        for (String p : products)
            result.put(p, new ArrayList<>());

        for(int d = startDay; d <= currentDay; d++)
        {
            TimeSeries ts = getDayData(username, d, currentDay);
            if(ts == null || ts.isEmpty())
                continue;

            for (String produto : products)
                result.get(produto).addAll(ts.getEventosProduto(produto));
        }

        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    public void saveCurrentDay(int dia)
    {
        persistence.saveCurrentDay(dia);
    }

    public int getSavedDay()
    {
        return persistence.getSavedDay();
    }
}