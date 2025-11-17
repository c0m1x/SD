package uminho.grupo57;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;

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
            return this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList())
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
            return this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList())
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
            return this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList())
                    .stream()
                    .map(Event::getPreco)
                    .max(Float::compareTo);
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public void addEvent(String nomeProduto, Event evento)
    {
        this.lock.writeLock().lock();
        try{
            this.eventosSerie.computeIfAbsent(nomeProduto.hashCode(), k -> new ArrayList<>())
                            .add(evento.clone());
            this.data = LocalDate.now();
        }finally{
            this.lock.writeLock().unlock();
        }
    }

    public void addEvent(Event evento)
    {
        addEvent(evento.getNome(), evento);
    }

    //* Query
    public List<Event> getEventosProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            List<Event> eventos = this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList());
            return eventos.stream()
                         .map(Event::clone)
                         .collect(Collectors.toList());
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public Set<String> getAllProdutos()
    {
        this.lock.readLock().lock();
        try{
            return this.eventosSerie.values().stream()
                    .flatMap(List::stream)
                    .map(Event::getNome)
                    .collect(Collectors.toSet());
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public Optional<Float> getPrecoMinimoProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            return this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList())
                    .stream()
                    .map(Event::getPreco)
                    .min(Float::compareTo);
        }finally{
            this.lock.readLock().unlock();
        }
    }

    public Optional<Float> getPrecoMedioProduto(String nomeProduto)
    {
        this.lock.readLock().lock();
        try{
            List<Event> eventos = this.eventosSerie.getOrDefault(nomeProduto.hashCode(), Collections.emptyList());
            if(eventos.isEmpty()) return Optional.empty();
            
            float soma = eventos.stream()
                               .map(Event::getPreco)
                               .reduce(0f, Float::sum);
            return Optional.of(soma / eventos.size());
        }finally{
            this.lock.readLock().unlock();
        }
    }
  
}
