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
     * Adiciona evento à série de um utilizador
     */
    public void addEvent(String username, Event event) {
        TimeSeries series = getOrCreateSeries(username);
        series.addEvent(event);
    }
    
    /**
     * Consulta produto na série de um utilizador
     */
    public List<Event> getEventosProduto(String username, String produto) {
        TimeSeries series = userSeries.get(username);
        if (series == null) {
            return Collections.emptyList();
        }
        return series.getEventosProduto(produto);
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
     * Obtém estatísticas de um produto
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
     * Avança para o dia seguinte (pode limpar dados antigos)
     */
    public void nextDay() {
        lock.writeLock().lock();
        try {
            currentDay++;
            System.out.println("→ Dia atual: " + currentDay);
            
            // Se exceder maxDays, limpar dados mais antigos
            if (currentDay > maxDays) {
                // Implementação futura: remover eventos com mais de maxDays
            }
            
            // Se exceder maxSeriesInMemory, fazer cleanup
            if (userSeries.size() > maxSeriesInMemory) {
                // Implementação futura: remover séries menos usadas
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int getCurrentDay() {
        return currentDay;
    }
    
    public int getTotalUsers() {
        return userSeries.size();
    }
}
