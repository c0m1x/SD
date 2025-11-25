package uminho.grupo57;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestor de memória para séries temporais
 * Mantém no máximo S séries em memória (além do dia corrente)
 * Usa política LRU para eviction
 */
public class SeriesMemoryManager {
    private final int maxSeriesInMemory; // Parâmetro S
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // username -> (dia -> DayData)
    private final Map<String, Map<Integer, DayData>> seriesInMemory;
    
    // Tracking para LRU: (username, dia) -> timestamp
    private final Map<String, Long> accessOrder;
    
    private final SeriesPersistence persistence;
    
    public SeriesMemoryManager(int maxSeriesInMemory, SeriesPersistence persistence) {
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.persistence = persistence;
        this.seriesInMemory = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true); // LRU
    }
    
    /**
     * Obtém dados de um dia (da memória ou disco)
     * Se necessário, descarrega séries antigas
     */
    public DayData getDayData(String username, int dia, int currentDay) throws Exception {
        String key = buildKey(username, dia);
        
        // Dia corrente: sempre em memória, nunca persiste
        if (dia == currentDay) {
            return getOrCreateInMemory(username, dia);
        }
        
        // Primeiro tenta memória
        lock.readLock().lock();
        try {
            Map<Integer, DayData> userSeries = seriesInMemory.get(username);
            if (userSeries != null && userSeries.containsKey(dia)) {
                DayData dayData = userSeries.get(dia);
                updateAccessOrder(key);
                return dayData;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Não está em memória: carregar do disco
        return loadFromDiskAndManageMemory(username, dia);
    }
    
    /**
     * Adiciona evento ao dia corrente
     */
    public void addEventToCurrentDay(String username, Event event, int currentDay) {
        DayData dayData = getOrCreateInMemory(username, currentDay);
        dayData.addEvent(event);
    }
    
    /**
     * Persiste um dia para disco e remove da memória
     */
    public void persistAndEvict(String username, int dia) throws Exception {
        lock.writeLock().lock();
        try {
            Map<Integer, DayData> userSeries = seriesInMemory.get(username);
            if (userSeries != null) {
                DayData dayData = userSeries.remove(dia);
                if (dayData != null && !dayData.isEmpty()) {
                    persistence.saveDayData(username, dia, dayData);
                    accessOrder.remove(buildKey(username, dia));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Ao avançar dia: persiste dia anterior
     */
    public void onDayAdvance(String username, int oldDay) throws Exception {
        persistAndEvict(username, oldDay);
    }
    
    /**
     * Remove dias antigos (memória e disco)
     */
    public void removeDaysOlderThan(String username, int diaMinimo) throws Exception {
        lock.writeLock().lock();
        try {
            // Remover da memória
            Map<Integer, DayData> userSeries = seriesInMemory.get(username);
            if (userSeries != null) {
                List<Integer> toRemove = new ArrayList<>();
                for (int dia : userSeries.keySet()) {
                    if (dia < diaMinimo) {
                        toRemove.add(dia);
                        accessOrder.remove(buildKey(username, dia));
                    }
                }
                toRemove.forEach(userSeries::remove);
            }
            
            // Remover do disco
            persistence.deleteOldDays(username, diaMinimo);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Obtém estatísticas de memória
     */
    public Map<String, Object> getMemoryStats() {
        lock.readLock().lock();
        try {
            int totalSeriesInMemory = seriesInMemory.values().stream()
                    .mapToInt(Map::size)
                    .sum();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("series_em_memoria", totalSeriesInMemory);
            stats.put("limite_maximo", maxSeriesInMemory);
            stats.put("usuarios_ativos", seriesInMemory.size());
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    private DayData getOrCreateInMemory(String username, int dia) {
        lock.writeLock().lock();
        try {
            Map<Integer, DayData> userSeries = seriesInMemory.computeIfAbsent(username, k -> new HashMap<>());
            DayData dayData = userSeries.get(dia);
            if (dayData == null) {
                dayData = new DayData(dia);
                userSeries.put(dia, dayData);
            }
            updateAccessOrder(buildKey(username, dia));
            return dayData;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private DayData loadFromDiskAndManageMemory(String username, int dia) throws Exception {
        lock.writeLock().lock();
        try {
            // Verificar se precisa fazer eviction
            int totalSeries = countNonCurrentDaySeries();
            if (totalSeries >= maxSeriesInMemory) {
                evictLeastRecentlyUsed();
            }
            
            // Carregar do disco
            DayData dayData = persistence.loadDayData(username, dia);
            if (dayData == null) {
                dayData = new DayData(dia); // Dia vazio
            }
            
            // Adicionar à memória
            Map<Integer, DayData> userSeries = seriesInMemory.computeIfAbsent(username, k -> new HashMap<>());
            userSeries.put(dia, dayData);
            updateAccessOrder(buildKey(username, dia));
            
            return dayData;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void evictLeastRecentlyUsed() throws Exception {
        if (accessOrder.isEmpty()) return;
        
        // Obter entrada mais antiga (LRU)
        String oldestKey = accessOrder.keySet().iterator().next();
        String[] parts = oldestKey.split(":");
        String username = parts[0];
        int dia = Integer.parseInt(parts[1]);
        
        System.out.println("Eviction LRU: " + username + " dia " + dia);
        persistAndEvict(username, dia);
    }
    
    private int countNonCurrentDaySeries() {
        // Conta séries em memória (excluindo dia corrente que é gerido separadamente)
        return seriesInMemory.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
    
    private void updateAccessOrder(String key) {
        accessOrder.put(key, System.currentTimeMillis());
    }
    
    private String buildKey(String username, int dia) {
        return username + ":" + dia;
    }
}