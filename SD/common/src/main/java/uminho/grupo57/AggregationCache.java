package uminho.grupo57;

import java.io.Serializable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache de agregações para um produto em um dia específico
 * Calcula on-demand e mantém resultados em cache
 */
public class AggregationCache implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final String produto;
    private final int dia;
    
    // Dados guardados em cache
    private Integer quantidadeTotal = null;
    private Float volumeTotal = null;
    private Float precoMaximo = null;
    private Float precoMinimo = null;
    private Float precoMedio = null;
    
    private boolean isCalculated = false;
    
    public AggregationCache(String produto, int dia) {
        this.produto = produto;
        this.dia = dia;
    }
    
    /**
     * Verifica se a cache foi calculada
     */
    public boolean isCalculated() {
        lock.readLock().lock();
        try {
            return isCalculated;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Calcula agregações a partir de uma lista de eventos
     */
    public void calculate(java.util.List<Event> eventos) {
        lock.writeLock().lock();
        try {
            if (eventos.isEmpty()) {
                quantidadeTotal = 0;
                volumeTotal = 0f;
                precoMaximo = 0f;
                precoMinimo = 0f;
                precoMedio = 0f;
            } else {
                quantidadeTotal = eventos.stream()
                        .mapToInt(Event::getQuantidade)
                        .sum();
                
                volumeTotal = eventos.stream()
                        .map(Event::getPreco)
                        .reduce(0f, Float::sum);
                
                precoMaximo = eventos.stream()
                        .map(Event::getPreco)
                        .max(Float::compareTo)
                        .orElse(0f);
                
                precoMinimo = eventos.stream()
                        .map(Event::getPreco)
                        .min(Float::compareTo)
                        .orElse(0f);
                
                precoMedio = volumeTotal / eventos.size();
            }
            
            isCalculated = true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Invalida a cache
     */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            quantidadeTotal = null;
            volumeTotal = null;
            precoMaximo = null;
            precoMinimo = null;
            precoMedio = null;
            isCalculated = false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Getters
    
    public String getProduto() {
        return produto;
    }
    
    public int getDia() {
        return dia;
    }
    
    public Integer getQuantidadeTotal() {
        lock.readLock().lock();
        try {
            return quantidadeTotal;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Float getVolumeTotal() {
        lock.readLock().lock();
        try {
            return volumeTotal;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Float getPrecoMaximo() {
        lock.readLock().lock();
        try {
            return precoMaximo;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Float getPrecoMinimo() {
        lock.readLock().lock();
        try {
            return precoMinimo;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Float getPrecoMedio() {
        lock.readLock().lock();
        try {
            return precoMedio;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return String.format("AggregationCache{produto='%s', dia=%d, qtd=%d, volume=%.2f, max=%.2f, min=%.2f, med=%.2f, calculated=%b}",
                    produto, dia, quantidadeTotal, volumeTotal, precoMaximo, precoMinimo, precoMedio, isCalculated);
        } finally {
            lock.readLock().unlock();
        }
    }
}