package uminho.grupo57;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Conexão TCP com suporte a multiplexagem através de tags.
 * <p>
 * Esta classe encapsula um {@link Socket} e adiciona um sistema de identificação
 * de mensagens através de <b>tags numéricas</b>, permitindo que múltiplas threads
 * enviem e recebam mensagens concorrentemente sem interferência.
 * </p>
 * 
 * <h2>Características</h2>
 * <ul>
 *   <li><b>Thread-Safe:</b> Usa locks separados para leitura e escrita</li>
 *   <li><b>Multiplexagem:</b> Tags permitem identificar respostas a requests específicos</li>
 *   <li><b>Buffering:</b> Usa BufferedInputStream/BufferedOutputStream para performance</li>
 *   <li><b>Protocolo Binário:</b> Integra-se com {@link Protocol} para comunicação eficiente</li>
 * </ul>
 * 
 * <h2>Formato de Frame</h2>
 * <pre>
 * ┌──────────────────────────────┐
 * │ int (4 bytes): tag           │  ← Identificador único
 * │ Protocol.Message: dados      │  ← Mensagem do protocolo
 * └──────────────────────────────┘
 * </pre>
 * 
 * <h2>Exemplo de Uso</h2>
 * <pre>{@code
 * // Criar conexão
 * Socket socket = new Socket("localhost", 8080);
 * TaggedConnection conn = new TaggedConnection(socket);
 * 
 * // Thread 1: Enviar login
 * Protocol.Message loginMsg = new Protocol.Message(Protocol.LOGIN, "user", "pass");
 * conn.send(1, loginMsg);
 * 
 * // Thread 2: Enviar query (concorrente com Thread 1)
 * Protocol.Message queryMsg = new Protocol.Message(Protocol.QUERY_PRODUCT, "Arroz");
 * conn.send(2, queryMsg);
 * 
 * // Receber respostas (podem chegar em qualquer ordem)
 * Frame frame1 = conn.receive();  // Pode ser tag=1 ou tag=2
 * Frame frame2 = conn.receive();  // A outra resposta
 * 
 * // Fechar conexão
 * conn.close();
 * }</pre>
 * 
 * <h2>Thread-Safety</h2>
 * <p>
 * A classe usa dois locks separados ({@code ls} para escrita, {@code lr} para leitura),
 * permitindo que uma thread escreva enquanto outra lê simultaneamente, maximizando throughput.
 * </p>
 * 
 * <h2>Integração com Demultiplexer</h2>
 * <p>
 * Tipicamente usado em conjunto com {@link Demultiplexer}, que gerencia filas
 * separadas por tag e distribui mensagens recebidas às threads corretas.
 * </p>
 * 
 * @author Grupo 57
 * @version 1.0
 * @since 2025
 * @see Protocol
 * @see Demultiplexer
 * @see Frame
 */
public class TaggedConnection implements AutoCloseable {
    
    /**
     * Lock para sincronização de operações de escrita (send).
     * <p>
     * Garante que apenas uma thread pode escrever no socket de cada vez,
     * evitando entrelaçamento de mensagens.
     * </p>
     */
    private final ReentrantLock ls = new ReentrantLock();
    
    /**
     * Lock para sincronização de operações de leitura (receive).
     * <p>
     * Garante que apenas uma thread pode ler do socket de cada vez,
     * evitando que múltiplas threads leiam partes da mesma mensagem.
     * </p>
     */
    private final ReentrantLock lr = new ReentrantLock();
    
    /**
     * Stream de saída para envio de dados binários.
     * <p>
     * Usa {@link BufferedOutputStream} para reduzir syscalls e melhorar performance.
     * </p>
     */
    private final DataOutputStream output;
    
    /**
     * Stream de entrada para recepção de dados binários.
     * <p>
     * Usa {@link BufferedInputStream} para reduzir syscalls e melhorar performance.
     * </p>
     */
    private final DataInputStream input;

    /**
     * Representa um frame da conexão: combinação de tag + mensagem.
     * <p>
     * Esta estrutura permite identificar a que request cada resposta corresponde,
     * essencial para multiplexagem de mensagens em ambientes multi-threaded.
     * </p>
     * 
     * <h3>Exemplo de Uso</h3>
     * <pre>{@code
     * Frame frame = conn.receive();
     * 
     * switch(frame.tag) {
     *     case 1:
     *         // Processar resposta do login
     *         if(frame.data.type == Protocol.OK) {
     *             System.out.println("Login OK");
     *         }
     *         break;
     *     case 2:
     *         // Processar resposta da query
     *         System.out.println("Resultado: " + frame.data.args[0]);
     *         break;
     * }
     * }</pre>
     * 
     * @see TaggedConnection#send(int, Protocol.Message)
     * @see TaggedConnection#receive()
     */
    public static class Frame {
        
        /**
         * Tag identificadora da mensagem.
         * <p>
         * Número único que associa resposta ao request correspondente.
         * Tipicamente gerado sequencialmente pelo cliente.
         * </p>
         */
        public final int tag;
        
        /**
         * Mensagem do protocolo.
         * <p>
         * Contém tipo de comando/resposta e argumentos associados.
         * </p>
         * 
         * @see Protocol.Message
         */
        public final Protocol.Message data;
        
        /**
         * Constrói novo frame com tag e mensagem.
         * 
         * @param tag Identificador único do frame
         * @param data Mensagem do protocolo
         * @throws NullPointerException se data for null
         */
        public Frame(int tag, Protocol.Message data) {
            this.tag = tag;
            this.data = data;
        }
    }

    /**
     * Cria nova conexão tagged sobre socket existente.
     * <p>
     * Inicializa streams de I/O com buffering para performance otimizada.
     * </p>
     * 
     * @param socket Socket TCP já conectado
     * @throws IOException Se erro ao criar streams de I/O
     * @throws NullPointerException se socket for null
     */
    public TaggedConnection(Socket socket) throws IOException {
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    /**
     * Envia frame (tag + mensagem) para o outro lado da conexão.
     * <p>
     * <b>Thread-Safe:</b> Usa lock de escrita para garantir atomicidade do envio.
     * Múltiplas threads podem chamar este método concorrentemente sem interferência.
     * </p>
     * 
     * <h3>Comportamento</h3>
     * <ol>
     *   <li>Adquire lock de escrita ({@code ls})</li>
     *   <li>Escreve tag (4 bytes, int)</li>
     *   <li>Escreve mensagem (formato {@link Protocol})</li>
     *   <li>Faz flush do buffer</li>
     *   <li>Liberta lock</li>
     * </ol>
     * 
     * @param tag Identificador único da mensagem
     * @param data Mensagem a enviar
     * @throws IOException Se erro de I/O ocorrer durante envio
     * @throws NullPointerException se data for null
     * @see #receive()
     * @see #send(Frame)
     */
    public void send(int tag, Protocol.Message data) throws IOException {
        ls.lock();
        try {
            output.writeInt(tag);
            Protocol.sendMessage(output, data);
            output.flush();
        } finally {
            ls.unlock();
        }
    }

    /**
     * Envia frame completo (atalho para {@link #send(int, Protocol.Message)}).
     * 
     * @param frame Frame a enviar (contém tag e data)
     * @throws IOException Se erro de I/O ocorrer durante envio
     * @throws NullPointerException se frame ou frame.data for null
     * @see #send(int, Protocol.Message)
     */
    public void send(Frame frame) throws IOException {
        send(frame.tag, frame.data);
    }

    /**
     * Recebe frame (tag + mensagem) do outro lado da conexão.
     * <p>
     * <b>Operação Bloqueante:</b> Bloqueia thread corrente até frame completo
     * estar disponível no socket.
     * </p>
     * <p>
     * <b>Thread-Safe:</b> Usa lock de leitura para garantir que apenas uma
     * thread lê do socket de cada vez.
     * </p>
     * 
     * <h3>Comportamento</h3>
     * <ol>
     *   <li>Adquire lock de leitura ({@code lr})</li>
     *   <li>Lê tag (4 bytes, int) - <b>BLOQUEANTE</b></li>
     *   <li>Lê mensagem (formato {@link Protocol}) - <b>BLOQUEANTE</b></li>
     *   <li>Cria e retorna {@link Frame}</li>
     *   <li>Liberta lock</li>
     * </ol>
     * 
     * @return Frame recebido (nunca null)
     * @throws IOException Se erro de I/O ocorrer durante leitura
     * @throws EOFException Se conexão for terminada pelo outro lado
     * @see #send(int, Protocol.Message)
     */
    public Frame receive() throws IOException {
        lr.lock();
        try {
            int tag = input.readInt();
            Protocol.Message mensagem = Protocol.receiveMessage(input);
            return new Frame(tag, mensagem);
        } finally {
            lr.unlock();
        }
    }

    /**
     * Fecha a conexão e liberta recursos.
     * <p>
     * Fecha ambos os streams de I/O. Após chamada, qualquer tentativa
     * de enviar ou receber resultará em {@link IOException}.
     * </p>
     * 
     * <h3>Nota</h3>
     * <p>
     * Este método <b>não</b> fecha o socket subjacente. O caller deve
     * fechar o socket separadamente se necessário.
     * </p>
     * 
     * @throws IOException Se erro ocorrer ao fechar streams
     * @see AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        output.close();
        input.close();
    }
}