package uminho.grupo57;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPool
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    private Queue<Runnable> waitingPool = new ArrayDeque<>();
    private final Runner[] runners;
    private final int max;
    private boolean shutdown = false;

    public ThreadPool(int max)
    {
        this.max = max;
        this.runners = new Runner[max];

        for(int i=0; i<max; i++) //Começa a correr cada thread Runner
        {
            this.runners[i] = new Runner();
            this.runners[i].start();
        }
    }

    private class Runner extends Thread
    {
        public void run()
        {
            while(true)
            {
                Runnable task;

                lock.lock(); //Arranjar tarefa se existirem ações à espera
                try{
                    while(waitingPool.isEmpty() && !shutdown)
                        cond.await();
                    if(shutdown && waitingPool.isEmpty())
                        return;
                    task = waitingPool.poll();

                }catch (InterruptedException e){
                    return;

                }finally{
                    lock.unlock();
                }

                task.run(); //Correr a tarefa nova
            }
        }
    }


    public void submitTask(Runnable task) throws InterruptedException
    {
        lock.lock();
        {
            try{
               if(shutdown)
                   throw new IllegalStateException("Impossivel adicionar tarefa, ThreadPool está a fechar");

               waitingPool.add(task);
               cond.signal();

            }finally{
               lock.unlock();
            }
        }
    }


    public void shutdown()
    {
        lock.lock();
        {
            try{
                shutdown = true;
                cond.signalAll();
            }finally{
                lock.unlock();
            }
        }
    }
}