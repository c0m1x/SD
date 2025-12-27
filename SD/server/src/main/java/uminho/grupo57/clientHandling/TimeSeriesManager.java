package uminho.grupo57.clientHandling;

import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.AggregationCache;
import uminho.grupo57.storage.SeriesMemoryManager;
import uminho.grupo57.storage.SeriesPersistence;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

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

    public TimeSeriesManager(int maxDays, int maxSeriesInMemory, String dataDirectory)
    {
        this.maxDays = maxDays;

        SeriesPersistence seriesPersistence = new SeriesPersistence(dataDirectory);
        this.memoryManager = new SeriesMemoryManager(maxSeriesInMemory, seriesPersistence);
        this.notificationManager = new NotificationManager();

        this.currentDay = memoryManager.getSavedDay();
        if(currentDay == -1 || currentDay == 0)
            currentDay = 1;

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  GESTOR DE SÉRIES TEMPORAIS INICIALIZADO         ║");
        System.out.println(String.format("║  Parâmetro D (dias): %-28s║", maxDays));
        System.out.println(String.format("║  Parâmetro S (séries): %-26s║", maxSeriesInMemory));
        System.out.println(String.format("║  Diretório dados: %-31s║", dataDirectory));
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
     * Adiciona evento ao dia corrente
     */
    public void addEvent(String username, String nomeProduto, int quantidade, float preco)
    {
        Event nEvento = new Event(nomeProduto, quantidade, preco, getCurrentDay());
        memoryManager.addEventToCurrentDay(username, nEvento, getCurrentDay());
        notificationManager.onEventAdded(username, nEvento.getNome());
    }

    /**
     * Consulta eventos de um produto num dia
     */
    public List<Event> getEventosProdutoDia(String username, String produto, int dia)
    {
        try{
            return memoryManager.getDayData(username, dia, getCurrentDay()).getEventosProduto(produto);
        }catch (IOException e){
            System.err.println("Erro ao obter eventos de um produto no dia: " + e.getMessage());
            return Collections.emptyList();
        }

    }

    /**
     * Produtos do dia corrente
     */
    public Set<String> getAllProdutos(String username)
    {
        try{
            return memoryManager.getDayData(username, getCurrentDay(), getCurrentDay()).getAllProdutos();
        }catch (IOException e){
            System.err.println("Erro ao obter produtos: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Estatísticas de um produto hoje (com cache)
     */
    public AggregationCache getCacheForToday(String username, String produto)
    {
        try{
            return memoryManager.getAggregationCache(username, produto, getCurrentDay(), getCurrentDay());
        }catch (IOException e){
            System.err.println("Erro ao obter cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Estatísticas de um produto num dia (com cache)
     */
    public AggregationCache getCacheRange(String username, String produto, int ultimoDia)
    {
        try{
            return memoryManager.getAggregationCache(username, produto, ultimoDia, getCurrentDay());
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
            int previousDay = currentDay;
            currentDay++;

            System.out.println("\n╔════════════════════════════════════════════╗");
            System.out.println("║  DIA " + currentDay + " INICIADO                            ║");
            System.out.println("╚════════════════════════════════════════════╝");

            memoryManager.removeDaysOlderThanAll(currentDay - maxDays);

            System.out.println("Memória: " + memoryManager.getMemoryStats());
            notificationManager.onDayAdvance();
            System.out.println("Dia " + currentDay + " pronto!\n");

        }catch (IOException e){
            throw new RuntimeException(e);
        }finally{
            lock.unlock();
        }
    }

    /**
     * Encerramento limpo
     */
    public void persistAll() throws IOException
    {
        try{
            memoryManager.persistAll(getCurrentDay(), false);
            memoryManager.saveCurrentDay(getCurrentDay());
        }catch (IOException e){
            System.err.println("Problema a guardar as séries: " + e.getMessage());
        }
    }

    public NotificationManager getNotificationManager()
    {
        return notificationManager;
    }

    public Map<String, List<Event>> getFilteredProducts(String username, Set<String> products, int dia) throws IOException
    {
        try{
            System.out.println("\nA filtrar por produtos...");
            return memoryManager.getEventsForProducts(username, products, dia, getCurrentDay());
        }catch (IOException e){
            System.err.println("Problema a filtrar por produtos: " + e.getMessage());
            return null;
        }
    }


    public void shutdownPersist() throws IOException
    {
        memoryManager.persistAll(getCurrentDay(), true);
        memoryManager.saveCurrentDay(getCurrentDay());
    }
}