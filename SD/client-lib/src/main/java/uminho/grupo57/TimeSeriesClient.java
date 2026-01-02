package uminho.grupo57;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cliente API thread-safe para comunicação com servidor de séries temporais.
 * <p>
 * Fornece interface de alto nível para todas operações do sistema,
 * encapsulando complexidade de comunicação via socket, multiplexagem
 * de mensagens e sincronização de threads.
 * </p>
 * 
 * <h2>Características Principais</h2>
 * <ul>
 *   <li><b>Thread-Safe:</b> Suporta múltiplas threads fazendo requests concorrentes</li>
 *   <li><b>Multiplexagem:</b> Usa {@link Demultiplexer} para gerenciar múltiplas operações</li>
 *   <li><b>Auto-Closeable:</b> Integra-se com try-with-resources</li>
 *   <li><b>Async Handlers:</b> Métodos handle* executam operações em threads separadas</li>
 * </ul>
 * 
 * <h2>Arquitetura</h2>
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │      TimeSeriesClient                   │
 * │  ┌───────────────────────────────────┐  │
 * │  │  TaggedConnection (Socket)        │  │
 * │  └──────────┬────────────────────────┘  │
 * │             │                            │
 * │  ┌──────────▼────────────────────────┐  │
 * │  │  Demultiplexer                    │  │
 * │  │  - Reader Thread                  │  │
 * │  │  - Tag Queues (1, 2, 3, ...)      │  │
 * │  └───────────────────────────────────┘  │
 * │                                          │
 * │  Thread Pool (Handlers)                 │
 * │  ┌────────┐ ┌────────┐ ┌────────┐      │
 * │  │Task 1  │ │Task 2  │ │Task N  │      │
 * │  └────────┘ └────────┘ └────────┘      │
 * └─────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>Exemplo de Uso Básico</h2>
 * <pre>{@code
 * // Try-with-resources garante close automático
 * try (TimeSeriesClient client = new TimeSeriesClient()) {
 *     // Conectar ao servidor
 *     client.connect("localhost", 8080);
 *     
 *     // Registar e autenticar
 *     client.register("joao", "senha123");
 *     client.login("joao", "senha123");
 *     
 *     // Registar compras
 *     client.registarCompra("Arroz", 2, 1.50f);
 *     client.registarCompra("Feijão", 1, 2.30f);
 *     
 *     // Consultar estatísticas
 *     client.consultarProduto("Arroz");
 *     
 *     // Listar produtos
 *     client.listarProdutos();
 *     
 * } // close() é chamado automaticamente
 * }</pre>
 * 
 * <h2>Uso Concorrente (Thread-Safe)</h2>
 * <pre>{@code
 * TimeSeriesClient client = new TimeSeriesClient();
 * client.connect("localhost", 8080);
 * client.login("user", "pass");
 * 
 * // Múltiplas threads podem fazer operações concorrentes
 * ExecutorService executor = Executors.newFixedThreadPool(5);
 * 
 * for (int i = 0; i < 100; i++) {
 *     final int id = i;
 *     executor.submit(() -> {
 *         try {
 *             client.registarCompra("Produto" + id, 1, 10.0f);
 *         } catch (Exception e) {
 *             e.printStackTrace();
 *         }
 *     });
 * }
 * 
 * executor.shutdown();
 * executor.awaitTermination(1, TimeUnit.MINUTES);
 * client.close();
 * }</pre>
 * 
 * <h2>Handlers Assíncronos</h2>
 * <p>
 * Métodos {@code handle*} executam operações em threads separadas,
 * úteis para GUIs ou operações não-bloqueantes:
 * </p>
 * <pre>{@code
 * // Registar compra em background
 * client.handleRegistarCompra("Arroz", 2, 1.50f);
 * 
 * // Aguardar notificação em thread separada
 * client.handleAguardarVendasSimultaneas("Arroz", "Feijão");
 * 
 * // Main thread continua executando...
 * }</pre>
 * 
 * @author Grupo 57
 * @version 1.0
 * @since 2025
 * @see Protocol
 * @see TaggedConnection
 * @see Demultiplexer
 */
public class TimeSeriesClient implements AutoCloseable {
    
    /**
     * Conexão tagged com servidor.
     * <p>
     * Gerencia comunicação via socket com sistema de tags para multiplexagem.
     * </p>
     */
    private TaggedConnection connection;
    
    /**
     * Demultiplexer para distribuição de mensagens por tag.
     * <p>
     * Permite que múltiplas threads façam requests concorrentes
     * e recebam suas respostas específicas.
     * </p>
     */
    private Demultiplexer demux;
    
    /**
     * Lock para proteção do gerador de tags.
     * <p>
     * Garante que tags únicas sejam geradas em ambiente multi-threaded.
     * </p>
     */
    private ReentrantLock lock = new ReentrantLock();
    
    /**
     * Gerador sequencial de tags únicas.
     * <p>
     * Incrementado atomicamente (protegido por {@link #lock}) a cada request.
     * </p>
     */
    private int tagGenerator = 0;
    
    /**
     * Flag indicando se cliente está conectado ao servidor.
     */
    private boolean connected = false;

    /**
     * Conecta ao servidor de séries temporais.
     * <p>
     * Estabelece conexão TCP, inicializa tagged connection e demultiplexer,
     * e inicia thread reader para processar respostas.
     * </p>
     * 
     * <h3>Comportamento</h3>
     * <ol>
     *   <li>Cria socket TCP para host:port</li>
     *   <li>Encapsula em {@link TaggedConnection}</li>
     *   <li>Cria e inicia {@link Demultiplexer}</li>
     *   <li>Marca cliente como conectado</li>
     * </ol>
     * 
     * @param host Endereço do servidor (ex: "localhost", "192.168.1.100")
     * @param port Porta do servidor (ex: 8080)
     * @throws IOException Se conexão falhar (servidor offline, rede inacessível, etc)
     * @throws IllegalStateException se já estiver conectado
     * @see #close()
     * @see #isConnected()
     */
    public void connect(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        connection = new TaggedConnection(socket);
        demux = new Demultiplexer(connection);
        demux.start();
        connected = true;
        System.out.println("Conectado a " + host + ":" + port);
    }

    /**
     * Desconecta do servidor e liberta recursos.
     * <p>
     * Envia comando LOGOUT, fecha demultiplexer e marca cliente como desconectado.
     * Este método é chamado automaticamente se usar try-with-resources.
     * </p>
     * 
     * <h3>Comportamento</h3>
     * <ol>
     *   <li>Tenta enviar {@link Protocol#LOGOUT} (ignora erros)</li>
     *   <li>Fecha {@link Demultiplexer} (fecha socket e para reader thread)</li>
     *   <li>Marca cliente como desconectado</li>
     * </ol>
     * 
     * <h3>Idempotência</h3>
     * <p>
     * Pode ser chamado múltiplas vezes sem efeitos colaterais.
     * </p>
     * 
     * @throws IOException Se erro ocorrer ao fechar conexão
     * @throws InterruptedException Se thread for interrompida
     * @see #connect(String, int)
     * @see AutoCloseable#close()
     */
    @Override
    public void close() throws IOException, InterruptedException {
        if(connected) {
            try {
                sendRequest(Protocol.LOGOUT);
            } catch (Exception e) {
                // Ignorar erros ao fazer logout
            }
            demux.close();
            connected = false;
            System.out.println("Desconectado");
        }
    }

    /**
     * Regista novo utilizador no sistema.
     * <p>
     * Cria conta com username e password especificados.
     * Username deve ser único no sistema.
     * </p>
     * 
     * <h3>Validações do Servidor</h3>
     * <ul>
     *   <li>Username não pode ser vazio</li>
     *   <li>Password não pode ser vazia</li>
     *   <li>Username "admin" é reservado</li>
     *   <li>Username deve ser único (não existir previamente)</li>
     * </ul>
     * 
     * @param username Nome de utilizador único
     * @param password Palavra-passe
     * @return {@code true} se registo bem-sucedido, {@code false} caso contrário
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado
     * @see #login(String, String)
     */
    public boolean register(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.REGISTER, username, password);

        if(response.type == Protocol.OK) {
            System.out.println(response.args[0]);
            return true;
        } else {
            System.err.println(response.args[0]);
            return false;
        }
    }

    /**
     * Autentica utilizador no sistema.
     * <p>
     * Valida credenciais e estabelece sessão autenticada.
     * Necessário antes de executar operações sobre séries temporais.
     * </p>
     * 
     * <h3>Conta Administrador</h3>
     * <p>
     * Username: "admin", Password: "1234"<br>
     * Apenas admin pode executar {@link #nextDay()}.
     * </p>
     * 
     * @param username Nome de utilizador
     * @param password Palavra-passe
     * @return {@code true} se autenticação bem-sucedida, {@code false} caso contrário
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado
     * @see #register(String, String)
     */
    public boolean login(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LOGIN, username, password);

        if(response.type == Protocol.OK) {
            System.out.println("correct " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    /**
     * Regista evento de compra no dia corrente.
     * <p>
     * Adiciona evento à série temporal do utilizador autenticado.
     * Evento é associado ao dia atual do servidor.
     * </p>
     * 
     * <h3>Validações</h3>
     * <ul>
     *   <li>Produto não pode ser vazio</li>
     *   <li>Quantidade deve ser positiva ({@code > 0})</li>
     *   <li>Preço deve ser não-negativo ({@code >= 0})</li>
     * </ul>
     * 
     * <h3>Exemplo</h3>
     * <pre>{@code
     * // Compra de 2kg de arroz a 1.50€/kg
     * client.registarCompra("Arroz", 2, 1.50f);
     * 
     * // Total gasto: 2 * 1.50 = 3.00€
     * }</pre>
     * 
     * @param produto Nome do produto (ex: "Arroz", "Feijão")
     * @param quantidade Quantidade comprada (inteiro positivo)
     * @param preco Preço unitário em euros (float não-negativo)
     * @return {@code true} se evento registado com sucesso
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #consultarProduto(String)
     * @see Event
     */
    public boolean registarCompra(String produto, int quantidade, float preco) 
            throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.ADD_EVENT,
                produto, String.valueOf(quantidade), String.valueOf(preco));

        if(response.type == Protocol.OK) {
            System.out.println("Compra registada: " + produto);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    /**
     * Consulta estatísticas agregadas de um produto no dia corrente.
     * <p>
     * Retorna métricas calculadas com base em todos eventos do produto no dia atual.
     * Usa sistema de cache no servidor para otimização ({@link AggregationCache}).
     * </p>
     * 
     * <h3>Estatísticas Retornadas</h3>
     * <ul>
     *   <li><b>Quantidade Total:</b> Soma de todas quantidades compradas</li>
     *   <li><b>Valor Total Gasto:</b> Soma de (quantidade × preço)</li>
     *   <li><b>Preço Máximo:</b> Maior preço unitário registado</li>
     *   <li><b>Preço Mínimo:</b> Menor preço unitário registado</li>
     *   <li><b>Preço Médio:</b> Valor total / Quantidade total</li>
     * </ul>
     * 
     * <h3>Exemplo de Saída</h3>
     * <pre>
     * Estatisticas: Arroz
     * Quantidade total: 5 unidades
     * Valor total gasto: 7.50€
     * Preco maximo: 2.00€
     * Preco minimo: 1.00€
     * Preco medio: 1.50€
     * </pre>
     * 
     * @param produto Nome do produto a consultar
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #consultarAgregacaoRange(String, int)
     * @see AggregationCache
     */
    public void consultarProduto(String produto) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.QUERY_PRODUCT, produto);

        if(response.type == Protocol.OK) {
            if(response.args.length == 1 && "0".equals(response.args[0])) {
                System.out.println("Produto '" + produto + "' não encontrado.");
                return;
            }

            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);

            System.out.println("\nEstatisticas: " + produto);
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preco maximo: %.2f€%n", precoMax);
            System.out.printf("Preco minimo: %.2f€%n", precoMin);
            System.out.printf("Preco medio: %.2f€%n", precoMedio);
        } else {
            System.err.println(response.args[0]);
        }
    }

    /**
     * Consulta estatísticas agregadas de um produto nos últimos N dias.
     * <p>
     * Agrega eventos desde {@code (diaAtual - numeroDias)} até {@code diaAtual}.
     * Útil para análises de tendências e histórico de compras.
     * </p>
     * 
     * <h3>Comportamento</h3>
     * <ul>
     *   <li>Se {@code numeroDias = 0}: apenas dia atual (equivalente a {@link #consultarProduto})</li>
     *   <li>Se {@code numeroDias = 7}: últimos 7 dias incluindo hoje</li>
     *   <li>Dias anteriores a (diaAtual - D) são ignorados (parâmetro maxDays do servidor)</li>
     * </ul>
     * 
     * <h3>Exemplo</h3>
     * <pre>{@code
     * // Estatísticas dos últimos 7 dias
     * client.consultarAgregacaoRange("Arroz", 7);
     * 
     * // Output:
     * // Estatisticas Arroz (ultimos 7 dias)
     * // Quantidade total: 15 unidades
     * // Valor total gasto: 22.50€
     * // ...
     * }</pre>
     * 
     * @param produto Nome do produto a consultar
     * @param numeroDias Número de dias anteriores a incluir (>= 0)
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #consultarProduto(String)
     * @see AggregationCache
     */
    public void consultarAgregacaoRange(String produto, int numeroDias) 
            throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.AGGREGATE_RANGE, 
                produto, String.valueOf(numeroDias));

        if(response.type == Protocol.OK) {
            if(response.args.length == 1 && "0".equals(response.args[0])) {
                System.out.println("Produto '" + produto + "' sem dados nos últimos " + numeroDias + " dias.");
                return;
            }

            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);

            System.out.println("\nEstatisticas " + produto + " (ultimos " + numeroDias + " dias)");
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preco maximo: %.2f€%n", precoMax);
            System.out.printf("Preco minimo: %.2f€%n", precoMin);
            System.out.printf("Preco medio: %.2f€%n", precoMedio);
        } else {
            System.err.println("error " + response.args[0]);
        }
    }

    /**
     * Filtra eventos de produtos específicos num intervalo de dias.
     * <p>
     * Retorna lista detalhada de todos eventos dos produtos especificados
     * desde {@code (diaAtual - dia)} até {@code diaAtual}.
     * </p>
     * 
     * <h3>Formato de Resposta</h3>
     * <pre>
     * Produto1:
     *      5 | 1.50€ | Dia 1
     *      2 | 2.00€ | Dia 3
     * Produto2:
     *      1 | 3.00€ | Dia 2
     * </pre>
     * 
     * <h3>Exemplo de Uso</h3>
     * <pre>{@code
     * // Filtrar eventos de Arroz e Feijão nos últimos 5 dias
     * String[] produtos = {"Arroz", "Feijão"};
     * client.filtrarEventos(5, produtos);
     * }</pre>
     * 
     * @param dia Número de dias anteriores a considerar
     * @param produtos Array de nomes de produtos a filtrar
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #consultarAgregacaoRange(String, int)
     */
    public void filtrarEventos(int dia, String[] produtos) 
            throws IOException, InterruptedException {
        checkConnection();
        String[] args = new String[produtos.length + 1];
        args[0] = String.valueOf(dia);
        System.arraycopy(produtos, 0, args, 1, produtos.length);

        Protocol.Message response = sendRequest(Protocol.FILTER_EVENTS, args);

        if (response.type == Protocol.OK) {
            if(response.args == null || response.args.length == 0 || 
               response.args[0] == null || response.args[0].isEmpty()) {
                System.out.println("Nenhum evento encontrado para os produtos especificados.");
                return;
            }

            System.out.println("\nEventos Filtrados:");
            for(String produtoData : response.args[0].split("\\|\\|")) {
                String[] parts = produtoData.split("\\|");
                if(parts.length < 2)
                    continue;

                String produto = parts[0];
                System.out.println(produto + ":");

                if(parts.length > 2 && !parts[2].isEmpty()) {
                    for (String evento : parts[2].split(",")) {
                        String[] eventoParts = evento.split(":");
                        if(eventoParts.length == 3)
                            System.out.printf("     %s | %s€ | %s%n", 
                                    eventoParts[0], eventoParts[1], eventoParts[2]);
                    }
                }
            }
        } else {
            if(response.args != null && response.args.length > 0)
                System.err.println("error " + response.args[0]);
            else
                System.err.println("error: resposta inválida do servidor");
        }
    }

    /**
     * Aguarda (bloqueante) até dois produtos serem vendidos no mesmo dia.
     * <p>
     * Thread fica bloqueada até:
     * <ul>
     *   <li>Ambos produtos serem vendidos no dia corrente → retorna {@code true}</li>
     *   <li>Dia avançar sem condição satisfeita → retorna {@code false}</li>
     * </ul>
     * </p>
     * 
     * <h3>Caso de Uso</h3>
     * <p>
     * Útil para detectar correlações de compras ou patterns de consumo.
     * Por exemplo, aguardar até que Arroz e Feijão sejam vendidos juntos.
     * </p>
     * 
     * <h3>Exemplo</h3>
     * <pre>{@code
     * // Thread fica bloqueada aqui
     * boolean vendeuJunto = client.aguardarVendasSimultaneas("Arroz", "Feijão");
     * 
     * if (vendeuJunto) {
     *     System.out.println("Arroz e Feijão vendidos no mesmo dia!");
     * } else {
     *     System.out.println("Dia avançou sem venda simultânea.");
     * }
     * }</pre>
     * 
     * @param produto1 Nome do primeiro produto
     * @param produto2 Nome do segundo produto
     * @return {@code true} se ambos vendidos no dia, {@code false} se dia avançou
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #aguardarVendasConsecutivas(String, int)
     * @see NotificationManager#waitSimultaneous(String, String, String)
     */
    public boolean aguardarVendasSimultaneas(String produto1, String produto2) 
            throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_SIMULTANEOUS, produto1, produto2);

        if (response.type == Protocol.OK) {
            boolean result = "true".equals(response.args[0]);
            if(result)
                System.out.println("Vendas simultaneas detectadas: " + produto1 + " e " + produto2);
            else
                System.out.println("Dia avancou sem vendas simultaneas de " + produto1 + " e " + produto2);
            return result;
        } else {
            System.err.println("Erro: " + response.args[0]);
            return false;
        }
    }

    /**
     * Aguarda (bloqueante) até N vendas consecutivas do mesmo produto.
     * <p>
     * Thread fica bloqueada até:
     * <ul>
     *   <li>N vendas consecutivas do produto → retorna nome do produto</li>
     *   <li>Dia avançar sem condição satisfeita → retorna {@code null}</li>
     * </ul>
     * </p>
     * 
     * <h3>Definição de "Consecutivo"</h3>
     * <p>
     * Contador de streak é resetado quando <b>qualquer outro produto</b> é vendido.
     * </p>
     * 
     * <h3>Exemplo</h3>
     * <pre>{@code
     * // Aguardar 3 vendas consecutivas de Arroz
     * String produto = client.aguardarVendasConsecutivas("Arroz", 3);
     * 
     * if (produto != null) {
     *     System.out.println("3 vendas consecutivas de " + produto + "!");
     * } else {
     *     System.out.println("Dia avançou sem streak.");
     * }
     * 
     * // Cenário que satisfaz:
     * // Venda 1: Arroz
     * // Venda 2: Arroz
     * // Venda 3: Arroz  ← Thread acorda aqui
     * 
     * // Cenário que NÃO satisfaz:
     * // Venda 1: Arroz
     * // Venda 2: Arroz
     * // Venda 3: Feijão ← Streak resetado
     * // Venda 4: Arroz  ← Streak = 1 novamente
     * }</pre>
     * 
     * @param produto Nome do produto
     * @param n Número de vendas consecutivas necessárias (> 0)
     * @return Nome do produto se condição satisfeita, {@code null} se dia avançou
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #aguardarVendasSimultaneas(String, String)
     * @see NotificationManager#waitConsecutive(String, String, int)
     */
    public String aguardarVendasConsecutivas(String produto, int n) 
            throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_CONSECUTIVE, 
                produto, String.valueOf(n));

        if(response.type == Protocol.OK) {
            String result = response.args[0];
            if(!"null".equals(result)) {
                System.out.println(n + " vendas consecutivas de " + result + " detectadas!");
                return result;
            } else {
                System.out.println("Dia avancou sem " + n + " vendas consecutivas de " + produto);
                return null;
            }
        } else {
            System.err.println("Erro: " + response.args[0]);
            return null;
        }
    }

    /**
     * Lista todos produtos com eventos registados pelo utilizador.
     * <p>
     * Retorna nomes de produtos únicos que têm pelo menos um evento no dia corrente.
     * </p>
     * 
     * <h3>Exemplo de Saída</h3>
     * <pre>
     * Produtos Registados
     *   Arroz
     *   Feijão
     *   Açúcar
     *   Massa
     * </pre>
     * 
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see #consultarProduto(String)
     */
    public void listarProdutos() throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LIST_PRODUCTS);

        if(response.type == Protocol.OK) {
            if(response.args.length == 0 || response.args[0].isEmpty()) {
                System.out.println("Nenhum produto registado.");
                return;
            }

            String[] produtos = response.args[0].split(",");
            System.out.println("\nProdutos Registados");
            for(String p : produtos)
                System.out.println("  " + p);
        } else {
            System.err.println(response.args[0]);
        }
    }

    /**
     * Avança para o dia seguinte no servidor (apenas administrador).
     * <p>
     * <b>Apenas o utilizador "admin" pode executar este comando.</b>
     * </p>
     * 
     * <h3>Efeitos no Servidor</h3>
     * <ul>
     *   <li>Incrementa contador do dia corrente</li>
     *   <li>Persiste séries do dia anterior para disco</li>
     *   <li>Remove dias anteriores a D (parâmetro maxDays)</li>
     *   <li>Notifica todas threads bloqueadas em wait operations</li>
     *   <li>Reseta streaks de vendas consecutivas</li>
     * </ul>
     * 
     * <h3>Exemplo</h3>
     * <pre>{@code
     * // Fazer login como admin
     * client.login("admin", "1234");
     * 
     * // Avançar dia
     * boolean success = client.nextDay();
     * 
     * if (success) {
     *     System.out.println("Dia avançado com sucesso!");
     * }
     * }</pre>
     * 
     * @return {@code true} se dia avançado com sucesso
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     * @throws IllegalStateException se não estiver conectado ou não autenticado
     * @see TimeSeriesManager#nextDay()
     * @see NotificationManager#onDayAdvance()
     */
    public boolean nextDay() throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.NEXT_DAY);

        if (response.type == Protocol.OK) {
            System.out.println("correct " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    /**
     * Verifica se cliente está conectado ao servidor.
     * 
     * @return {@code true} se conectado, {@code false} caso contrário
     * @see #connect(String, int)
     * @see #close()
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Verifica se cliente está conectado, lança exceção caso contrário.
     * 
     * @throws IOException Se cliente não estiver conectado
     */
    private void checkConnection() throws IOException {
        if (!connected)
            throw new IOException("Não conectado ao servidor");
    }

    /**
     * Envia request ao servidor e aguarda resposta (thread-safe).
     * <p>
     * Gera tag única, envia mensagem via demultiplexer e bloqueia
     * até resposta correspondente chegar.
     * </p>
     * 
     * <h3>Thread-Safety</h3>
     * <p>
     * Múltiplas threads podem chamar este método concorrentemente.
     * Tags únicas garantem que cada thread recebe sua própria resposta.
     * </p>
     * 
     * @param type Tipo de comando (ver {@link Protocol})
     * @param args Argumentos do comando
     * @return Resposta do servidor
     * @throws IOException Se erro de comunicação ocorrer
     * @throws InterruptedException Se thread for interrompida
     */
    private Protocol.Message sendRequest(byte type, String... args) 
            throws IOException, InterruptedException {
        int tag;
        lock.lock();
        try {
            tagGenerator++;
            tag = tagGenerator;
        } finally {
            lock.unlock();
        }

        Protocol.Message mensagem = new Protocol.Message(type, args);
        demux.send(tag, mensagem);
        return demux.receive(tag);
    }

    // ============================================================
    // MÉTODOS ASSÍNCRONOS (Handlers)
    // ============================================================
    
    /**
     * Executa registo de utilizador em thread separada (assíncrono).
     * <p>
     * Útil para GUIs para evitar bloquear interface durante operação.
     * </p>
     * 
     * @param username Nome de utilizador
     * @param password Palavra-passe
     * @see #register(String, String)
     */
    public void handleRegister(String username, String password) {
        new RegisterHandler(username, password).start();
    }

    /**
     * Executa login em thread separada (assíncrono).
     * 
     * @param username Nome de utilizador
     * @param password Palavra-passe
     * @see #login(String, String)
     */
    public void handleLogin(String username, String password) {
        new LoginHandler(username, password).start();
    }

    /**
     * Executa registo de compra em thread separada (assíncrono).
     * 
     * @param produto Nome do produto
     * @param quantidade Quantidade comprada
     * @param preco Preço unitário
     * @see #registarCompra(String, int, float)
     */
    public void handleRegistarCompra(String produto, int quantidade, float preco) {
        new RegistarCompraHandler(produto, quantidade, preco).start();
    }

    /**
     * Executa consulta de produto em thread separada (assíncrono).
     * 
     * @param produto Nome do produto
     * @see #consultarProduto(String)
     */
    public void handleConsultarProduto(String produto) {
        new ConsultarProdutoHandler(produto).start();
    }

    /**
     * Executa consulta de agregação em thread separada (assíncrono).
     * 
     * @param produto Nome do produto
     * @param numeroDias Número de dias
     * @see #consultarAgregacaoRange(String, int)
     */
    public void handleConsultarAgregacaoRange(String produto, int numeroDias) {
        new ConsultarAgregacaoRangeHandler(produto, numeroDias).start();
    }

    /**
     * Executa filtro de eventos em thread separada (assíncrono).
     * 
     * @param dia Número de dias anteriores
     * @param produtos Array de produtos
     * @see #filtrarEventos(int, String[])
     */
    public void handleFiltrarEventos(int dia, String[] produtos) {
        new FiltrarEventosHandler(dia, produtos).start();
    }

    /**
     * Executa espera por vendas simultâneas em thread separada (assíncrono).
     * <p>
     * Não bloqueia thread principal, útil para operações em background.
     * </p>
     * 
     * @param produto1 Primeiro produto
     * @param produto2 Segundo produto
     * @see #aguardarVendasSimultaneas(String, String)
     */
    public void handleAguardarVendasSimultaneas(String produto1, String produto2) {
        new AguardarVendasSimultaneasHandler(produto1, produto2).start();
    }

    /**
     * Executa espera por vendas consecutivas em thread separada (assíncrono).
     * 
     * @param produto Nome do produto
     * @param n Número de vendas consecutivas
     * @see #aguardarVendasConsecutivas(String, int)
     */
    public void handleAguardarVendasConsecutivas(String produto, int n) {
        new AguardarVendasConsecutivasHandler(produto, n).start();
    }

    /**
     * Executa listagem de produtos em thread separada (assíncrono).
     * 
     * @see #listarProdutos()
     */
    public void handleListarProdutos() {
        new ListarProdutosHandler().start();
    }

    /**
     * Executa avanço de dia em thread separada (assíncrono).
     * 
     * @see #nextDay()
     */
    public void handleNextDay() {
        new NextDayHandler().start();
    }

    // ============================================================
    // CLASSES INTERNAS - HANDLERS ASSÍNCRONOS
    // ============================================================
    
    private class RegisterHandler extends Thread {
        private String username, password;
        
        public RegisterHandler(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public void run() {
            try {
                register(username, password);
            } catch (Exception e) {
                System.err.println("Erro no register: " + e.getMessage());
            }
        }
    }

    private class LoginHandler extends Thread {
        private String username, password;
        
        public LoginHandler(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public void run() {
            try {
                login(username, password);
            } catch (Exception e) {
                System.err.println("Erro no login: " + e.getMessage());
            }
        }
    }

    private class RegistarCompraHandler extends Thread {
        private String produto;
        private int quantidade;
        private float preco;
        
        public RegistarCompraHandler(String produto, int quantidade, float preco) {
            this.produto = produto;
            this.quantidade = quantidade;
            this.preco = preco;
        }
        
        public void run() {
            try {
                registarCompra(produto, quantidade, preco);
            } catch (Exception e) {
                System.err.println("Erro ao registar compra: " + e.getMessage());
            }
        }
    }

    private class ConsultarProdutoHandler extends Thread {
        private String produto;
        
        public ConsultarProdutoHandler(String produto) {
            this.produto = produto;
        }
        
        public void run() {
            try {
                consultarProduto(produto);
            } catch (Exception e) {
                System.err.println("Erro ao consultar produto: " + e.getMessage());
            }
        }
    }

    private class ConsultarAgregacaoRangeHandler extends Thread {
        private String produto;
        private int numeroDias;
        
        public ConsultarAgregacaoRangeHandler(String produto, int numeroDias) {
            this.produto = produto;
            this.numeroDias = numeroDias;
        }
        
        public void run() {
            try {
                consultarAgregacaoRange(produto, numeroDias);
            } catch (Exception e) {
                System.err.println("Erro ao consultar agregação: " + e.getMessage());
            }
        }
    }

    private class FiltrarEventosHandler extends Thread {
        private int dia;
        private String[] produtos;
        
        public FiltrarEventosHandler(int dia, String[] produtos) {
            this.dia = dia;
            this.produtos = produtos;
        }
        
        public void run() {
            try {
                filtrarEventos(dia, produtos);
            } catch (Exception e) {
                System.err.println("Erro ao filtrar eventos: " + e.getMessage());
            }
        }
    }

    private class AguardarVendasSimultaneasHandler extends Thread {
        private String produto1, produto2;
        
        public AguardarVendasSimultaneasHandler(String produto1, String produto2) {
            this.produto1 = produto1;
            this.produto2 = produto2;
        }
        
        public void run() {
            try {
                aguardarVendasSimultaneas(produto1, produto2);
            } catch (Exception e) {
                System.err.println("Erro ao aguardar vendas simultâneas: " + e.getMessage());
            }
        }
    }

    private class AguardarVendasConsecutivasHandler extends Thread {
        private String produto;
        private int n;
        
        public AguardarVendasConsecutivasHandler(String produto, int n) {
            this.produto = produto;
            this.n = n;
        }
        
        public void run() {
            try {
                aguardarVendasConsecutivas(produto, n);
            } catch (Exception e) {
                System.err.println("Erro ao aguardar vendas consecutivas: " + e.getMessage());
            }
        }
    }

    private class ListarProdutosHandler extends Thread {
        public void run() {
            try {
                listarProdutos();
            } catch (Exception e) {
                System.err.println("Erro ao listar produtos: " + e.getMessage());
            }
        }
    }

    private class NextDayHandler extends Thread {
        public void run() {
            try {
                nextDay();
            } catch (Exception e) {
                System.err.println("Erro ao avançar dia: " + e.getMessage());
            }
        }
    }
}