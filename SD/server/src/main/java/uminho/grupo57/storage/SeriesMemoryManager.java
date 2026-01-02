package uminho.grupo57.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import uminho.grupo57.entities.Event;
import uminho.grupo57.entities.TimeSeries;

/**
 * Gestão em memória de séries temporais com políticas LRU e caches de
 * agregação. Fornece acesso thread-safe a séries por dia e operações de
 * persistência delegadas a {@link SeriesPersistence}.
 */
public class SeriesMemoryManager {

    private final int maxSeriesInMemory;
    private final SeriesPersistence persistence;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Integer, TimeSeries> seriesInMemory; // dia -> TimeSeries
    private final Map<Integer, Map<Integer, AggregationCache>> aggregationCache; // produtoHash -> (dia -> AggregationCache)
    private final Map<Integer, Long> accessOrder; // LRU key = dia

    // Instrumentation
    private final AtomicInteger loadFromDiskCount = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger evictions = new AtomicInteger(0);

    public SeriesMemoryManager(int maxSeriesInMemory, SeriesPersistence persistence)
    {
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.persistence = persistence;

        this.seriesInMemory = new ConcurrentHashMap<>();
        this.aggregationCache = new ConcurrentHashMap<>();
        this.accessOrder = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true));
    }
    /**
     * Obtém os dados de um dia, carregando da memória ou do disco conforme necessário.
     *
     * @param dia Dia desejado
     * @param currentDay Dia atual (para políticas de cache)
     * @return `TimeSeries` para o dia (pode ser vazio)
     * @throws IOException Se ocorrer erro ao carregar do disco
     */
    public TimeSeries getDayData(int dia, int currentDay) throws IOException
    {
        if(maxSeriesInMemory == 0)
            return persistence.loadDayData(dia);
        if(dia == currentDay)
            return getOrCreateInMemory(dia);

        // Fast-path: try read-lock to see if present
        lock.readLock().lock();
        TimeSeries ts;
        try {
            ts = seriesInMemory.get(dia);
            if (ts != null) {
                cacheHits.incrementAndGet();
                updateAccessOrder(dia);
                return ts;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Not present in memory: load from disk and manage memory.
        // Important: do NOT try to acquire write lock while holding read lock
        return loadFromDiskAndManageMemory(dia);
    }

    /**
     * Adiciona um evento ao dia corrente (em memória) e invalida a cache
     * de agregação correspondente caso exista.
     *
     * @param event Evento a adicionar
     * @param currentDay Dia corrente
     */
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

    /**
     * Persiste um evento em disco (delegado para `SeriesPersistence`).
     *
     * @param evento Evento a guardar
     * @param produto Nome do produto
     * @param dia Dia do evento
     * @throws IOException Se ocorrer erro de I/O
     */
    public void saveEvent(Event evento, String produto, int dia) throws IOException
    {
        persistence.saveEvento(evento, produto, dia);
    }

    /**
     * Obtém/Calcula cache de agregação para um produto num intervalo de dias.
     *
     * @param produto Nome do produto
     * @param dias Número de dias para agregação
     * @param currentDay Dia atual
     * @return `AggregationCache` com resultados agregados
     * @throws IOException Se ocorrer erro ao ler dados do disco
     */
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

    /**
     * Recupera eventos filtrados por produtos ao longo dos últimos `dias`.
     *
     * @param products Conjunto de nomes de produtos
     * @param dias Número de dias a incluir
     * @param currentDay Dia atual
     * @return Mapa produto -> lista de eventos
     * @throws IOException Se ocorrer erro ao ler do disco
     */
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

    /**
     * Persiste o dia atual (arquivo dia.dat).
     *
     * @param currentDay Dia atual a gravar
     */
    public void saveCurrentDay(int currentDay)
    {
        persistence.saveCurrentDay(currentDay);
    }

    /** @return dia salvo em disco ou -1 se não existir */
    public int getSavedDay()
    {
        return persistence.getSavedDay();
    }

    /**
     * Operações a executar quando o dia avança (evicção, etc.).
     *
     * @param oldDay Dia anterior
     */
    public void onDayAdvance(int oldDay)
    {
        if(maxSeriesInMemory == 0)
            return;

        evict(oldDay);
    }

    /** @return estatísticas simples de memória (número de séries e caches) */
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

    /**
     * Pede à persistência para apagar o dia mais antigo caso o número de
     * diretórios/dias exceda {@code maxDias}.
     *
     * @param maxDias Limite máximo de dias a manter
     * @param curDay Dia atual
     */
    public void deleteOldestDayIfLowerThanMax(int maxDias, int curDay)
    {
        persistence.deleteOldestDayIfLowerThanMax(maxDias, curDay);
    }

    private TimeSeries getOrCreateInMemory(int dia)
    {
        lock.writeLock().lock();
        try {
            TimeSeries ts = seriesInMemory.get(dia);
            if (ts != null)
                return ts;

            if (countSeries() >= maxSeriesInMemory)
                evictLeastRecentlyUsed();

            ts = new TimeSeries(dia, new HashMap<>());
            seriesInMemory.put(dia, ts);
            updateAccessOrder(dia);
            return ts;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private TimeSeries loadFromDiskAndManageMemory(int dia) throws IOException
    {
        TimeSeries diskData = persistence.loadDayData(dia);
        loadFromDiskCount.incrementAndGet();
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
        synchronized (accessOrder) {
            if (accessOrder.isEmpty())
                return;

            Integer key = accessOrder.keySet().iterator().next();
            evict(key);
        }
    }

    private void evict(int dia)
    {
        seriesInMemory.remove(dia);
        accessOrder.remove(dia);

        aggregationCache.values().forEach(m -> m.remove(dia));
        evictions.incrementAndGet();
    }

    private int countSeries()
    {
        return seriesInMemory.size();
    }

    private void updateAccessOrder(int dia)
    {
        accessOrder.put(dia, System.currentTimeMillis());
    }

    // Instrumentation getters
    public int getLoadedSeriesCount() {
        return seriesInMemory.size();
    }

    public int getLoadFromDiskCount() {
        return loadFromDiskCount.get();
    }

    public int getCacheHits() {
        return cacheHits.get();
    }

    public int getEvictions() {
        return evictions.get();
    }
}