package uminho.grupo57;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable
{
    private final TaggedConnection connection;
    private final ReentrantLock lockGlobal = new ReentrantLock();
    private final ReentrantLock lockIO = new ReentrantLock();
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


    private byte[] checkTagQueue(int tag, Entrada entrada) throws IOException
    {
        lockGlobal.lock();
        try{
            if(!entrada.mensagens.isEmpty())
                return entrada.mensagens.poll();
            return null;
        }finally{
            lockGlobal.unlock();
        }
    }


    private TaggedConnection.Frame readFrame() throws IOException
    {
        lockIO.lock();
        try
        {
            return connection.receive();

        }catch (IOException e){
            lockGlobal.lock();
            try
            {
                ioe = e;
                for(Entrada entrada : mensagens.values())
                    entrada.condition.signalAll();

            }finally{
                lockGlobal.unlock();
            }
            throw e;

        }finally{
            lockIO.unlock();
        }
    }


    private byte[] waitCorrectFrame(int tag, Entrada entrada, TaggedConnection.Frame frame) throws IOException, InterruptedException
    {
        lockGlobal.lock();
        try
        {
            Entrada entradaAlvoFrame = mensagens.get(frame.tag);
            if(entradaAlvoFrame == null)
            {
                entradaAlvoFrame = new Entrada();
                mensagens.put(frame.tag, entradaAlvoFrame);
            }

            entradaAlvoFrame.mensagens.add(frame.data);
            entradaAlvoFrame.condition.signalAll();

            if(frame.tag == tag)
                return entradaAlvoFrame.mensagens.poll(); //Se o frame que recebemos tem a mesma tag que a que queremos retorna

            while(entrada.mensagens.isEmpty())
            {
                if(ioe != null) //Testa se a conexão está a funcionar
                    throw ioe;
                entrada.condition.await();
            }
            return entrada.mensagens.poll(); //Mal receba uma mensagem return

        }finally{
            lockGlobal.unlock();
        }
    }

    //TODO: Confirmar se podemos ter estes locks e unlocks a meio
    //TODO: Confirmar se é melhor adicionar timeouts
    public byte[] receive(int tag) throws IOException, InterruptedException
    {
        Entrada entrada; //Confirmamos se existe uma entrada para esta tag, se não existir adicionamos
        lockGlobal.lock();
        try
        {
            entrada = mensagens.get(tag);
            if(entrada == null)
            {
                entrada = new Entrada();
                mensagens.put(tag, entrada);
            }

        }finally{
            lockGlobal.unlock();
        }

        for(;;) //Loop até arranjar uma resposta
        {
            byte[] resposta = checkTagQueue(tag, entrada); //Se já houver uma mensagem na Deque removemo-la e retornamo-la
            if(resposta != null)
                return resposta;

            TaggedConnection.Frame frame = readFrame(); //Lê um frame do socket
            resposta = waitCorrectFrame(tag, entrada, frame);
            if(resposta != null)
                return resposta;
        }
    }
}