package uminho.grupo57.clientHandling;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.AggregationCache;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

/**
 * Gestor thread-safe de séries temporais por utilizador
 * Coordena memória, persistência e notificações
 */
public class TimeSeriesManager {

    private final int maxDays;               // Parâmetro D
    private final ReentrantLock lock = new ReentrantLock();
    private int currentDay;

    private final SeriesMemoryManager memoryManager;
    private final NotificationManager notificationManager;

    public TimeSeriesManager(int maxDays, int maxSeriesInMemory, SeriesPersistence persistence)
    {
        this.maxDays = maxDays;

        this.memoryManager = new SeriesMemoryManager(maxSeriesInMemory, persistence);
        this.notificationManager = new NotificationManager();

        this.currentDay = memoryManager.getSavedDay();
        if(currentDay == -1 || currentDay == 0)
            currentDay = 1;

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  GESTOR DE SÉRIES TEMPORAIS INICIALIZADO         ║");
        System.out.println(String.format("║  Parâmetro D (dias): %-28s║", maxDays));
        System.out.println(String.format("║  Parâmetro S (séries): %-26s║", maxSeriesInMemory));
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    public int getCurrentDay()
    {
        lock.lock();
        try{
            return currentDay;
        }finally{
            lock.unlock();
        }
    }


    /**
     * Adiciona evento ao dia corrente e dispara notificações.
     *
     * @param nomeProduto Nome do produto
     * @param quantidade Quantidade
     * @param preco Preço unitário
     * @return Evento criado
     */
    public Event addEvent(String nomeProduto, int quantidade, float preco)
    {
        Event nEvento = new Event(nomeProduto, quantidade, preco, getCurrentDay());
        memoryManager.addEventToCurrentDay(nEvento, getCurrentDay());
        notificationManager.onEventAdded(nEvento.getNome());
        return nEvento;
    }

    /**
     * Consulta eventos de um produto num dia
     */
    public List<Event> getEventosProdutoDia(String produto, int dia)
    {
        try{
            return memoryManager.getDayData(dia, getCurrentDay()).getEventosProduto(produto);
        }catch (IOException e){
            System.err.println("Erro ao obter eventos de um produto no dia: " + e.getMessage());
            return Collections.emptyList();
        }

    }

    /**
     * Produtos do dia corrente
     */
    public Set<String> getAllProdutos()
    {
        try{
            int dia = getCurrentDay();
            return memoryManager.getDayData(dia, dia).getAllProdutos();
        }catch (IOException e){
            System.err.println("Erro ao obter produtos: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Estatísticas de um produto hoje (com cache)
     */
    public AggregationCache getCacheForToday(String produto)
    {
        try{
            return memoryManager.getAggregationCache(produto, getCurrentDay(), getCurrentDay());
        }catch (IOException e){
            System.err.println("Erro ao obter cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Estatísticas de um produto num dia (com cache)
     */
    public AggregationCache getCacheRange(String produto, int ultimoDia)
    {
        try{
            return memoryManager.getAggregationCache(produto, ultimoDia, getCurrentDay());
        }catch (IOException e){
            System.err.println("Erro ao obter cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Avança para o dia seguinte
     */
    public void nextDay()
    {
        lock.lock();
        try{
            currentDay++;

            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("║  DIA " + currentDay + " INICIADO             ║");
            System.out.println("╚════════════════════════════════════════════╝");

            memoryManager.deleteOldestDayIfLowerThanMax(maxDays, getCurrentDay());
            System.out.println("Memória: " + memoryManager.getMemoryStats());
            notificationManager.onDayAdvance();
            System.out.println("Dia " + currentDay + " pronto!\n");
        }finally{
            lock.unlock();
        }
    }

    /**
     * Persiste um evento delegando para o gestor de memória/persistência.
     *
     * @param evento Evento a persistir
     * @param nomeProduto Nome do produto associado
     * @throws IOException Em caso de erro de I/O ao gravar
     */
    public void saveEvent(Event evento, String nomeProduto) throws IOException
    {
        memoryManager.saveEvent(evento, nomeProduto, getCurrentDay());
    }

    /**
     * Retorna o gestor de notificações associado para registar waits/notify.
     *
     * @return instância de {@link NotificationManager}
     */
    public NotificationManager getNotificationManager()
    {
        return notificationManager;
    }

    /**
     * Retorna um mapa produto->eventos filtrado pelos produtos fornecidos
     * para o dia especificado.
     *
     * @param products Conjunto de nomes de produtos a filtrar
     * @param dia Dia para o qual filtrar (número de dias atrás)
     * @return Mapa produto -> lista de eventos
     * @throws IOException Em caso de erro ao ler dados do disco
     */
    public Map<String, List<Event>> getFilteredProducts(Set<String> products, int dia) throws IOException
    {
        try{
            System.out.println("\nA filtrar por produtos...");
            return memoryManager.getEventsForProducts(products, dia, getCurrentDay());
        }catch (IOException e){
            System.err.println("Problema a filtrar por produtos: " + e.getMessage());
            return null;
        }
    }

    /**
     * Persiste o dia atual para disco (usado no shutdown do servidor).
     *
     * @throws IOException Em caso de erro ao gravar
     */
    public void shutdownPersist() throws IOException
    {
        memoryManager.saveCurrentDay(getCurrentDay());
    }
}