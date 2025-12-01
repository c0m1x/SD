package uminho.grupo57;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gere notificações bloqueantes para condições de vendas
 * Thread-safe com Condition variables
 */
public class NotificationManager {
    
    private final ReentrantLock lock = new ReentrantLock();
    
    // WAIT_SIMULTANEOUS: username -> "produto1:produto2" -> SimultaneousWait
    private final Map<String, Map<String, SimultaneousWait>> simultaneousWaits = new HashMap<>();
    
    // WAIT_CONSECUTIVE: username -> produto -> ConsecutiveTracker
    private final Map<String, Map<String, ConsecutiveTracker>> consecutiveTrackers = new HashMap<>();
    
    private class SimultaneousWait {
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
     * Chamado quando evento é adicionado
     */
    public void onEventAdded(String username, String produto) {
        lock.lock();
        try {
            // Verificar WAIT_SIMULTANEOUS
            Map<String, SimultaneousWait> userSimWaits = simultaneousWaits.get(username);
            if (userSimWaits != null) {
                for (Map.Entry<String, SimultaneousWait> entry : userSimWaits.entrySet()) {
                    SimultaneousWait wait = entry.getValue();
                    wait.produtosVendidos.add(produto);
                    
                    String[] produtos = entry.getKey().split(":");
                    if (wait.produtosVendidos.contains(produtos[0]) && 
                        wait.produtosVendidos.contains(produtos[1])) {
                        wait.satisfied = true;
                        wait.condition.signalAll();
                    }
                }
            }
            
            // Verificar WAIT_CONSECUTIVE
            Map<String, ConsecutiveTracker> userConsTrackers = consecutiveTrackers.get(username);
            if (userConsTrackers != null) {
                ConsecutiveTracker tracker = userConsTrackers.get(produto);
                if (tracker != null) {
                    tracker.currentStreak++;
                    
                    for (ConsecutiveWait wait : tracker.waits) {
                        if (tracker.currentStreak >= wait.targetStreak && !wait.satisfied) {
                            wait.satisfied = true;
                            wait.condition.signalAll();
                        }
                    }
                }
                
                // Resetar outros produtos (quebra consecutividade)
                for (Map.Entry<String, ConsecutiveTracker> entry : userConsTrackers.entrySet()) {
                    if (!entry.getKey().equals(produto)) {
                        entry.getValue().currentStreak = 0;
                    }
                }
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Chamado ao avançar dia - notifica threads bloqueadas
     */
    public void onDayAdvance() {
        lock.lock();
        try {
            // Notificar WAIT_SIMULTANEOUS
            for (Map<String, SimultaneousWait> userWaits : simultaneousWaits.values()) {
                for (SimultaneousWait wait : userWaits.values()) {
                    wait.dayEnded = true;
                    wait.condition.signalAll();
                }
            }
            simultaneousWaits.clear();
            
            // Notificar WAIT_CONSECUTIVE
            for (Map<String, ConsecutiveTracker> userTrackers : consecutiveTrackers.values()) {
                for (ConsecutiveTracker tracker : userTrackers.values()) {
                    for (ConsecutiveWait wait : tracker.waits) {
                        wait.dayEnded = true;
                        wait.condition.signalAll();
                    }
                    tracker.waits.clear();
                    tracker.currentStreak = 0;
                }
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Bloqueia até p1 E p2 vendidos no dia corrente
     */
    public boolean waitSimultaneous(String username, String produto1, String produto2) 
            throws InterruptedException {
        lock.lock();
        try {
            String key = produto1.compareTo(produto2) < 0 
                ? produto1 + ":" + produto2 
                : produto2 + ":" + produto1;
            
            Map<String, SimultaneousWait> userWaits = simultaneousWaits.computeIfAbsent(
                username, k -> new HashMap<>());
            
            SimultaneousWait wait = userWaits.computeIfAbsent(key, k -> new SimultaneousWait());
            
            while (!wait.satisfied && !wait.dayEnded) {
                wait.condition.await();
            }
            
            return wait.satisfied;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Bloqueia até n vendas consecutivas do mesmo produto
     */
    public boolean waitConsecutive(String username, String produto, int n) 
            throws InterruptedException {
        lock.lock();
        try {
            Map<String, ConsecutiveTracker> userTrackers = consecutiveTrackers.computeIfAbsent(
                username, k -> new HashMap<>());
            
            ConsecutiveTracker tracker = userTrackers.computeIfAbsent(
                produto, k -> new ConsecutiveTracker());
            
            // Se já satisfeito
            if (tracker.currentStreak >= n) {
                return true;
            }
            
            ConsecutiveWait wait = new ConsecutiveWait();
            wait.targetStreak = n;
            tracker.waits.add(wait);
            
            while (!wait.satisfied && !wait.dayEnded) {
                wait.condition.await();
            }
            
            return wait.satisfied;
            
        } finally {
            lock.unlock();
        }
    }
}
