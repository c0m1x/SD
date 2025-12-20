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
        final Deque<byte[]> queue = new ArrayDeque<>();
        final Condition hasMessages;

        TagQueue(Condition condition) {
            this.hasMessages = condition;
        }
    }

    private final TaggedConnection connection;
    private final Lock lock = new ReentrantLock();
    private final Map<Integer, TagQueue> tagQueues = new HashMap<>();

    private Thread readerThread;
    private volatile boolean closed = false;
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
                        // Obter ou criar a fila para esta tag
                        TagQueue tq = tagQueues.get(frame.tag);
                        if (tq == null) {
                            tq = new TagQueue(lock.newCondition());
                            tagQueues.put(frame.tag, tq);
                        }

                        // Adicionar mensagem à fila
                        tq.queue.addLast(frame.data);

                        // Notificar threads que esperam por esta tag
                        tq.hasMessages.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                // Guardar exceção para propagar às threads que esperam
                lock.lock();
                try {
                    if (!closed) {
                        readerException = e;
                        // Notificar todas as threads à espera
                        for (TagQueue tq : tagQueues.values()) {
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
    public void send(int tag, byte[] data) throws IOException
    {
        connection.send(tag, data);
    }

    /**
     * Recebe uma mensagem com a tag especificada.
     * Bloqueia até chegar uma mensagem com essa tag.
     */
    public byte[] receive(int tag) throws IOException, InterruptedException
    {
        lock.lock();
        try {
            // Obter ou criar a fila para esta tag
            TagQueue tq = tagQueues.get(tag);
            if (tq == null) {
                tq = new TagQueue(lock.newCondition());
                tagQueues.put(tag, tq);
            }

            // Esperar até haver mensagens na fila
            while (tq.queue.isEmpty()) {
                // Verificar se houve erro na thread de leitura
                if (readerException != null) {
                    throw new IOException("Reader thread failed", readerException);
                }
                if (closed) {
                    throw new IOException("Demultiplexer is closed");
                }

                tq.hasMessages.await();

                // Revalidar após acordar
                if (readerException != null) {
                    throw new IOException("Reader thread failed", readerException);
                }
                if (closed) {
                    throw new IOException("Demultiplexer is closed");
                }
            }

            // Remover e devolver a primeira mensagem da fila
            return tq.queue.removeFirst();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Fecha o demultiplexer e a conexão subjacente
     */
    @Override
    public void close() throws IOException
    {
        lock.lock();
        try {
            if (closed)
                return;
            closed = true;

            // Notificar todas as threads à espera
            for (TagQueue tq : tagQueues.values()) {
                tq.hasMessages.signalAll();
            }
        } finally {
            lock.unlock();
        }

        // Fechar a conexão (isto fará a thread de leitura terminar)
        connection.close();

        // Esperar que a thread de leitura termine
        if (readerThread != null) {
            try {
                readerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
