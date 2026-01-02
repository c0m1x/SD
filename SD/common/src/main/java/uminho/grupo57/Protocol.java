package uminho.grupo57;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Protocolo binário de comunicação cliente-servidor para o sistema de séries temporais.
 * <p>
 * Define o formato de mensagens trocadas entre cliente e servidor através de sockets TCP.
 * Utiliza serialização binária com {@link DataInputStream} e {@link DataOutputStream} 
 * para eficiência e compactação de dados.
 * </p>
 * 
 * <h2>Formato de Mensagem</h2>
 * <pre>
 * ┌─────────────────────────────────────┐
 * │ byte:  tipo de comando              │
 * │ int:   número de argumentos (N)     │
 * │ ┌───────────────────────────────┐   │
 * │ │ Para cada argumento (1..N):   │   │
 * │ │   int:   tamanho (UTF-8)      │   │
 * │ │   bytes: dados                │   │
 * │ └───────────────────────────────┘   │
 * └─────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Exemplo de Uso</h2>
 * <pre>{@code
 * // Criar mensagem de login
 * Protocol.Message loginMsg = new Protocol.Message(
 *     Protocol.LOGIN, 
 *     "username", 
 *     "password"
 * );
 * 
 * // Serializar para socket
 * DataOutputStream out = new DataOutputStream(socket.getOutputStream());
 * Protocol.sendMessage(out, loginMsg);
 * 
 * // Receber resposta
 * DataInputStream in = new DataInputStream(socket.getInputStream());
 * Protocol.Message response = Protocol.receiveMessage(in);
 * 
 * if (response.type == Protocol.OK) {
 *     System.out.println("Login bem-sucedido!");
 * }
 * }</pre>
 * 
 * @author Grupo 57
 * @version 1.0
 * @since 2025
 * @see TaggedConnection
 * @see Demultiplexer
 */
public class Protocol {

    // ============================================================
    // TIPOS DE MENSAGEM - COMANDOS DO CLIENTE
    // ============================================================
    
    /**
     * Comando para registar novo utilizador no sistema.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>username (String) - Nome de utilizador único</li>
     *   <li>password (String) - Palavra-passe</li>
     * </ol>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Utilizador registado com sucesso</li>
     *   <li>{@link #ERROR} - Username já existe ou inválido</li>
     * </ul>
     * </p>
     * 
     * @see #LOGIN
     */
    public static final byte REGISTER = 1;
    
    /**
     * Comando para autenticar utilizador existente.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>username (String) - Nome de utilizador</li>
     *   <li>password (String) - Palavra-passe</li>
     * </ol>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Autenticação bem-sucedida</li>
     *   <li>{@link #ERROR} - Credenciais inválidas</li>
     * </ul>
     * </p>
     * 
     * @see #REGISTER
     * @see #LOGOUT
     */
    public static final byte LOGIN = 2;
    
    /**
     * Comando para adicionar evento de compra ao dia corrente.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>produto (String) - Nome do produto</li>
     *   <li>quantidade (String) - Quantidade comprada (inteiro positivo)</li>
     *   <li>preco (String) - Preço unitário (float não-negativo)</li>
     * </ol>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Evento registado com sucesso</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     *   <li>{@link #ERROR} - Argumentos inválidos</li>
     * </ul>
     * </p>
     * <p>
     * <b>Nota:</b> Requer autenticação prévia via {@link #LOGIN}.
     * </p>
     * 
     * @see Event
     */
    public static final byte ADD_EVENT = 3;
    
    /**
     * Comando para consultar estatísticas agregadas de um produto no dia corrente.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>produto (String) - Nome do produto a consultar</li>
     * </ol>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Estatísticas retornadas no formato:
     *       {@code "qtdTotal|volumeTotal|precoMax|precoMin|precoMedio"}</li>
     *   <li>{@link #OK} - {@code "0"} se produto não encontrado</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * 
     * @see #AGGREGATE_RANGE
     * @see AggregationCache
     */
    public static final byte QUERY_PRODUCT = 4;
    
    /**
     * Comando para listar todos os produtos com eventos registados.
     * <p>
     * <b>Argumentos esperados:</b> Nenhum
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Lista de produtos separados por vírgula: {@code "Arroz,Feijão,Açúcar"}</li>
     *   <li>{@link #OK} - String vazia se não existem produtos</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     */
    public static final byte LIST_PRODUCTS = 5;
    
    /**
     * Comando para avançar para o dia seguinte (apenas administrador).
     * <p>
     * <b>Argumentos esperados:</b> Nenhum
     * </p>
     * <p>
     * <b>Comportamento:</b>
     * <ul>
     *   <li>Incrementa contador do dia corrente</li>
     *   <li>Persiste dados do dia anterior para disco</li>
     *   <li>Remove dias anteriores a D (parâmetro maxDays)</li>
     *   <li>Notifica threads bloqueadas em {@link #WAIT_SIMULTANEOUS} e {@link #WAIT_CONSECUTIVE}</li>
     * </ul>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Dia avançado com sucesso: {@code "Dia atual: N"}</li>
     *   <li>{@link #ERROR} - Utilizador não é administrador</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * <p>
     * <b>Nota:</b> Apenas o utilizador "admin" pode executar este comando.
     * </p>
     * 
     * @see TimeSeriesManager#nextDay()
     * @see NotificationManager#onDayAdvance()
     */
    public static final byte NEXT_DAY = 6;
    
    /**
     * Comando para terminar sessão do utilizador.
     * <p>
     * <b>Argumentos esperados:</b> Nenhum
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Logout efetuado</li>
     * </ul>
     * </p>
     * 
     * @see #LOGIN
     */
    public static final byte LOGOUT = 7;
    
    /**
     * Comando para agregar estatísticas de um produto nos últimos N dias.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>produto (String) - Nome do produto</li>
     *   <li>numeroDias (String) - Número de dias anteriores (inteiro)</li>
     * </ol>
     * </p>
     * <p>
     * <b>Comportamento:</b> Agrega eventos do dia atual até {@code (diaAtual - numeroDias)}.
     * Usa sistema de cache para otimização ({@link AggregationCache}).
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Estatísticas no formato: {@code "qtd|volume|max|min|medio"}</li>
     *   <li>{@link #OK} - {@code "0"} se não há dados no intervalo</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * 
     * @see #QUERY_PRODUCT
     * @see AggregationCache
     */
    public static final byte AGGREGATE_RANGE = 8;
    
    /**
     * Comando para filtrar eventos de produtos específicos num dia anterior.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>dia (String) - Número de dias anteriores ao dia atual</li>
     *   <li>produto1 (String) - Nome do primeiro produto</li>
     *   <li>produto2..produtoN (String) - Nomes adicionais de produtos</li>
     * </ol>
     * </p>
     * <p>
     * <b>Formato de resposta:</b>
     * <pre>
     * produto1|count|qtd1:preco1:Dia X,qtd2:preco2:Dia Y||produto2|count|...
     * </pre>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Lista de eventos no formato especificado</li>
     *   <li>{@link #OK} - String vazia se não há eventos</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * <p>
     * <b>Nota:</b> Este comando pode retornar grandes quantidades de dados.
     * A serialização deve ser eficiente para evitar overhead de rede.
     * </p>
     * 
     * @see SerializationEfficiencyTest
     */
    public static final byte FILTER_EVENTS = 9;
    
    /**
     * Comando bloqueante que aguarda até dois produtos serem vendidos no mesmo dia.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>produto1 (String) - Nome do primeiro produto</li>
     *   <li>produto2 (String) - Nome do segundo produto</li>
     * </ol>
     * </p>
     * <p>
     * <b>Comportamento:</b>
     * <ul>
     *   <li>Thread fica bloqueada até condição satisfeita OU dia avançar</li>
     *   <li>Se ambos produtos forem vendidos no dia corrente: retorna {@code true}</li>
     *   <li>Se dia avançar sem condição satisfeita: retorna {@code false}</li>
     * </ul>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - {@code "true"} se condição satisfeita</li>
     *   <li>{@link #OK} - {@code "false"} se dia avançou</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * 
     * @see NotificationManager#waitSimultaneous(String, String, String)
     * @see #WAIT_CONSECUTIVE
     */
    public static final byte WAIT_SIMULTANEOUS = 10;
    
    /**
     * Comando bloqueante que aguarda N vendas consecutivas do mesmo produto.
     * <p>
     * <b>Argumentos esperados:</b>
     * <ol>
     *   <li>produto (String) - Nome do produto</li>
     *   <li>n (String) - Número de vendas consecutivas (inteiro positivo)</li>
     * </ol>
     * </p>
     * <p>
     * <b>Comportamento:</b>
     * <ul>
     *   <li>Thread fica bloqueada até N vendas consecutivas OU dia avançar</li>
     *   <li>Contador de streak é resetado quando outro produto é vendido</li>
     *   <li>Se condição satisfeita: retorna nome do produto</li>
     *   <li>Se dia avançar: retorna {@code "null"}</li>
     * </ul>
     * </p>
     * <p>
     * <b>Respostas possíveis:</b>
     * <ul>
     *   <li>{@link #OK} - Nome do produto se condição satisfeita</li>
     *   <li>{@link #OK} - {@code "null"} se dia avançou</li>
     *   <li>{@link #UNAUTHORIZED} - Utilizador não autenticado</li>
     * </ul>
     * </p>
     * 
     * @see NotificationManager#waitConsecutive(String, String, int)
     * @see #WAIT_SIMULTANEOUS
     */
    public static final byte WAIT_CONSECUTIVE = 11;

    // ============================================================
    // TIPOS DE MENSAGEM - RESPOSTAS DO SERVIDOR
    // ============================================================
    
    /**
     * Resposta de sucesso do servidor.
     * <p>
     * Indica que operação foi executada com sucesso. 
     * Pode conter argumentos adicionais com dados da resposta.
     * </p>
     */
    public static final byte OK = 100;
    
    /**
     * Resposta de erro genérico do servidor.
     * <p>
     * Indica que operação falhou. O primeiro argumento contém mensagem descritiva do erro.
     * </p>
     * <p>
     * <b>Exemplos de erros:</b>
     * <ul>
     *   <li>"Utilizador já existe"</li>
     *   <li>"Credenciais inválidas"</li>
     *   <li>"Quantidade deve ser positiva"</li>
     *   <li>"Comando desconhecido"</li>
     * </ul>
     * </p>
     */
    public static final byte ERROR = 101;
    
    /**
     * Resposta indicando que utilizador não está autenticado.
     * <p>
     * Retornado quando operação requer autenticação prévia via {@link #LOGIN}.
     * </p>
     */
    public static final byte UNAUTHORIZED = 102;

    // ============================================================
    // CLASSE INTERNA - MESSAGE
    // ============================================================
    
    /**
     * Representa uma mensagem do protocolo binário.
     * <p>
     * Classe imutável que encapsula tipo de comando e argumentos.
     * Fornece métodos para serialização/deserialização binária.
     * </p>
     * 
     * <h3>Exemplo de Uso</h3>
     * <pre>{@code
     * // Criar mensagem
     * Message msg = new Message(Protocol.ADD_EVENT, "Arroz", "2", "1.50");
     * 
     * // Serializar
     * DataOutputStream out = ...;
     * msg.writeTo(out);
     * 
     * // Deserializar
     * DataInputStream in = ...;
     * Message received = Message.readFrom(in);
     * 
     * // Aceder dados
     * byte tipo = received.type;
     * String produto = received.args[0];
     * }</pre>
     */
    public static class Message {
        
        /**
         * Tipo de comando ou resposta.
         * <p>
         * Deve ser uma das constantes definidas em {@link Protocol}:
         * {@link #REGISTER}, {@link #LOGIN}, {@link #OK}, etc.
         * </p>
         */
        public final byte type;
        
        /**
         * Argumentos da mensagem.
         * <p>
         * Array de strings com dados adicionais. Pode ser vazio mas nunca null.
         * </p>
         */
        public final String[] args;

        /**
         * Constrói nova mensagem do protocolo.
         * 
         * @param type Tipo de comando/resposta (ver constantes em {@link Protocol})
         * @param args Argumentos da mensagem (pode ser varargs vazio)
         * @throws NullPointerException se args for null (internamente substituído por array vazio)
         */
        public Message(byte type, String... args) {
            this.type = type;
            this.args = args != null ? args : new String[0];
        }

        /**
         * Serializa mensagem para stream de saída binário.
         * <p>
         * <b>Formato escrito:</b>
         * <ol>
         *   <li>1 byte: tipo de comando</li>
         *   <li>4 bytes (int): número de argumentos</li>
         *   <li>Para cada argumento:
         *     <ul>
         *       <li>Se null: 4 bytes (int) com valor 0</li>
         *       <li>Se não-null: UTF-8 string (tamanho + bytes)</li>
         *     </ul>
         *   </li>
         * </ol>
         * </p>
         * 
         * @param out Stream de saída para escrever dados
         * @throws IOException Se erro de I/O ocorrer durante escrita
         * @see #readFrom(DataInputStream)
         */
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeByte(type);
            out.writeInt(args.length);

            for(String arg : args) {
                if(arg == null)
                    out.writeInt(0);
                else
                    out.writeUTF(arg);
            }
            out.flush();
        }

        /**
         * Deserializa mensagem de stream de entrada binário.
         * <p>
         * Lê formato escrito por {@link #writeTo(DataOutputStream)}.
         * Operação <b>bloqueante</b> até mensagem completa estar disponível.
         * </p>
         * 
         * @param in Stream de entrada para ler dados
         * @return Nova instância de {@link Message} com dados lidos
         * @throws IOException Se erro de I/O ocorrer ou formato inválido
         * @throws EOFException Se stream terminar prematuramente
         * @see #writeTo(DataOutputStream)
         */
        public static Message readFrom(DataInputStream in) throws IOException {
            byte type = in.readByte();
            int numArgs = in.readInt();

            String[] args = new String[numArgs];
            for (int i = 0; i < numArgs; i++) {
                args[i] = in.readUTF();
            }

            return new Message(type, args);
        }

        /**
         * Retorna representação textual da mensagem para debug.
         * 
         * @return String no formato "Message{type=X, args=[arg1, arg2, ...]}"
         */
        @Override
        public String toString() {
            return "Message{type=" + type + ", args=" + java.util.Arrays.toString(args) + "}";
        }
    }

    // ============================================================
    // MÉTODOS UTILITÁRIOS ESTÁTICOS
    // ============================================================
    
    /**
     * Envia mensagem binária através de stream de saída.
     * <p>
     * Atalho para {@link Message#writeTo(DataOutputStream)}.
     * </p>
     * 
     * @param out Stream de saída
     * @param msg Mensagem a enviar
     * @throws IOException Se erro de I/O ocorrer
     * @see #receiveMessage(DataInputStream)
     */
    public static void sendMessage(DataOutputStream out, Message msg) throws IOException {
        msg.writeTo(out);
    }

    /**
     * Recebe mensagem binária de stream de entrada (bloqueante).
     * <p>
     * Atalho para {@link Message#readFrom(DataInputStream)}.
     * </p>
     * 
     * @param in Stream de entrada
     * @return Mensagem recebida
     * @throws IOException Se erro de I/O ocorrer
     * @throws EOFException Se conexão terminar inesperadamente
     * @see #sendMessage(DataOutputStream, Message)
     */
    public static Message receiveMessage(DataInputStream in) throws IOException {
        return Message.readFrom(in);
    }

    /**
     * Cria mensagem de resposta de sucesso (OK).
     * 
     * @param args Argumentos opcionais da resposta
     * @return Nova mensagem do tipo {@link #OK}
     * @see #error(String)
     */
    public static Message ok(String... args) {
        return new Message(OK, args);
    }

    /**
     * Cria mensagem de erro com descrição.
     * 
     * @param message Descrição do erro
     * @return Nova mensagem do tipo {@link #ERROR}
     * @see #ok(String...)
     */
    public static Message error(String message) {
        return new Message(ERROR, message);
    }

    /**
     * Cria mensagem de não-autorizado com descrição.
     * 
     * @param message Descrição (ex: "Login necessário")
     * @return Nova mensagem do tipo {@link #UNAUTHORIZED}
     * @see #ok(String...)
     */
    public static Message unauthorized(String message) {
        return new Message(UNAUTHORIZED, message);
    }
}