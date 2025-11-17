package uminho.grupo57;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class TimeSeries
{
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private Map<Integer, List<Event>> eventosSerie;
    private LocalDate data;

    public TimeSeries()
    {
        eventosSerie = new HashMap<>();
        data = LocalDate.now();
    }

    public TimeSeries(TimeSeries copia)
    {
        copia.lock.readLock().lock();
        try{
            this.data = copia.data;

            this.eventosSerie = copia.eventosSerie.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            listaEventos -> listaEventos.getValue().stream()
                                    .map(Event::clone)
                                    .collect(Collectors.toList())
                    ));
        }finally{
            copia.lock.readLock().unlock();
        }

    }

    public TimeSeries clone()
    {
        return new TimeSeries(this);
    }


    //* Agregação
    public int getTotalQuantidadeProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            return this.eventosSerie.get(nomeProduto.hashCode())
                                    .stream()
                                    .map(Event::getQuantidade)
                                    .reduce(0, Integer::sum);
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public float getTotalPrecoProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            return this.eventosSerie.get(nomeProduto.hashCode())
                    .stream()
                    .map(Event::getPreco)
                    .reduce(0f, Float::sum);
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public Optional<Float> getPrecoMaximoProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            return this.eventosSerie.get(nomeProduto.hashCode())
                    .stream()
                    .map(Event::getPreco)
                    .max(Float::compareTo);
        }finally{
            this.lock.readLock().unlock();
        }
    }
}
