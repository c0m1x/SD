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
    
    // Conecta ao servidor
    public void connect(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        connection = new TaggedConnection(socket);
        demux = new Demultiplexer(connection);
        connected = true;
        System.out.println("Conectado a " + host + ":" + port);
    }
    
    // Desconecta do servidor (AutoCloseable)
    @Override
    public void close() throws IOException {
        if (connected) {
            try {
                sendRequest(Protocol.LOGOUT);
            } catch (Exception e) {
            }
            demux.close();
            connected = false;
            System.out.println("Desconectado");
        }
    }
    
    // Regista novo utilizador no servidor
    public boolean register(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.REGISTER, username, password);
        
        if (response.type == Protocol.OK) {
            System.out.println(response.args[0]);
            return true;
        } else {
            System.err.println(response.args[0]);
            return false;
        }
    }
    
    // Efetua login no servidor
    public boolean login(String username, String password) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LOGIN, username, password);
        
        if (response.type == Protocol.OK) {
            System.out.println("correct " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }
    
    // Regista um novo evento de compra
    public boolean registarCompra(String produto, int quantidade, float preco) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.ADD_EVENT,
            produto, String.valueOf(quantidade), String.valueOf(preco));
        
        if (response.type == Protocol.OK) {
            System.out.println("Compra registada: " + produto);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }
    
    // Consulta estatísticas de um produto
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
    
    // Consulta agregação de um produto nos últimos N dias
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
    
    // Filtra eventos de múltiplos produtos num dia específico
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
            
            System.out.println("\nEventos Filtrados (Dia " + dia + ")");
            // Formato otimizado: produto|qtd|evento1_qtd:preco,evento2_qtd:preco|produto2|...
            for (String produtoData : response.args[0].split("\\|\\|")) {
                String[] parts = produtoData.split("\\|");
                String produto = parts[0];
                int numEventos = Integer.parseInt(parts[1]);
                System.out.println("  " + produto + " (" + numEventos + " eventos):");
                
                if (parts.length > 2) {
                    for (String evento : parts[2].split(",")) {
                        String[] eventoParts = evento.split(":");
                        System.out.printf("    - Qtd: %s, Preco: %s€%n", eventoParts[0], eventoParts[1]);
                    }
                }
            }
        } else {
            System.err.println("error " + response.args[0]);
        }
    }
    
    // Aguarda até que dois produtos sejam vendidos simultaneamente (no mesmo dia)
    public boolean aguardarVendasSimultaneas(String produto1, String produto2) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_SIMULTANEOUS, produto1, produto2);
        
        if (response.type == Protocol.OK) {
            boolean result = "true".equals(response.args[0]);
            if (result) {
                System.out.println("Vendas simultaneas detectadas: " + produto1 + " e " + produto2);
            } else {
                System.out.println("Dia avancou sem vendas simultaneas de " + produto1 + " e " + produto2);
            }
            return result;
        } else {
            System.err.println("Erro: " + response.args[0]);
            return false;
        }
    }
    
    // Aguarda até N vendas consecutivas de um produto
    public String aguardarVendasConsecutivas(String produto, int n) throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_CONSECUTIVE, produto, String.valueOf(n));
        
        if (response.type == Protocol.OK) {
            String result = response.args[0];
            if (!"null".equals(result)) {
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
    
    // Lista todos os produtos registados
    public void listarProdutos() throws IOException, InterruptedException {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LIST_PRODUCTS);
        
        if (response.type == Protocol.OK) {
            if (response.args.length == 0 || response.args[0].isEmpty()) {
                System.out.println("Nenhum produto registado.");
                return;
            }
            
            String[] produtos = response.args[0].split(",");
            System.out.println("\nProdutos Registados");
            for (String p : produtos) {
                System.out.println("  " + p);
            }
        } else {
            System.err.println(response.args[0]);
        }
    }
    
    // Solicita ao servidor para avançar para o dia seguinte
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

    public boolean isConnected() {
        return connected;
    }
    
    private void checkConnection() throws IOException {
        if (!connected) {
            throw new IOException("Não conectado ao servidor");
        }
    }
    
    // Envia pedido e aguarda resposta (THREAD-SAFE)
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