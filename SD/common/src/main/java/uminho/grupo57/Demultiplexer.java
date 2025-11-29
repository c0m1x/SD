package uminho.grupo57;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable
{
    private final TaggedConnection connection;
    private final ReentrantLock lockGlobal = new ReentrantLock();
    private Map<Integer, Entrada> mensagens = new HashMap<>();
    private IOException ioe;

    class Entrada
    {
        public Condition condition = lockGlobal.newCondition();
        public Deque<byte[]> mensagens = new ArrayDeque<>();
    }



    public Demultiplexer(TaggedConnection conn)
    {
        this.connection = conn;
    }

    public void start()
    {
        new Thread(()->
        {
            try{
                for(;;){
                    TaggedConnection.Frame f = connection.receive();
                    lockGlobal.lock();
                    try{
                        Entrada entrada = mensagens.get(f.tag);
                        if(entrada == null)
                        {
                            entrada.mensagens.add(f.data);
                            mensagens.put(f.tag, entrada);
                        }
                        entrada.mensagens.add(f.data);
                        entrada.condition.signalAll();
                    }finally{
                        lockGlobal.unlock();
                    }
                }
            }catch(IOException e){
                lockGlobal.lock();
                try{
                    ioe = e;

                    for(Entrada entrada : mensagens.values())
                    {
                        entrada.condition.signalAll();
                    }
                }finally{
                    lockGlobal.unlock();
                }
            }

        }).start();
    }

    public void close() throws IOException
    {
        lockGlobal.lock();
        try{
            for (Entrada entrada : mensagens.values())
                entrada.condition.signalAll();
        }finally{
            lockGlobal.unlock();
        }
        connection.close();
    }

    public void send(int tag, byte[] data) throws IOException
    {
        connection.send(tag, data);
    }

    public byte[] receive(int tag) throws IOException, InterruptedException
    {
        lockGlobal.lock();
        try {
            Entrada entrada = mensagens.get(tag);
            if(entrada == null)
                mensagens.put(tag, new Entrada());

            while(entrada.mensagens.isEmpty())
            {
                if(ioe != null) //Verificar se a conexão falhou
                    throw ioe;

                entrada.condition.await();
            }
            return entrada.mensagens.poll();
        } finally {
            lockGlobal.unlock();
        }
    }
}