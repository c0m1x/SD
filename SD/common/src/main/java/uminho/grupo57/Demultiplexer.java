package uminho.grupo57;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Demultiplexer para TaggedConnection
 * - Encapsula uma TaggedConnection
 * - Delega o envio de mensagens para a TaggedConnection
 * - Disponibiliza receive(int tag) que bloqueia até chegar uma mensagem com a tag especificada
 * - Thread dedicada lê continuamente da TaggedConnection e distribui mensagens por tag
 * - Usa um mapa de inteiros para filas (Deque) onde são depositadas as mensagens por tag
 * - Cada tag tem uma variável de condição para notificar threads à espera
 */
public class Demultiplexer implements AutoCloseable
{
    /**
     * Estrutura para guardar mensagens de uma tag específica
     * e sincronizar threads que esperam por mensagens dessa tag
     */
    private static class TagQueue {
        final Deque<Protocol.Message> queue = new ArrayDeque<>();
        final Condition hasMessages;

        TagQueue(Condition condition) {
            this.hasMessages = condition;
        }
    }

    private final TaggedConnection connection;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Integer, TagQueue> tagQueues = new HashMap<>();

    private Thread readerThread;
    private boolean closed = false;
    private IOException readerException = null;

    public Demultiplexer(TaggedConnection conn)
    {
        this.connection = conn;
    }

    /**
     * Inicia a thread de leitura que processa mensagens recebidas
     */
    public void start() {
        readerThread = new Thread(() -> {
            try {
                while (!closed) {
                    TaggedConnection.Frame frame = connection.receive();

                    lock.lock();
                    try {
                        TagQueue tq = tagQueues.get(frame.tag); // Obter ou criar a queue para esta tag
                        if (tq == null) {
                            tq = new TagQueue(lock.newCondition());
                            tagQueues.put(frame.tag, tq);
                        }
                        tq.queue.addLast(frame.data); // Adicionar mensagem à queue
                        tq.hasMessages.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                lock.lock(); // Guardar exceção para passar às threads que bloqueadas
                try {
                    if (!closed)
                    {
                        readerException = e;
                        for (TagQueue tq : tagQueues.values())
                        {
                            tq.hasMessages.signalAll();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        });
        readerThread.start();
    }

    /**
     * Envia uma mensagem com tag e dados
     */
    public void send(int tag, Protocol.Message data) throws IOException
    {
        connection.send(tag, data);
    }

    /**
     * Recebe uma mensagem com a tag especificada.
     * Bloqueia até chegar uma mensagem com essa tag.
     */
    public Protocol.Message receive(int tag) throws IOException, InterruptedException
    {
        lock.lock();
        try{
            TagQueue tq = tagQueues.get(tag); // Obter ou criar a queue para esta tag
            if (tq == null) {
                tq = new TagQueue(lock.newCondition());
                tagQueues.put(tag, tq);
            }

            while(tq.queue.isEmpty()) // Esperar até haver mensagens na queue, verifica antes e depois de acordar a thread se existe algum erro
            {
                if(readerException != null)
                    throw new IOException("Thread de leitura falhou", readerException);
                if(closed)
                    throw new IOException("Demultiplexer está fechado");

                tq.hasMessages.await();
                if(readerException != null)
                    throw new IOException("Thread de leitura falhou", readerException);
                if(closed)
                    throw new IOException("Demultiplexer está fechado");
            }
            return tq.queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fecha o demultiplexer e a conexão subjacente
     */
    @Override
    public void close() throws IOException, InterruptedException {
        lock.lock();
        try {
            if(closed)
                return;
            closed = true;

            for (TagQueue tq : tagQueues.values()) // Notificar todas as threads à espera
                tq.hasMessages.signalAll();
        } finally {
            lock.unlock();
        }
        connection.close();

        if (readerThread != null)  // Esperar que a thread de leitura termine
        {
            try {
                readerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().join();
            }
        }
    }
}