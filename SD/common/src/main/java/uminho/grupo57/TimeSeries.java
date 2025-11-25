package uminho.grupo57;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Série temporal organizada por dias
 * Estrutura: Map<Dia, Map<Produto, List<Event>>>
 */
public class TimeSeries implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Organização por dia: dia -> (produto_hash -> lista de eventos)
    private Map<Integer, Map<Integer, List<Event>>> eventosPorDia;
    
    // Cache de produtos para evitar recálculo constante
    private transient Set<String> cachedProducts = null;

    public TimeSeries() {
        eventosPorDia = new HashMap<>();
    }

    public TimeSeries(TimeSeries copia) {
        copia.lock.readLock().lock();
        try {
            this.eventosPorDia = new HashMap<>();
            for (Map.Entry<Integer, Map<Integer, List<Event>>> diaEntry : copia.eventosPorDia.entrySet()) {
                Map<Integer, List<Event>> produtosNoDia = new HashMap<>();
                for (Map.Entry<Integer, List<Event>> produtoEntry : diaEntry.getValue().entrySet()) {
                    List<Event> eventos = produtoEntry.getValue().stream()
                            .map(Event::clone)
                            .collect(Collectors.toList());
                    produtosNoDia.put(produtoEntry.getKey(), eventos);
                }
                this.eventosPorDia.put(diaEntry.getKey(), produtosNoDia);
            }
        } finally {
            copia.lock.readLock().unlock();
        }
    }

    public TimeSeries clone() {
        return new TimeSeries(this);
    }

    /**
     * Adiciona evento em um dia específico
     */
    public void addEvent(Event evento) {
        this.lock.writeLock().lock();
        try {
            int dia = evento.getDia();
            String nomeProduto = evento.getNome();
            int produtoHash = nomeProduto.hashCode();
            
            eventosPorDia
                .computeIfAbsent(dia, k -> new HashMap<>())
                .computeIfAbsent(produtoHash, k -> new ArrayList<>())
                .add(evento.clone());
            
            // Invalidar cache de produtos
            cachedProducts = null;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Obtém eventos de um produto em um dia específico
     */
    public List<Event> getEventosProdutoDia(String nomeProduto, int dia) {
        this.lock.readLock().lock();
        try {
            Map<Integer, List<Event>> produtosNoDia = eventosPorDia.get(dia);
            if (produtosNoDia == null) {
                return Collections.emptyList();
            }
            
            List<Event> eventos = produtosNoDia.getOrDefault(nomeProduto.hashCode(), Collections.emptyList());
            return eventos.stream()
                    .map(Event::clone)
                    .collect(Collectors.toList());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Obtém todos os eventos de um produto (todos os dias)
     */
    public List<Event> getEventosProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            int produtoHash = nomeProduto.hashCode();
            List<Event> todosEventos = new ArrayList<>();
            
            for (Map<Integer, List<Event>> produtosNoDia : eventosPorDia.values()) {
                List<Event> eventos = produtosNoDia.get(produtoHash);
                if (eventos != null) {
                    todosEventos.addAll(eventos.stream()
                            .map(Event::clone)
                            .collect(Collectors.toList()));
                }
            }
            
            return todosEventos;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Obtém eventos de um produto nos últimos d dias (excluindo dia corrente)
     */
    public List<Event> getEventosProdutoUltimosDias(String nomeProduto, int diaCorrente, int numeroDias) {
        this.lock.readLock().lock();
        try {
            int produtoHash = nomeProduto.hashCode();
            List<Event> eventos = new ArrayList<>();
            
            int diaInicio = Math.max(0, diaCorrente - numeroDias);
            
            for (int dia = diaInicio; dia < diaCorrente; dia++) {
                Map<Integer, List<Event>> produtosNoDia = eventosPorDia.get(dia);
                if (produtosNoDia != null) {
                    List<Event> eventosDia = produtosNoDia.get(produtoHash);
                    if (eventosDia != null) {
                        eventos.addAll(eventosDia.stream()
                                .map(Event::clone)
                                .collect(Collectors.toList()));
                    }
                }
            }
            
            return eventos;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Obtém todos os produtos (de todos os dias)
     */
    public Set<String> getAllProdutos() {
        this.lock.readLock().lock();
        try {
            if (cachedProducts != null) {
                return new HashSet<>(cachedProducts);
            }
            
            Set<String> produtos = eventosPorDia.values().stream()
                    .flatMap(dia -> dia.values().stream())
                    .flatMap(List::stream)
                    .map(Event::getNome)
                    .collect(Collectors.toSet());
            
            cachedProducts = produtos;
            return new HashSet<>(produtos);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Obtém produtos vendidos em um dia específico
     */
    public Set<String> getProdutosDia(int dia) {
        this.lock.readLock().lock();
        try {
            Map<Integer, List<Event>> produtosNoDia = eventosPorDia.get(dia);
            if (produtosNoDia == null) {
                return Collections.emptySet();
            }
            
            return produtosNoDia.values().stream()
                    .flatMap(List::stream)
                    .map(Event::getNome)
                    .collect(Collectors.toSet());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    // ============ AGREGAÇÕES ============

    /**
     * Quantidade total de um produto (todos os dias)
     */
    public int getTotalQuantidadeProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            return getEventosProduto(nomeProduto).stream()
                    .mapToInt(Event::getQuantidade)
                    .sum();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Quantidade total de um produto nos últimos d dias
     */
    public int getTotalQuantidadeProdutoUltimosDias(String nomeProduto, int diaCorrente, int numeroDias) {
        this.lock.readLock().lock();
        try {
            return getEventosProdutoUltimosDias(nomeProduto, diaCorrente, numeroDias).stream()
                    .mapToInt(Event::getQuantidade)
                    .sum();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço total de um produto (todos os dias)
     */
    public float getTotalPrecoProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            return getEventosProduto(nomeProduto).stream()
                    .map(Event::getPreco)
                    .reduce(0f, Float::sum);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Volume total de vendas nos últimos d dias
     */
    public float getVolumeTotalUltimosDias(String nomeProduto, int diaCorrente, int numeroDias) {
        this.lock.readLock().lock();
        try {
            return getEventosProdutoUltimosDias(nomeProduto, diaCorrente, numeroDias).stream()
                    .map(Event::getPreco)
                    .reduce(0f, Float::sum);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço máximo de um produto
     */
    public Optional<Float> getPrecoMaximoProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            return getEventosProduto(nomeProduto).stream()
                    .map(Event::getPreco)
                    .max(Float::compareTo);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço máximo nos últimos d dias
     */
    public Optional<Float> getPrecoMaximoUltimosDias(String nomeProduto, int diaCorrente, int numeroDias) {
        this.lock.readLock().lock();
        try {
            return getEventosProdutoUltimosDias(nomeProduto, diaCorrente, numeroDias).stream()
                    .map(Event::getPreco)
                    .max(Float::compareTo);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço mínimo de um produto
     */
    public Optional<Float> getPrecoMinimoProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            return getEventosProduto(nomeProduto).stream()
                    .map(Event::getPreco)
                    .min(Float::compareTo);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço médio de um produto
     */
    public Optional<Float> getPrecoMedioProduto(String nomeProduto) {
        this.lock.readLock().lock();
        try {
            List<Event> eventos = getEventosProduto(nomeProduto);
            if (eventos.isEmpty()) return Optional.empty();
            
            float soma = eventos.stream()
                    .map(Event::getPreco)
                    .reduce(0f, Float::sum);
            return Optional.of(soma / eventos.size());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Preço médio nos últimos d dias
     */
    public Optional<Float> getPrecoMedioUltimosDias(String nomeProduto, int diaCorrente, int numeroDias) {
        this.lock.readLock().lock();
        try {
            List<Event> eventos = getEventosProdutoUltimosDias(nomeProduto, diaCorrente, numeroDias);
            if (eventos.isEmpty()) return Optional.empty();
            
            float soma = eventos.stream()
                    .map(Event::getPreco)
                    .reduce(0f, Float::sum);
            return Optional.of(soma / eventos.size());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Remove eventos de dias antigos (para gestão de memória)
     */
    public void removeEventosAnterioresA(int diaMinimo) {
        this.lock.writeLock().lock();
        try {
            eventosPorDia.keySet().removeIf(dia -> dia < diaMinimo);
            cachedProducts = null; // Invalidar cache
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Obtém todos os dias com eventos
     */
    public Set<Integer> getDiasComEventos() {
        this.lock.readLock().lock();
        try {
            return new HashSet<>(eventosPorDia.keySet());
        } finally {
            this.lock.readLock().unlock();
        }
    }

    /**
     * Verifica se há eventos em um dia específico
     */
    public boolean temEventosNoDia(int dia) {
        this.lock.readLock().lock();
        try {
            Map<Integer, List<Event>> produtosNoDia = eventosPorDia.get(dia);
            return produtosNoDia != null && !produtosNoDia.isEmpty();
        } finally {
            this.lock.readLock().unlock();
        }
    }
}