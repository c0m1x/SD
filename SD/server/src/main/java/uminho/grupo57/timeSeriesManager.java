package uminho.grupo57;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.*;

/**
 * Gestor thread-safe de séries temporais por utilizador
 * Mantém múltiplas séries em memória e permite avançar dias
 */
public class timeSeriesManager {
    
    private final ConcurrentHashMap<String, TimeSeries> userSeries;
    private final int maxDays;
    private final int maxSeriesInMemory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private int currentDay;
    
    public timeSeriesManager(int maxDays, int maxSeriesInMemory) {
        this.maxDays = maxDays;
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.userSeries = new ConcurrentHashMap<>();
        this.currentDay = 0;
    }
    
    /**
     * Obtém ou cria a série temporal de um utilizador
     */
    public TimeSeries getOrCreateSeries(String username) {
        return userSeries.computeIfAbsent(username, k -> new TimeSeries());
    }
    
    /**
     * Adiciona evento à série de um utilizador no dia corrente
     */
    public void addEvent(String username, Event event) {
        TimeSeries series = getOrCreateSeries(username);
        
        // Criar novo evento com o dia corrente
        Event eventWithDay = new Event(event.getNome(), event.getQuantidade(), event.getPreco(), currentDay);
        series.addEvent(eventWithDay);
    }
    
    /**
     * Consulta produto na série de um utilizador (todos os dias)
     */
    public List<Event> getEventosProduto(String username, String produto) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyList();
        }
        return series.getEventosProduto(produto);
    }
    
    /**
     * Consulta produto em um dia específico
     */
    public List<Event> getEventosProdutoDia(String username, String produto, int dia) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyList();
        }
        return series.getEventosProdutoDia(produto, dia);
    }
    
    /**
     * Consulta produto nos últimos d dias (excluindo dia corrente)
     */
    public List<Event> getEventosProdutoUltimosDias(String username, String produto, int numeroDias) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyList();
        }
        return series.getEventosProdutoUltimosDias(produto, currentDay, numeroDias);
    }
    
    /**
     * Lista todos os produtos de um utilizador
     */
    public Set<String> getAllProdutos(String username) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptySet();
        }
        return series.getAllProdutos();
    }
    
    /**
     * Obtém estatísticas de um produto (todos os dias)
     */
    public Map<String, Object> getStats(String username, String produto) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("quantidade_total", series.getTotalQuantidadeProduto(produto));
        stats.put("preco_total", series.getTotalPrecoProduto(produto));
        stats.put("preco_max", series.getPrecoMaximoProduto(produto).orElse(0f));
        stats.put("preco_min", series.getPrecoMinimoProduto(produto).orElse(0f));
        stats.put("preco_medio", series.getPrecoMedioProduto(produto).orElse(0f));
        
        return stats;
    }
    
    /**
     * Obtém estatísticas nos últimos d dias (excluindo dia corrente)
     */
    public Map<String, Object> getStatsUltimosDias(String username, String produto, int numeroDias) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("quantidade_total", series.getTotalQuantidadeProdutoUltimosDias(produto, currentDay, numeroDias));
        stats.put("volume_total", series.getVolumeTotalUltimosDias(produto, currentDay, numeroDias));
        stats.put("preco_max", series.getPrecoMaximoUltimosDias(produto, currentDay, numeroDias).orElse(0f));
        stats.put("preco_medio", series.getPrecoMedioUltimosDias(produto, currentDay, numeroDias).orElse(0f));
        
        return stats;
    }
    
    /**
     * Avança para o dia seguinte
     */
    public void nextDay() {
        lock.writeLock().lock();
        try {
            currentDay++;
            System.out.println("═══ Avançou para dia " + currentDay + " ═══");
            
            // Remover eventos mais antigos que maxDays
            if (currentDay > maxDays) {
                int diaMinimo = currentDay - maxDays;
                for (TimeSeries series : userSeries.values()) {
                    series.removeEventosAnterioresA(diaMinimo);
                }
                System.out.println("Removidos eventos anteriores ao dia " + diaMinimo);
            }
            
            // TODO: Implementar gestão de memória (S séries)
            // TODO: Implementar persistência em disco
            // TODO: Notificar threads bloqueadas
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
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
    
    public int getMaxDays() {
        return maxDays;
    }
    
    public int getMaxSeriesInMemory() {
        return maxSeriesInMemory;
    }
}