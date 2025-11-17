package uminho.grupo57;

import java.io.*;
import java.net.Socket;

/**
 * Handler que trata um cliente em thread separada
 * Implementa protocolo de comunicação request-response
 */
public class ClientHandler implements Runnable {
    
    private final Socket socket;
    private final autenticathionManager authManager;
    private final timeSeriesManager tsManager;
    private String currentUser = null;
    
    public ClientHandler(Socket socket, autenticathionManager authManager, timeSeriesManager tsManager) {
        this.socket = socket;
        this.authManager = authManager;
        this.tsManager = tsManager;
    }
    
    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente conectado");
            
            protocol.Message msg;
            while ((msg = protocol.receiveMessage(in)) != null) {
                handleMessage(msg, out);
            }
            
        } catch (IOException e) {
            System.err.println("[" + socket.getRemoteSocketAddress() + "] Erro: " + e.getMessage());
        } finally {
            try {
                socket.close();
                System.out.println("[" + socket.getRemoteSocketAddress() + "] Desconectado");
            } catch (IOException e) {
                // ignore
            }
        }
    }
    
    private void handleMessage(protocol.Message msg, BufferedWriter out) throws IOException {
        switch (msg.type) {
            case protocol.REGISTER:
                handleRegister(msg, out);
                break;
            case protocol.LOGIN:
                handleLogin(msg, out);
                break;
            case protocol.ADD_EVENT:
                handleAddEvent(msg, out);
                break;
            case protocol.QUERY_PRODUCT:
                handleQueryProduct(msg, out);
                break;
            case protocol.LIST_PRODUCTS:
                handleListProducts(msg, out);
                break;
            case protocol.LOGOUT:
                handleLogout(msg, out);
                break;
            default:
                protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Comando desconhecido"));
        }
    }
    
    private void handleRegister(protocol.Message msg, BufferedWriter out) throws IOException {
        if (msg.args.length < 2) {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Uso: REGISTER|username|password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (authManager.register(username, password)) {
            protocol.sendMessage(out, new protocol.Message(protocol.OK, "Utilizador registado"));
            System.out.println("✓ Novo utilizador: " + username);
        } else {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Utilizador já existe"));
        }
    }
    
    private void handleLogin(protocol.Message msg, BufferedWriter out) throws IOException {
        if (msg.args.length < 2) {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Uso: LOGIN|username|password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (authManager.authenticate(username, password)) {
            currentUser = username;
            protocol.sendMessage(out, new protocol.Message(protocol.OK, "Login bem-sucedido"));
            System.out.println("✓ Login: " + username);
        } else {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Credenciais inválidas"));
        }
    }
    
    private void handleAddEvent(protocol.Message msg, BufferedWriter out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, new protocol.Message(protocol.UNAUTHORIZED, "Login necessário"));
            return;
        }
        
        if (msg.args.length < 3) {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Uso: ADD_EVENT|produto|quantidade|preco"));
            return;
        }
        
        try {
            String produto = msg.args[0];
            int quantidade = Integer.parseInt(msg.args[1]);
            float preco = Float.parseFloat(msg.args[2]);
            
            Event event = new Event(produto, quantidade, preco);
            tsManager.addEvent(currentUser, event);
            
            protocol.sendMessage(out, new protocol.Message(protocol.OK, "Evento registado"));
        } catch (NumberFormatException e) {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Quantidade/preço inválidos"));
        }
    }
    
    private void handleQueryProduct(protocol.Message msg, BufferedWriter out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, new protocol.Message(protocol.UNAUTHORIZED, "Login necessário"));
            return;
        }
        
        if (msg.args.length < 1) {
            protocol.sendMessage(out, new protocol.Message(protocol.ERROR, "Uso: QUERY_PRODUCT|produto"));
            return;
        }
        
        String produto = msg.args[0];
        java.util.Map<String, Object> stats = tsManager.getStats(currentUser, produto);
        
        if (stats.isEmpty()) {
            protocol.sendMessage(out, new protocol.Message(protocol.OK, "0"));
        } else {
            String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
                stats.get("quantidade_total"),
                stats.get("preco_total"),
                stats.get("preco_max"),
                stats.get("preco_min"),
                stats.get("preco_medio")
            );
            protocol.sendMessage(out, new protocol.Message(protocol.OK, response));
        }
    }
    
    private void handleListProducts(protocol.Message msg, BufferedWriter out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, new protocol.Message(protocol.UNAUTHORIZED, "Login necessário"));
            return;
        }
        
        java.util.Set<String> produtos = tsManager.getAllProdutos(currentUser);
        
        if (produtos.isEmpty()) {
            protocol.sendMessage(out, new protocol.Message(protocol.OK, ""));
        } else {
            String response = String.join(",", produtos);
            protocol.sendMessage(out, new protocol.Message(protocol.OK, response));
        }
    }
    
    private void handleLogout(protocol.Message msg, BufferedWriter out) throws IOException {
        if (currentUser != null) {
            System.out.println("✓ Logout: " + currentUser);
            currentUser = null;
        }
        protocol.sendMessage(out, new protocol.Message(protocol.OK, "Logout efetuado"));
    }
}
