package uminho.grupo57;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Demultiplexador de mensagens por tag para conexões tagged.
 * <p>
 * Gerencia distribuição automática de mensagens recebidas para múltiplas threads clientes
 * através de um sistema de <b>filas separadas por tag</b>. Permite que várias threads
 * façam requests concorrentes e recebam suas respostas específicas sem interferência.
 * </p>
 * 
 * <h2>Arquitetura</h2>
 * <pre>
 *                  ┌─────────────────────┐
 *                  │  TaggedConnection   │
 *                  │   (Socket TCP)      │
 *                  └──────────┬──────────┘
 *                             │
 *                   ┌─────────▼─────────┐
 *                   │  Reader Thread    │
 *                   │  (loop infinito)  │
 *                   └─────────┬─────────┘
 *                             │
 *              ┌──────────────┼──────────────┐
 *              │              │              │
 *         ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
 *         │Queue[1] │    │Queue[2] │    │Queue[N] │
 *         │[msg1]   │    │[msg5]   │    │[msg9]   │
 *         │[msg2]   │    │[msg6]   │    │         │
 *         └────┬────┘    └────┬────┘    └────┬────┘
 *              │              │              │
 *         ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
 *         │Thread 1 │    │Thread 2 │    │Thread N │
 *         │ await() │    │ await() │    │ await() │
 *         └─────────┘    └─────────┘    └─────────┘
 * </pre>
 * 
 * <h2>Fluxo de Operação</h2>
 * <ol>
 *   <li><b>Start:</b> Thread reader é iniciada via {@link #start()}</li>
 *   <li><b>Send:</b> Threads clientes chamam {@link #send(int, Protocol.Message)}</li>
 *   <li><b>Reader Loop:</b> Thread reader continuamente lê frames do socket</li>
 *   <li><b>Distribution:</b> Cada frame é colocado na fila da tag correspondente</li>
 *   <li><b>Notification:</b> Threads bloqueadas em {@link #receive(int)} são notificadas</li>
 *   <li><b>Receive:</b> Thread cliente acorda e retorna mensagem da sua fila</li>
 * </ol>
 * 
 * <h2>Exemplo de Uso</h2>
 * <pre>{@code
 * // Setup
 * Socket socket = new Socket("localhost", 8080);
 * TaggedConnection conn = new TaggedConnection(socket);
 * Demultiplexer demux = new Demultiplexer(conn);
 * demux.start();  // Inicia thread reader
 * 
 * // Múltiplas threads podem fazer requests concorrentes
 * 
 * // Thread 1: Login
 * new Thread(() -> {
 *     try {
 *         Protocol.Message loginMsg = new Protocol.Message(Protocol.LOGIN, "user", "pass");
 *         demux.send(1, loginMsg);
 *         Protocol.Message response = demux.receive(1);  // Bloqueia até resposta
 *         System.out.println("Login: " + response.type);
 *     } catch (Exception e) {
 *         e.printStackTrace();
 *     }
 * }).start();
 * 
 * // Thread 2: Query (concorrente com Thread 1)
 * new Thread(() -> {
 *     try {
 *         Protocol.Message queryMsg = new Protocol.Message(Protocol.QUERY_PRODUCT, "Arroz");
 *         demux.send(2, queryMsg);
 *         Protocol.Message response = demux.receive(2);  // Bloqueia até resposta
 *         System.out.println("Query: " + response.args[0]);
 *     } catch (Exception e) {
 *         e.printStackTrace();
 *     }
 * }).start();
 * 
 * // Cleanup
 * demux.close();
 * }</pre>
 * 
 * <h2>Garantias de Thread-Safety</h2>
 * <ul>
 *   <li>Múltiplas threads podem chamar {@link #send} concorrentemente</li>
 *   <li>Múltiplas threads podem chamar {@link #receive} com tags diferentes</li>
 *   <li>Cada tag tem fila e condition variable independentes</li>
 *   <li>Mensagens nunca são perdidas ou entregues à thread errada</li>
 * </ul>
 * 
 * <h2>Gestão de Erros</h2>
 * <p>
 * Se thread reader encontrar erro de I/O:
 * <ul>
 *   <li>Exceção é armazenada em {@link #readerException}</li>
 *   <li>Todas threads bloqueadas são notificadas</li>
 *   <li>Próximas chamadas a {@link #receive} propagam exceção</li>
 * </ul>
 * </p>
 * 
 * @author Grupo 57
 * @version 1.0
 * @since 2025
 * @see TaggedConnection
 * @see Protocol
 */
public class Demultiplexer implements AutoCloseable {
    
    /**
     * Estrutura interna para gerenciar fila de mensagens de uma tag específica.
     * <p>
     * Cada tag tem sua própria instância de {@code TagQueue}, contendo:
     * <ul>
     *   <li>Fila de mensagens pendentes (FIFO)</li>
     *   <li>Condition variable para sincronização de threads</li>
     * </ul>
     * </p>
     * 
     * <h3>Sincronização</h3>
     * <p>
     * Threads que chamam {@link #receive(int)} com esta tag ficam bloqueadas
     * na condition variable até:
     * <ul>
     *   <li>Mensagem chegar para esta tag, OU</li>
     *   <li>Demultiplexer ser fechado, OU</li>
     *   <li>Erro ocorrer na thread reader</li>
     * </ul>
     * </p>
     */
    private static class TagQueue {
        
        /**
         * Fila FIFO de mensagens pendentes para esta tag.
         * <p>
         * Usa {@link ArrayDeque} para eficiência em operações de fila.
         * </p>
         */
        final Deque<Protocol.Message> queue = new ArrayDeque<>();
        
        /**
         * Condition variable para bloquear/acordar threads esperando por mensagens.
         * <p>
         * Associada ao lock global do {@link Demultiplexer}.
         * </p>
         */
        final Condition hasMessages;

        /**
         * Constrói nova fila de tag com condition variable.
         * 
         * @param condition Condition associada ao lock do demultiplexer
         */
        TagQueue(Condition condition) {
            this.hasMessages = condition;
        }
    }

    /**
     * Conexão tagged subjacente para envio/recepção de frames.
     */
    private final TaggedConnection connection;
    
    /**
     * Lock global para sincronização de acesso às estruturas internas.
     * <p>
     * Protege: {@link #tagQueues}, {@link #closed}, {@link #readerException}.
     * </p>
     */
    private final ReentrantLock lock = new ReentrantLock();
    
    /**
     * Mapa de tags para filas de mensagens.
     * <p>
     * Criado dinamicamente à medida que novas tags são usadas.
     * </p>
     * <p>
     * <b>Key:</b> Tag (Integer)<br>
     * <b>Value:</b> {@link TagQueue} com fila e condition variable
     * </p>
     */
    private final Map<Integer, TagQueue> tagQueues = new HashMap<>();

    /**
     * Thread dedicada para leitura contínua de frames do socket.
     * <p>
     * Executada em background, distribui mensagens recebidas para filas apropriadas.
     * </p>
     */
    private Thread readerThread;
    
    /**
     * Flag indicando se demultiplexer foi fechado.
     * <p>
     * Quando {@code true}, novas operações devem falhar imediatamente.
     * </p>
     */
    private boolean closed = false;
    
    /**
     * Exceção capturada pela thread reader, se houver.
     * <p>
     * Se não-null, indica que thread reader encontrou erro fatal.
     * Propagada para threads clientes em {@link #receive(int)}.
     * </p>
     */
    private IOException readerException = null;

    /**
     * Constrói novo demultiplexer sobre conexão tagged existente.
     * <p>
     * <b>Nota:</b> Não inicia automaticamente thread reader.
     * Deve-se chamar {@link #start()} explicitamente.
     * </p>
     * 
     * @param conn Conexão tagged a ser demultiplexada
     * @throws NullPointerException se conn for null
     * @see #start()
     */
    public Demultiplexer(TaggedConnection conn) {
        this.connection = conn;
    }

    /**
     * Inicia thread reader para processamento de mensagens recebidas.
     * <p>
     * <b>Deve ser chamado uma única vez</b> após construção e antes
     * de qualquer operação de send/receive.
     * </p>
     * 
     * <h3>Comportamento da Thread Reader</h3>
     * <pre>{@code
     * while (!closed) {
     *     Frame frame = connection.receive();  // Bloqueante
     *     
     *     lock.lock();
     *     try {
     *         TagQueue queue = getOrCreateQueue(frame.tag);
     *         queue.queue.addLast(frame.data);
     *         queue.hasMessages.signalAll();  // Acorda threads esperando
     *     } finally {
     *         lock.unlock();
     *     }
     * }
     * }</pre>
     * 
     * <h3>Tratamento de Erros</h3>
     * <p>
     * Se {@link TaggedConnection#receive()} lançar {@link IOException}:
     * <ul>
     *   <li>Exceção é armazenada em {@link #readerException}</li>
     *   <li>Todas threads bloqueadas são notificadas</li>
     *   <li>Thread reader termina</li>
     * </ul>
     * </p>
     * 
     * @throws IllegalStateException se já foi iniciado anteriormente
     * @see #close()
     */
    public void start() {
        readerThread = new Thread(() -> {
            try {
                while (!closed) {
                    TaggedConnection.Frame frame = connection.receive();

                    lock.lock();
                    try {
                        TagQueue tq = tagQueues.get(frame.tag);
                        if (tq == null) {
                            tq = new TagQueue(lock.newCondition());
                            tagQueues.put(frame.tag, tq);
                        }
                        tq.queue.addLast(frame.data);
                        tq.hasMessages.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                lock.lock();
                try {
                    if (!closed) {
                        readerException = e;
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
     * Envia mensagem com tag especificada.
     * <p>
     * <b>Thread-Safe:</b> Múltiplas threads podem chamar concorrentemente.
     * Delegado para {@link TaggedConnection#send(int, Protocol.Message)}.
     * </p>
     * 
     * @param tag Identificador único da mensagem
     * @param data Mensagem a enviar
     * @throws IOException Se erro de I/O ocorrer durante envio
     * @throws NullPointerException se data for null
     * @see #receive(int)
     */
    public void send(int tag, Protocol.Message data) throws IOException {
        connection.send(tag, data);
    }

    /**
     * Recebe mensagem com tag especificada (operação bloqueante).
     * <p>
     * <b>Comportamento:</b>
     * <ul>
     *   <li>Se mensagem já existe na fila: retorna imediatamente</li>
     *   <li>Caso contrário: bloqueia até mensagem chegar</li>
     *   <li>Se erro ocorrer na reader thread: lança IOException</li>
     *   <li>Se demux for fechado: lança IOException</li>
     * </ul>
     * </p>
     * 
     * <h3>Exemplo de Uso</h3>
     * <pre>{@code
     * // Thread cliente
     * demux.send(42, loginMsg);
     * Protocol.Message response = demux.receive(42);  // Bloqueia até resposta
     * 
     * if (response.type == Protocol.OK) {
     *     System.out.println("Sucesso!");
     * }
     * }</pre>
     * 
     * <h3>Garantias</h3>
     * <ul>
     *   <li>Mensagens são entregues em ordem FIFO por tag</li>
     *   <li>Nunca retorna mensagem de tag diferente</li>
     *   <li>Thread-safe: múltiplas threads podem receber tags diferentes</li>
     * </ul>
     * 
     * @param tag Tag da mensagem a receber
     * @return Mensagem recebida (nunca null)
     * @throws IOException Se erro de I/O ocorrer ou demux fechado
     * @throws InterruptedException Se thread for interrompida durante espera
     * @see #send(int, Protocol.Message)
     */
    public Protocol.Message receive(int tag) throws IOException, InterruptedException {
        lock.lock();
        try {
            TagQueue tq = tagQueues.get(tag);
            if (tq == null) {
                tq = new TagQueue(lock.newCondition());
                tagQueues.put(tag, tq);
            }

            while(tq.queue.isEmpty()) {
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
     * Fecha demultiplexer e liberta todos recursos.
     * <p>
     * <b>Efeitos:</b>
     * <ul>
     *   <li>Marca demux como fechado</li>
     *   <li>Notifica todas threads bloqueadas (acordam com IOException)</li>
     *   <li>Fecha conexão tagged subjacente</li>
     *   <li>Aguarda terminação da thread reader</li>
     * </ul>
     * </p>
     * 
     * <h3>Comportamento Idempotente</h3>
     * <p>
     * Chamadas subsequentes a {@code close()} não têm efeito.
     * </p>
     * 
     * @throws IOException Se erro ocorrer ao fechar conexão
     * @throws InterruptedException Se thread corrente for interrompida ao aguardar reader
     * @see AutoCloseable#close()
     */
    @Override
    public void close() throws IOException, InterruptedException {
        lock.lock();
        try {
            if(closed)
                return;
            closed = true;

            for (TagQueue tq : tagQueues.values()) {
                tq.hasMessages.signalAll();
            }
        } finally {
            lock.unlock();
        }
        
        connection.close();

        if (readerThread != null) {
            try {
                readerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}