package uminho.grupo57;

import java.io.*;
import java.net.Socket;

/**
 * Cliente API para comunicação com servidor de TimeSeries
 * Utiliza protocolo text-based com mensagens pipe-delimited
 */
public class TimeSeriesClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private boolean connected = false;
    
    /**
     * Conecta ao servidor
     * @param host endereço do servidor (ex: "localhost")
     * @param port porta do servidor (ex: 8080)
     * @throws IOException se falhar conexão
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        connected = true;
        System.out.println("✓ Conectado a " + host + ":" + port);
    }
    
    /**
     * Desconecta do servidor
     * @throws IOException se falhar desconexão
     */
    public void disconnect() throws IOException {
        if (connected) {
            protocol.sendMessage(out, new protocol.Message(protocol.LOGOUT));
            in.close();
            out.close();
            socket.close();
            connected = false;
            System.out.println("✓ Desconectado");
        }
    }
    
    /**
     * Regista novo utilizador no servidor
     * @param username nome de utilizador
     * @param password password
     * @return true se registo teve sucesso
     * @throws IOException se falhar comunicação
     */
    public boolean register(String username, String password) throws IOException {
        checkConnection();
        protocol.sendMessage(out, new protocol.Message(protocol.REGISTER, username, password));
        protocol.Message response = protocol.receiveMessage(in);
        
        if (protocol.OK.equals(response.type)) {
            System.out.println("validated " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
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
    public boolean login(String username, String password) throws IOException {
        checkConnection();
        protocol.sendMessage(out, new protocol.Message(protocol.LOGIN, username, password));
        protocol.Message response = protocol.receiveMessage(in);
        
        if (protocol.OK.equals(response.type)) {
            System.out.println("validated " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
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
    public boolean registarCompra(String produto, int quantidade, float preco) throws IOException {
        checkConnection();
        protocol.sendMessage(out, new protocol.Message(protocol.ADD_EVENT, 
            produto, String.valueOf(quantidade), String.valueOf(preco)));
        protocol.Message response = protocol.receiveMessage(in);
        
        if (protocol.OK.equals(response.type)) {
            System.out.println("Compra registada: " + produto);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }
    
    /**
     * Consulta estatísticas de um produto
     * @param produto nome do produto
     * @throws IOException se falhar comunicação
     */
    public void consultarProduto(String produto) throws IOException {
        checkConnection();
        protocol.sendMessage(out, new protocol.Message(protocol.QUERY_PRODUCT, produto));
        protocol.Message response = protocol.receiveMessage(in);
        
        if (protocol.OK.equals(response.type)) {
            if (response.args.length == 1 && "0".equals(response.args[0])) {
                System.out.println("Produto '" + produto + "' não encontrado.");
                return;
            }
            
            int qtdTotal = Integer.parseInt(response.args[0]);
            float precoTotal = Float.parseFloat(response.args[1]);
            float precoMax = Float.parseFloat(response.args[2]);
            float precoMin = Float.parseFloat(response.args[3]);
            float precoMedio = Float.parseFloat(response.args[4]);
            
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
     * Lista todos os produtos registados
     * @throws IOException se falhar comunicação
     */
    public void listarProdutos() throws IOException {
        checkConnection();
        protocol.sendMessage(out, new protocol.Message(protocol.LIST_PRODUCTS));
        protocol.Message response = protocol.receiveMessage(in);
        
        if (protocol.OK.equals(response.type)) {
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
}
