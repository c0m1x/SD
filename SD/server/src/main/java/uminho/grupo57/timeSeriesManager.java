package uminho.grupo57;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.*;

/**
 * Gestor thread-safe de séries temporais por utilizador
 * Integra persistência em disco e gestão de memória (parâmetro S)
 */
public class timeSeriesManager {
    
    private final ConcurrentHashMap<String, TimeSeries> userSeries;
    private final int maxDays; // Parâmetro D
    private final int maxSeriesInMemory; // Parâmetro S
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private int currentDay;
    
    
    // Componentes modulares
    private final SeriesPersistence persistence;
    private final NotificationManager notificationManager;
    private final SeriesMemoryManager memoryManager;
    
    public timeSeriesManager(int maxDays, int maxSeriesInMemory) {
        this(maxDays, maxSeriesInMemory, "data/series");
    }
    
    public timeSeriesManager(int maxDays, int maxSeriesInMemory, String dataDirectory) {
        this.maxDays = maxDays;
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.userSeries = new ConcurrentHashMap<>();
        this.currentDay = 0;
        
        // Inicializar componentes
        this.persistence = new SeriesPersistence(dataDirectory);
        this.memoryManager = new SeriesMemoryManager(maxSeriesInMemory, persistence);
        this.notificationManager = new NotificationManager();
        
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  GESTOR DE SÉRIES TEMPORAIS INICIALIZADO        ║");
        System.out.println("║  Parâmetro D (dias): " + maxDays + "                         ║");
        System.out.println("║  Parâmetro S (séries): " + maxSeriesInMemory + "                          ║");
        System.out.println("║  Diretório dados: " + dataDirectory + "                    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }
    
    /**
     * Obtém ou cria a série temporal de um utilizador
     */
    public TimeSeries getOrCreateSeries(String username) {
        return userSeries.computeIfAbsent(username, k -> new TimeSeries(k, memoryManager));
    }
    
    /**
     * Adiciona evento à série de um utilizador no dia corrente
     */
    public void addEvent(String username, Event event) {
        TimeSeries series = getOrCreateSeries(username);
        
        // Criar novo evento com o dia corrente
        Event eventWithDay = new Event(event.getNome(), event.getQuantidade(), event.getPreco(), currentDay);
        series.addEvent(eventWithDay, currentDay);
        
        // Notificar notificações bloqueantes
        notificationManager.onEventAdded(username, event.getNome());
    }
    
    /**
     * Consulta produto em um dia específico
     */
    public List<Event> getEventosProdutoDia(String username, String produto, int dia) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyList();
        }
        return series.getEventosProdutoDia(produto, dia, currentDay);
    }
    
    /**
     * Lista todos os produtos de um utilizador (dia corrente)
     */
    public Set<String> getAllProdutos(String username) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptySet();
        }
        
        // Obter produtos do dia corrente
        try {
            DayData currentDayData = memoryManager.getDayData(username, currentDay, currentDay);
            return currentDayData.getAllProdutos();
        } catch (Exception e) {
            System.err.println("Erro ao obter produtos: " + e.getMessage());
            return Collections.emptySet();
        }
    }
    
    /**
     * Obtém estatísticas de um produto em um dia específico (COM CACHE)
     */
    public Map<String, Object> getStatsForDay(String username, String produto, int dia) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyMap();
        }
        
        return series.getAggregationForDay(produto, dia, currentDay);
    }
    
    /**
     * Obtém estatísticas nos últimos d dias (COM CACHE + PERSISTÊNCIA)
     * Processa incrementalmente do disco sem exceder limite S
     */
    public Map<String, Object> getStatsUltimosDias(String username, String produto, int numeroDias) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyMap();
        }
        
        // Este método já gere automaticamente:
        // - Cache para dias já calculados
        // - Carregamento do disco quando necessário
        // - Eviction LRU para respeitar limite S
        return series.getAggregationRange(produto, currentDay, numeroDias);
    }
    
    /**
     * Obtém estatísticas de um produto (todos os dias)
     * ATENÇÃO: Pode ser lento para muitos dias
     */
    public Map<String, Object> getStats(String username, String produto) {
        // Delegar para getStatsUltimosDias com todos os dias
        return getStatsUltimosDias(username, produto, currentDay);
    }
    
    /**
     * Avança para o dia seguinte
     * 1. Persiste dia anterior de cada utilizador
     * 2. Remove dias antigos (> D)
     * 3. Limpa cache de agregações antigas
     */
    public void nextDay() {
        lock.writeLock().lock();
        try {
            int previousDay = currentDay;
            currentDay++;
            
            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("║  DIA " + currentDay + " INICIADO                         ║");
            System.out.println("╚════════════════════════════════════════════╝");
            
            // 1. Persistir dia anterior para cada utilizador
            System.out.println("A guardar dia " + previousDay + "...");
            for (Map.Entry<String, TimeSeries> entry : userSeries.entrySet()) {
                String username = entry.getKey();
                TimeSeries series = entry.getValue();
                
                try {
                    series.onDayAdvance(previousDay);
                } catch (Exception e) {
                    System.err.println("Erro ao persistir " + username + ": " + e.getMessage());
                }
            }
            
            // 2. Remover dias antigos (> D)
            if (currentDay > maxDays) {
                int diaMinimo = currentDay - maxDays;
                System.out.println("A remover dias anteriores a " + diaMinimo + "...");
                
                for (Map.Entry<String, TimeSeries> entry : userSeries.entrySet()) {
                    String username = entry.getKey();
                    TimeSeries series = entry.getValue();
                    
                    try {
                        series.removeDaysOlderThan(diaMinimo);
                    } catch (Exception e) {
                        System.err.println("Erro ao remover dias antigos de " + username + ": " + e.getMessage());
                    }
                }
            }
            
            // 3. Estatísticas de memória
            Map<String, Object> memStats = memoryManager.getMemoryStats();
            System.out.println("Memória: " + memStats);
            
            // Notificar threads bloqueadas (WAIT_SIMULTANEOUS, WAIT_CONSECUTIVE)
            notificationManager.onDayAdvance();
            System.out.println("Threads bloqueadas notificadas.");
            
            System.out.println("Dia " + currentDay + " pronto!\n");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Força persistência de todas as séries (para encerramento limpo)
     */
    public void persistAll() {
        lock.writeLock().lock();
        try {
            System.out.println("\nA guardar todas as séries...");
            
            for (Map.Entry<String, TimeSeries> entry : userSeries.entrySet()) {
                String username = entry.getKey();
                TimeSeries series = entry.getValue();
                
                try {
                    // Persistir dia corrente também
                    series.onDayAdvance(currentDay);
                } catch (Exception e) {
                    System.err.println("Erro ao persistir " + username + ": " + e.getMessage());
                }
            }
            
            System.out.println("Todas as séries guardadas!\n");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Estatísticas globais do sistema
     */
    public Map<String, Object> getSystemStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("dia_corrente", currentDay);
            stats.put("total_usuarios", userSeries.size());
            stats.put("max_dias", maxDays);
            stats.put("max_series_memoria", maxSeriesInMemory);
            
            // Estatísticas de memória
            Map<String, Object> memStats = memoryManager.getMemoryStats();
            stats.putAll(memStats);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // ========== GETTERS ==========
    
    public int getCurrentDay() {
        lock.readLock().lock();
        try {
            return currentDay;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public int getTotalUsers() {
        return userSeries.size();
    }
    
    
    /**
     * Filtra eventos de múltiplos produtos num dia específico
     */
    public Map<String, List<Event>> getEventosFiltrados(String username, int dia, String[] produtos) {
        lock.readLock().lock();
        try {
            TimeSeries ts = userSeries.get(username);
            if (ts == null) {
                return Collections.emptyMap();
            }
            
            Map<String, List<Event>> resultado = new HashMap<>();
            Set<String> produtosSet = new HashSet<>(Arrays.asList(produtos));
            
            for (String produto : produtosSet) {
                List<Event> eventos = ts.getEventosProdutoDia(produto, dia, currentDay);
                if (!eventos.isEmpty()) {
                    resultado.put(produto, eventos);
                }
            }
            
            return resultado;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Getter para NotificationManager
     */
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    /**
     * Getter para parâmetro D
     */
    public int getD() {
        return maxDays;
    }

    public int getMaxDays() {
        return maxDays;
    }
    
    public int getMaxSeriesInMemory() {
        return maxSeriesInMemory;
    }

}
