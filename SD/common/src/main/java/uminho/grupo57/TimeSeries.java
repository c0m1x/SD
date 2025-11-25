package uminho.grupo57;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Série temporal com persistência e gestão de memória
 * Coordena DayData, Cache, Persistence e Memory Manager
 */
public class TimeSeries implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final String username;
    
    // Componentes modulares
    private transient SeriesMemoryManager memoryManager;
    private transient Map<Integer, Map<Integer, AggregationCache>> aggregationCache;
    
    public TimeSeries(String username, SeriesMemoryManager memoryManager) {
        this.username = username;
        this.memoryManager = memoryManager;
        this.aggregationCache = new HashMap<>();
    }
    
    // Para inicializar após deserialização
    public void setMemoryManager(SeriesMemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        if (this.aggregationCache == null) {
            this.aggregationCache = new HashMap<>();
        }
    }
    
    /**
     * Adiciona evento ao dia corrente
     */
    public void addEvent(Event evento, int currentDay) {
        lock.writeLock().lock();
        try {
            memoryManager.addEventToCurrentDay(username, evento, currentDay);
            
            // Invalidar cache desse dia
            invalidateCacheForDay(evento.getNome().hashCode(), currentDay);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao adicionar evento", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Obtém eventos de um produto em um dia específico
     */
    public List<Event> getEventosProdutoDia(String nomeProduto, int dia, int currentDay) {
        lock.readLock().lock();
        try {
            DayData dayData = memoryManager.getDayData(username, dia, currentDay);
            return dayData.getEventosProduto(nomeProduto);
        } catch (Exception e) {
            System.err.println("Erro ao obter eventos: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Agregação com cache para um dia específico
     * Se dia < corrente: usa cache
     * Processa do disco se necessário sem exceder limite S
     */
    public Map<String, Object> getAggregationForDay(String nomeProduto, int dia, int currentDay) {
        if (dia >= currentDay) {
            // Dia corrente: sem cache
            return calculateAggregationForDay(nomeProduto, dia, currentDay);
        }
        
        // Dia passado: usa cache lazy
        AggregationCache cache = getOrCalculateCache(nomeProduto, dia, currentDay);
        
        Map<String, Object> result = new HashMap<>();
        result.put("quantidade_total", cache.getQuantidadeTotal());
        result.put("volume_total", cache.getVolumeTotal());
        result.put("preco_max", cache.getPrecoMaximo());
        result.put("preco_min", cache.getPrecoMinimo());
        result.put("preco_medio", cache.getPrecoMedio());
        
        return result;
    }
    
    /**
     * Agregação para range de dias
     * Processa incrementalmente, descartando dados do disco ao longo do processo
     */
    public Map<String, Object> getAggregationRange(String nomeProduto, int diaCorrente, int numeroDias) {
        int diaInicio = Math.max(0, diaCorrente - numeroDias);
        
        int quantidadeTotal = 0;
        float volumeTotal = 0f;
        float precoMaximo = Float.MIN_VALUE;
        float precoMinimo = Float.MAX_VALUE;
        float somaPrecos = 0f;
        int contadorEventos = 0;
        
        // Processa dia a dia (incremental)
        for (int dia = diaInicio; dia < diaCorrente; dia++) {
            // Usa cache se disponível
            AggregationCache cache = getOrCalculateCache(nomeProduto, dia, diaCorrente);
            
            if (cache.getQuantidadeTotal() > 0) {
                quantidadeTotal += cache.getQuantidadeTotal();
                volumeTotal += cache.getVolumeTotal();
                precoMaximo = Math.max(precoMaximo, cache.getPrecoMaximo());
                precoMinimo = Math.min(precoMinimo, cache.getPrecoMinimo());
                
                // Para média precisa: carregar eventos (pode vir do disco)
                try {
                    DayData dayData = memoryManager.getDayData(username, dia, diaCorrente);
                    List<Event> eventos = dayData.getEventosProdutoInternal(nomeProduto.hashCode());
                    
                    somaPrecos += eventos.stream().map(Event::getPreco).reduce(0f, Float::sum);
                    contadorEventos += eventos.size();
                    
                    // Nota: SeriesMemoryManager gerencia automaticamente a eviction
                    // Se S séries já estiverem em memória, a mais antiga será persistida
                    
                } catch (Exception e) {
                    System.err.println("Erro ao processar dia " + dia + ": " + e.getMessage());
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("quantidade_total", quantidadeTotal);
        result.put("volume_total", volumeTotal);
        result.put("preco_max", precoMaximo == Float.MIN_VALUE ? 0f : precoMaximo);
        result.put("preco_min", precoMinimo == Float.MAX_VALUE ? 0f : precoMinimo);
        result.put("preco_medio", contadorEventos > 0 ? somaPrecos / contadorEventos : 0f);
        
        return result;
    }
    
    /**
     * Persiste dia anterior ao avançar
     */
    public void onDayAdvance(int oldDay) {
        lock.writeLock().lock();
        try {
            memoryManager.onDayAdvance(username, oldDay);
        } catch (Exception e) {
            System.err.println("Erro ao persistir dia: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove dias antigos
     */
    public void removeDaysOlderThan(int diaMinimo) {
        lock.writeLock().lock();
        try {
            memoryManager.removeDaysOlderThan(username, diaMinimo);
            
            // Limpar cache de dias antigos
            for (Map<Integer, AggregationCache> cacheProduto : aggregationCache.values()) {
                cacheProduto.keySet().removeIf(dia -> dia < diaMinimo);
            }
        } catch (Exception e) {
            System.err.println("Erro ao remover dias antigos: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    private Map<String, Object> calculateAggregationForDay(String nomeProduto, int dia, int currentDay) {
        List<Event> eventos = getEventosProdutoDia(nomeProduto, dia, currentDay);
        
        Map<String, Object> result = new HashMap<>();
        if (eventos.isEmpty()) {
            result.put("quantidade_total", 0);
            result.put("volume_total", 0f);
            result.put("preco_max", 0f);
            result.put("preco_min", 0f);
            result.put("preco_medio", 0f);
        } else {
            int qtd = eventos.stream().mapToInt(Event::getQuantidade).sum();
            float volume = eventos.stream().map(Event::getPreco).reduce(0f, Float::sum);
            float max = eventos.stream().map(Event::getPreco).max(Float::compareTo).orElse(0f);
            float min = eventos.stream().map(Event::getPreco).min(Float::compareTo).orElse(0f);
            float medio = volume / eventos.size();
            
            result.put("quantidade_total", qtd);
            result.put("volume_total", volume);
            result.put("preco_max", max);
            result.put("preco_min", min);
            result.put("preco_medio", medio);
        }
        
        return result;
    }
    
    private AggregationCache getOrCalculateCache(String nomeProduto, int dia, int currentDay) {
        int produtoHash = nomeProduto.hashCode();
        
        // Tenta ler
        lock.readLock().lock();
        try {
            Map<Integer, AggregationCache> cacheProduto = aggregationCache.get(produtoHash);
            if (cacheProduto != null) {
                AggregationCache cache = cacheProduto.get(dia);
                if (cache != null && cache.isCalculated()) {
                    return cache;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Calcula
        lock.writeLock().lock();
        try {
            Map<Integer, AggregationCache> cacheProduto = aggregationCache.computeIfAbsent(produtoHash, k -> new HashMap<>());
            
            AggregationCache cache = cacheProduto.get(dia);
            if (cache != null && cache.isCalculated()) {
                return cache;
            }
            
            cache = new AggregationCache(nomeProduto, dia);
            
            // Carregar dados (pode vir do disco)
            DayData dayData = memoryManager.getDayData(username, dia, currentDay);
            List<Event> eventos = dayData.getEventosProdutoInternal(produtoHash);
            cache.calculate(eventos);
            
            cacheProduto.put(dia, cache);
            return cache;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular cache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void invalidateCacheForDay(int produtoHash, int dia) {
        Map<Integer, AggregationCache> cacheProduto = aggregationCache.get(produtoHash);
        if (cacheProduto != null) {
            AggregationCache cache = cacheProduto.get(dia);
            if (cache != null) {
                cache.invalidate();
            }
        }
    }
    
    public Map<String, Object> getMemoryStats() {
        return memoryManager.getMemoryStats();
    }
}