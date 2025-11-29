package uminho.grupo57;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cliente API para comunicação com servidor de TimeSeries
 * THREAD-SAFE: Suporta múltiplas threads fazendo pedidos concorrentes
 * Utiliza TaggedConnection e Demultiplexer para demultiplexagem de respostas
 */
public class TimeSeriesClient implements AutoCloseable {
    private TaggedConnection connection;
    private Demultiplexer demux;
    private final AtomicInteger tagGenerator = new AtomicInteger(0);
    private boolean connected = false;
    
    /**
     * Conecta ao servidor
     * @param host endereço do servidor (ex: "localhost")
     * @param port porta do servidor (ex: 8080)
     * @throws IOException se falhar conexão
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
     * Desconecta do servidor (AutoCloseable)
     * @throws IOException se falhar desconexão
     */
    @Override
    public void close() throws IOException {
        if (connected) {
            try {
                // Enviar LOGOUT antes de fechar
                sendRequest(Protocol.LOGOUT);
            } catch (Exception e) {
                // Ignora erro ao enviar logout
            }
            demux.close();
            connected = false;
            System.out.println("Desconectado");
        }
    }
    
    /**
     * Regista novo utilizador no servidor
     * @param username nome de utilizador
     * @param password password
     * @return true se registo teve sucesso
     * @throws IOException se falhar comunicação
     */
    public boolean register(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.REGISTER, username, password);
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ " + response.args[0]);
            return true;
        } else {
            System.err.println("✗ " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Efetua login no servidor
     * @param username nome de utilizador
     * @param password password
     * @return true se login teve sucesso
     * @throws IOException se falhar comunicação
     */
    public boolean login(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LOGIN, username, password);
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ " + response.args[0]);
            return true;
        } else {
            System.err.println("✗ " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Regista um novo evento de compra
     * @param produto nome do produto
     * @param quantidade quantidade comprada
     * @param preco preço unitário
     * @return true se registo teve sucesso
     * @throws IOException se falhar comunicação
     */
    public boolean registarCompra(String produto, int quantidade, float preco) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.ADD_EVENT,
            produto, String.valueOf(quantidade), String.valueOf(preco));
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ Compra registada: " + produto);
            return true;
        } else {
            System.err.println("✗ " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Consulta estatísticas de um produto
     * @param produto nome do produto
     * @throws IOException se falhar comunicação
     */
    public void consultarProduto(String produto) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.QUERY_PRODUCT, produto);
        
        if (response.type == Protocol.OK) {
            if (response.args.length == 1 && "0".equals(response.args[0])) {
                System.out.println("Produto '" + produto + "' não encontrado.");
                return;
            }
            
            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);
            
            System.out.println("\n═══ Estatísticas: " + produto + " ═══");
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preço máximo: %.2f€%n", precoMax);
            System.out.printf("Preço mínimo: %.2f€%n", precoMin);
            System.out.printf("Preço médio: %.2f€%n", precoMedio);
        } else {
            System.err.println("✗ " + response.args[0]);
        }
    }
    
    /**
     * Consulta agregação de um produto nos últimos N dias
     * @param produto nome do produto
     * @param numeroDias quantos dias consultar
     * @throws IOException se falhar comunicação
     */
    public void consultarAgregacaoRange(String produto, int numeroDias) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.AGGREGATE_RANGE, produto, String.valueOf(numeroDias));
        
        if (response.type == Protocol.OK) {
            if (response.args.length == 1 && "0".equals(response.args[0])) {
                System.out.println("Produto '" + produto + "' sem dados nos últimos " + numeroDias + " dias.");
                return;
            }
            
            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);
            
            System.out.println("\n═══ Estatísticas " + produto + " (últimos " + numeroDias + " dias) ═══");
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preço máximo: %.2f€%n", precoMax);
            System.out.printf("Preço mínimo: %.2f€%n", precoMin);
            System.out.printf("Preço médio: %.2f€%n", precoMedio);
        } else {
            System.err.println("✗ " + response.args[0]);
        }
    }
    
    /**
     * Filtra eventos de múltiplos produtos num dia específico
     * @param dia dia a consultar (0 = dia corrente)
     * @param produtos array de nomes de produtos
     * @throws IOException se falhar comunicação
     */
    public void filtrarEventos(int dia, String[] produtos) throws IOException, InterruptedException {
        checkConnection();
        String[] args = new String[produtos.length + 1];
        args[0] = String.valueOf(dia);
        System.arraycopy(produtos, 0, args, 1, produtos.length);
        
        Protocol.Message response = sendRequest(Protocol.FILTER_EVENTS, args);
        
        if (response.type == Protocol.OK) {
            if (response.args.length == 0 || response.args[0].isEmpty()) {
                System.out.println("Nenhum evento encontrado para os produtos especificados no dia " + dia + ".");
                return;
            }
            
            System.out.println("\n═══ Eventos Filtrados (Dia " + dia + ") ═══");
            // Formato otimizado: produto|qtd|evento1_qtd:preco,evento2_qtd:preco|produto2|...
            for (String produtoData : response.args[0].split("\\|\\|")) {
                String[] parts = produtoData.split("\\|");
                String produto = parts[0];
                int numEventos = Integer.parseInt(parts[1]);
                System.out.println("  • " + produto + " (" + numEventos + " eventos):");
                
                if (parts.length > 2) {
                    for (String evento : parts[2].split(",")) {
                        String[] eventoParts = evento.split(":");
                        System.out.printf("    - Qtd: %s, Preço: %s€%n", eventoParts[0], eventoParts[1]);
                    }
                }
            }
        } else {
            System.err.println("✗ " + response.args[0]);
        }
    }
    
    /**
     * Aguarda até que dois produtos sejam vendidos simultaneamente (no mesmo dia)
     * Bloqueia até condição satisfeita OU dia avançar
     * @param produto1 primeiro produto
     * @param produto2 segundo produto
     * @return true se condição foi satisfeita, false se dia avançou sem satisfazer
     * @throws IOException se falhar comunicação
     */
    public boolean aguardarVendasSimultaneas(String produto1, String produto2) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_SIMULTANEOUS, produto1, produto2);
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ Vendas simultâneas detectadas: " + produto1 + " e " + produto2);
            return true;
        } else if (response.type == Protocol.ERROR) {
            System.out.println("✗ Dia avançou sem vendas simultâneas de " + produto1 + " e " + produto2);
            return false;
        } else {
            System.err.println("✗ Erro: " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Aguarda até N vendas consecutivas de um produto
     * Bloqueia até condição satisfeita OU dia avançar
     * @param produto nome do produto
     * @param n número de vendas consecutivas
     * @return true se condição foi satisfeita, false se dia avançou sem satisfazer
     * @throws IOException se falhar comunicação
     */
    public boolean aguardarVendasConsecutivas(String produto, int n) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_CONSECUTIVE, produto, String.valueOf(n));
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ " + n + " vendas consecutivas de " + produto + " detectadas!");
            return true;
        } else if (response.type == Protocol.ERROR) {
            System.out.println("✗ Dia avançou sem " + n + " vendas consecutivas de " + produto);
            return false;
        } else {
            System.err.println("✗ Erro: " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Lista todos os produtos registados
     * @throws IOException se falhar comunicação
     */
    public void listarProdutos() throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LIST_PRODUCTS);
        
        if (response.type == Protocol.OK) {
            if (response.args.length == 0 || response.args[0].isEmpty()) {
                System.out.println("Nenhum produto registado.");
                return;
            }
            
            String[] produtos = response.args[0].split(",");
            System.out.println("\n═══ Produtos Registados ═══");
            for (String p : produtos) {
                System.out.println("  • " + p);
            }
        } else {
            System.err.println("✗ " + response.args[0]);
        }
    }
    
    /**
     * Solicita ao servidor para avançar para o dia seguinte
     */
    public boolean nextDay() throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.NEXT_DAY);
        
        if (response.type == Protocol.OK) {
            System.out.println("✓ " + response.args[0]);
            return true;
        } else {
            System.err.println("✗ " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Verifica se está conectado
     * @return true se conectado
     */
    public boolean isConnected() {
        return connected;
    }
    
    private void checkConnection() throws IOException {
        if (!connected) {
            throw new IOException("Não conectado ao servidor");
        }
    }
    
    /**
     * Envia pedido e aguarda resposta (THREAD-SAFE)
     * Cada thread obtém um tag único para demultiplexagem
     */
    private Protocol.Message sendRequest(byte type, String... args) throws IOException, InterruptedException {
        int tag = tagGenerator.getAndIncrement();
        
        // Serializar mensagem
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        Protocol.sendMessage(dos, new Protocol.Message(type, args));
        dos.flush();
        
        // Enviar com tag
        demux.send(tag, baos.toByteArray());
        
        // Aguardar resposta com mesmo tag
        byte[] responseData = demux.receive(tag);
        
        // Deserializar resposta
        ByteArrayInputStream bais = new ByteArrayInputStream(responseData);
        DataInputStream dis = new DataInputStream(bais);
        return Protocol.receiveMessage(dis);
    }
}