package uminho.grupo57.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Representa a coleção de eventos de um dia (TimeSeries).
 * Fornece operações thread-safe para adicionar e consultar eventos por produto.
 */
public class TimeSeries
{
    private final ReentrantLock lock = new ReentrantLock();
    private final int dia;
    private Map<Integer, List<Event>> eventosPorProduto = new HashMap<>();

    /**
     * Cria uma nova série para um dia específico.
     *
     * @param dia Dia representado por esta série
     */
    public TimeSeries(int dia)
    {
        this.dia = dia;
    }

    /**
     * Cria série com dados já carregados.
     *
     * @param dia Dia representado
     * @param eventosPorProduto Mapa de eventos por hash do produto
     */
    public TimeSeries(int dia, Map<Integer, List<Event>> eventosPorProduto)
    {
        this.dia = dia;
        this.eventosPorProduto = eventosPorProduto;
    }

    /**
     * Adiciona um evento ao dia (thread-safe).
     *
     * @param event Evento a adicionar
     */
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

    /**
     * Retorna eventos de um produto (por nome).
     *
     * @param nomeProduto Nome do produto
     * @return lista de eventos (cópias)
     */
    public List<Event> getEventosProduto(String nomeProduto)
    {
        int produtoHash = nomeProduto.hashCode();
        return getEventosProdutoInternal(produtoHash);
    }

    /**
     * Versão interna por hash de produto.
     */
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

    /** @return conjunto de nomes de produtos presentes na série */
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

    /** @return true se não existirem eventos nesta série */
    public boolean isEmpty()
    {
        lock.lock();
        try{
            return eventosPorProduto.isEmpty();
        }finally{
            lock.unlock();
        }
    }

    /** @return dia desta série */
    public int getDia()
    {
        return dia;
    }

    /**
     * Estima o tamanho em memória (valor aproximado).
     *
     * @return estimativa em bytes
     */
    public long estimateMemorySize() {
        long size = 0;
        for (List<Event> eventos : eventosPorProduto.values()) {
            size += eventos.size() * 100; // Estimativa: ~100 bytes por evento
        }
        return size;
    }
}
