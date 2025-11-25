package uminho.grupo57;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dados de um dia específico
 * Contém eventos organizados por produto
 */
public class DayData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int dia;
    private final Map<Integer, List<Event>> eventosPorProduto; // produto_hash -> eventos
    private transient long lastAccessTime;
    
    public DayData(int dia) {
        this.dia = dia;
        this.eventosPorProduto = new HashMap<>();
        this.lastAccessTime = System.currentTimeMillis();
    }
    
    public void addEvent(Event event) {
        int produtoHash = event.getNome().hashCode();
        eventosPorProduto.computeIfAbsent(produtoHash, k -> new ArrayList<>()).add(event);
        updateAccessTime();
    }
    
    public List<Event> getEventosProduto(String nomeProduto) {
        updateAccessTime();
        int produtoHash = nomeProduto.hashCode();
        List<Event> eventos = eventosPorProduto.get(produtoHash);
        if (eventos == null) {
            return Collections.emptyList();
        }
        return eventos.stream().map(Event::clone).collect(Collectors.toList());
    }
    
    public List<Event> getEventosProdutoInternal(int produtoHash) {
        updateAccessTime();
        return eventosPorProduto.getOrDefault(produtoHash, Collections.emptyList());
    }
    
    public Set<String> getAllProdutos() {
        updateAccessTime();
        return eventosPorProduto.values().stream()
                .flatMap(List::stream)
                .map(Event::getNome)
                .collect(Collectors.toSet());
    }
    
    public boolean isEmpty() {
        return eventosPorProduto.isEmpty();
    }
    
    public int getDia() {
        return dia;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    private void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }
    
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