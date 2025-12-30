package uminho.grupo57.entities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class TimeSeries
{
    private final ReentrantLock lock = new ReentrantLock();
    private final int dia;
    private Map<Integer, List<Event>> eventosPorProduto = new HashMap<>();

    public TimeSeries(int dia)
    {
        this.dia = dia;
    }

    public TimeSeries(int dia, Map<Integer, List<Event>> eventosPorProduto)
    {
        this.dia = dia;
        this.eventosPorProduto = eventosPorProduto;
    }

    public void addEvent(Event event)
    {
        int produtoHash = event.getNome().hashCode();
        lock.lock();
        try{
            eventosPorProduto.computeIfAbsent(produtoHash, k -> new ArrayList<>()).add(event);
        }finally{
            lock.unlock();
        }
    }

    public List<Event> getEventosProduto(String nomeProduto)
    {
        int produtoHash = nomeProduto.hashCode();
        return getEventosProdutoInternal(produtoHash);
    }

    public List<Event> getEventosProdutoInternal(int produtoHash)
    {
        lock.lock();
        try{
            List<Event> eventos = eventosPorProduto.get(produtoHash);
            if(eventos == null)
                return Collections.emptyList();
            return eventos.stream().map(Event::clone)
                                   .collect(Collectors.toList());
        }finally{
            lock.unlock();
        }
    }

    public Set<String> getAllProdutos()
    {
        lock.lock();
        try{
            return eventosPorProduto.values().stream()
                    .flatMap(List::stream)
                    .map(Event::getNome)
                    .collect(Collectors.toSet());
        }finally{
            lock.unlock();
        }
    }

    public boolean isEmpty()
    {
        lock.lock();
        try{
            return eventosPorProduto.isEmpty();
        }finally{
            lock.unlock();
        }
    }

    public int getDia()
    {
        return dia;
    }

    //public long getLastAccessTime() {
    //    return lastAccessTime;
    //}

    //private void updateAccessTime() {
      //  this.lastAccessTime = System.currentTimeMillis();
    //}

    /**
     * Estima tamanho em memória (aproximado)
     */
    public long estimateMemorySize() {
        long size = 0;
        for (List<Event> eventos : eventosPorProduto.values()) {
            size += eventos.size() * 100; // Estimativa: ~100 bytes por evento
        }
        return size;
    }
}
