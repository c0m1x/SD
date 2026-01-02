package uminho.grupo57.clientHandling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gere notificações bloqueantes para condições de vendas.
 * <p>Fornece wait/notify para duas condições: vendas simultâneas e vendas consecutivas.</p>
 * Thread-safe usando {@link ReentrantLock} e {@link java.util.concurrent.locks.Condition}.
 */
public class NotificationManager
{

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, SimultaneousWait> simultaneousWaits = new HashMap<>();
    private final Map<String, ConsecutiveTracker> consecutiveTrackers = new HashMap<>();

    private class SimultaneousWait
    {
        Condition condition = lock.newCondition();
        Set<String> produtosVendidos = new HashSet<>();
        boolean satisfied = false;
        boolean dayEnded = false;
    }

    private class ConsecutiveTracker {
        int currentStreak = 0;
        List<ConsecutiveWait> waits = new ArrayList<>();
    }

    private class ConsecutiveWait {
        Condition condition = lock.newCondition();
        int targetStreak;
        boolean satisfied = false;
        boolean dayEnded = false;
    }

    /**
     * Deve ser chamado quando um evento de venda for adicionado.
     * Desperta waits relevantes (simultâneo/consecutivo).
     *
     * @param produto Nome do produto vendido
     */
    public void onEventAdded(String produto)
    {
        lock.lock();
        try{
            for(Map.Entry<String, SimultaneousWait> entry : simultaneousWaits.entrySet())
            {
                SimultaneousWait wait = entry.getValue();
                wait.produtosVendidos.add(produto);

                String[] produtos = entry.getKey().split(":");
                if(wait.produtosVendidos.contains(produtos[0]) && wait.produtosVendidos.contains(produtos[1]))
                {
                    wait.satisfied = true;
                    wait.condition.signalAll();
                }
            }

            ConsecutiveTracker tracker = consecutiveTrackers.get(produto);
            if(tracker != null)
            {
                tracker.currentStreak++;
                for(ConsecutiveWait wait : tracker.waits)
                {
                    if(tracker.currentStreak >= wait.targetStreak && !wait.satisfied)
                    {
                        wait.satisfied = true;
                        wait.condition.signalAll();
                    }
                }
            }

            for(Map.Entry<String, ConsecutiveTracker> entry : consecutiveTrackers.entrySet())
            {
                if(!entry.getKey().equals(produto))
                    entry.getValue().currentStreak = 0;
            }
        }finally{
            lock.unlock();
        }
    }

    /**
     * Notifica que o dia avançou, acordando e limpando waits pendentes.
     */
    public void onDayAdvance()
    {
        lock.lock();
        try{
            for(SimultaneousWait wait : simultaneousWaits.values())
            {
                wait.dayEnded = true;
                wait.condition.signalAll();
            }
            simultaneousWaits.clear();

            for(ConsecutiveTracker tracker : consecutiveTrackers.values())
            {
                for(ConsecutiveWait wait : tracker.waits)
                {
                    wait.dayEnded = true;
                    wait.condition.signalAll();
                }
                tracker.waits.clear();
                tracker.currentStreak = 0;
            }

        }finally{
            lock.unlock();
        }
    }

    /**
     * Bloqueia até que ambos os produtos tenham sido vendidos no dia corrente
     * ou até o dia avançar.
     *
     * @param produto1 Primeiro produto
     * @param produto2 Segundo produto
     * @return {@code true} se a condição foi satisfeita, {@code false} se o dia acabou
     * @throws InterruptedException Se a thread for interrompida enquanto espera
     */
    public boolean waitSimultaneous(String produto1, String produto2) throws InterruptedException
    {
        lock.lock();
        try{
            String key = produto1.compareTo(produto2) < 0 ? produto1 + ":" + produto2 : produto2 + ":" + produto1;
            SimultaneousWait wait = simultaneousWaits.computeIfAbsent(key, k -> new SimultaneousWait());

            String[] produtos = key.split(":");
            if(wait.produtosVendidos.contains(produtos[0]) && wait.produtosVendidos.contains(produtos[1]))
                return true;

            while(!wait.satisfied && !wait.dayEnded)
                wait.condition.await();
            return wait.satisfied;

        }finally{
            lock.unlock();
        }
    }

    /**
     * Bloqueia até que um produto tenha sido vendido N vezes consecutivas
     * ou até o dia avançar.
     *
     * @param produto Nome do produto
     * @param n Número de vendas consecutivas a aguardar
     * @return {@code true} se a condição foi satisfeita, {@code false} se o dia acabou
     * @throws InterruptedException Se a thread for interrompida enquanto espera
     */
    public boolean waitConsecutive(String produto, int n) throws InterruptedException
    {
        lock.lock();
        try{
            ConsecutiveTracker tracker = consecutiveTrackers.computeIfAbsent(produto, k -> new ConsecutiveTracker());
            if(tracker.currentStreak >= n)
                return true;

            ConsecutiveWait wait = new ConsecutiveWait();
            wait.targetStreak = n;
            tracker.waits.add(wait);

            while(!wait.satisfied && !wait.dayEnded)
                wait.condition.await();
            return wait.satisfied;
        }finally{
            lock.unlock();
        }
    }
}