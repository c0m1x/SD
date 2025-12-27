package uminho.grupo57.storage;

import uminho.grupo57.entities.Event;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Classe que representa a cache de agregações para um produto num dia específico
 * Faz cálculos on-demand e mantém resultados em cache
 */
public class AggregationCache
{
    
    private final ReentrantLock lock = new ReentrantLock();
    
    private final String produto;
    private final int dia;
    
    // Dados guardados em cache
    private Integer quantidadeTotal = null;
    private Float volumeTotal = null;
    private Float precoMaximo = null;
    private Float precoMinimo = null;
    private Float precoMedio = null;
    
    private boolean isCalculated = false;
    
    public AggregationCache(String produto, int dia)
    {
        this.produto = produto;
        this.dia = dia;
    }
    
    /**
     * Método que verifica se a cache foi cálculada ou não
     * @return Um boolean a true se a cache já foi calculada e false caso contrário
     */
    public boolean isCalculated() {
        lock.lock();
        try{
            return isCalculated;
        }finally{
            lock.unlock();
        }
    }
    
    /**
     * Método que calcula a cache para uma lista de eventos
     * @param eventos Uma lista de eventos que dita quais os eventos que entram no cálculo
     */
    public void calculate(List<Event> eventos)
    {
        lock.lock();
        try{
            if(eventos.isEmpty())
            {
                quantidadeTotal = 0;
                volumeTotal = 0f;
                precoMaximo = 0f;
                precoMinimo = 0f;
                precoMedio = 0f;
            }else{
                quantidadeTotal = eventos.stream()
                        .mapToInt(Event::getQuantidade)
                        .sum();

                volumeTotal = eventos.stream()
                        .map(e -> e.getPreco() * e.getQuantidade())
                        .reduce(0f, Float::sum);

                precoMaximo = eventos.stream()
                        .map(Event::getPreco)
                        .max(Float::compareTo)
                        .orElse(0f);
                
                precoMinimo = eventos.stream()
                        .map(Event::getPreco)
                        .min(Float::compareTo)
                        .orElse(0f);
                
                precoMedio = volumeTotal / quantidadeTotal;
            }
            
            isCalculated = true;
        }finally{
            lock.unlock();
        }
    }

    /**
     * Acumula os valores de outra AggregationCache nesta
     * Assume que ambas estão calculadas
     */
    public void merge(AggregationCache other)
    {
        if(other == null)
            return;

        lock.lock();
        other.lock.lock();
        try {
            if(!other.isCalculated) // Se a outra cache não foi calculada manter os valores desta
                return;

            if(!this.isCalculated) // Se esta cache ainda não foi calculada, copiar diretamente
            {
                this.quantidadeTotal = other.quantidadeTotal;
                this.volumeTotal = other.volumeTotal;
                this.precoMaximo = other.precoMaximo;
                this.precoMinimo = other.precoMinimo;
                this.precoMedio = other.precoMedio;
                this.isCalculated = true;
                return;
            }

            this.quantidadeTotal += other.quantidadeTotal;
            this.volumeTotal += other.volumeTotal;
            this.precoMaximo = Math.max(this.precoMaximo, other.precoMaximo);
            this.precoMinimo = Math.min(this.precoMinimo, other.precoMinimo);

            if(this.quantidadeTotal > 0) //Preço médio
                this.precoMedio = this.volumeTotal / this.quantidadeTotal;
            else
                this.precoMedio = 0f;

            this.isCalculated = true;
        }finally{
            other.lock.unlock();
            lock.unlock();
        }
    }


    /**
     * Método que invalida a cache
     */
    public void invalidate()
    {
        lock.lock();
        try{
            quantidadeTotal = null;
            volumeTotal = null;
            precoMaximo = null;
            precoMinimo = null;
            precoMedio = null;
            isCalculated = false;
        }finally{
            lock.unlock();
        }
    }
    
    // Getters

    /**
     * Método que retorna o Produto a que a cache se refere
     * @return O produto referido
     */
    public String getProduto() {
        return produto;
    }

    /**
     * Método que retorna o dia a que a cache se refere
     * @return O dia referido
     */
    public int getDia() {
        return dia;
    }

    /**
     * Método que retorna a quantidade total
     * @return O produto referido
     */
    public Integer getQuantidadeTotal() {
        lock.lock();
        try{
            return quantidadeTotal;
        }finally{
            lock.unlock();
        }
    }
    
    public Float getVolumeTotal() {
        lock.lock();
        try{
            return volumeTotal;
        }finally{
            lock.unlock();
        }
    }
    
    public Float getPrecoMaximo() {
        lock.lock();
        try{
            return precoMaximo;
        }finally{
            lock.unlock();
        }
    }
    
    public Float getPrecoMinimo() {
        lock.lock();
        try{
            return precoMinimo;
        }finally{
            lock.unlock();
        }
    }
    
    public Float getPrecoMedio() {
        lock.lock();
        try{
            return precoMedio;
        }finally{
            lock.unlock();
        }
    }
    
    @Override
    public String toString() {
        lock.lock();
        try{
            return String.format("AggregationCache{produto='%s', dia=%d, qtd=%d, volume=%.2f, max=%.2f, min=%.2f, med=%.2f, calculated=%b}",
                    produto, dia, quantidadeTotal, volumeTotal, precoMaximo, precoMinimo, precoMedio, isCalculated);
        }finally{
            lock.unlock();
        }
    }
}