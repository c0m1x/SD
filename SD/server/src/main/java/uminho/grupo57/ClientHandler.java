package uminho.grupo57;

import java.io.*;
import java.net.Socket;

/**
 * Handler que trata um cliente em thread separada
 * Implementa protocolo binário de comunicação request-response
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
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
        ) {
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente conectado");
            
            protocol.Message msg;
            while ((msg = protocol.receiveMessage(in)) != null) {
                handleMessage(msg, out);
            }
            
        } catch (EOFException e) {
            // Cliente desconectou
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente desconectou");
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
    
    private void handleMessage(protocol.Message msg, DataOutputStream out) throws IOException {
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
            case protocol.NEXT_DAY: 
                handleNextDay(msg, out);
                break;
            case protocol.LOGOUT:
                handleLogout(msg, out);
                break;
            case protocol.AGGREGATE_RANGE:
                handleAggregateRange(msg, out);
                break;
            case protocol.FILTER_EVENTS:
                handleFilterEvents(msg, out);
                break;
            case protocol.WAIT_SIMULTANEOUS:
                handleWaitSimultaneous(msg, out);
                break;
            case protocol.WAIT_CONSECUTIVE:
                handleWaitConsecutive(msg, out);
                break;
            default:
                protocol.sendMessage(out, protocol.error("Comando desconhecido"));
        }
    }
    
    private void handleRegister(protocol.Message msg, DataOutputStream out) throws IOException {
        if (msg.args.length < 2) {
            protocol.sendMessage(out, protocol.error("Uso: REGISTER username password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (username.isEmpty() || password.isEmpty()) {
            protocol.sendMessage(out, protocol.error("Username e password não podem estar vazios"));
            return;
        }
        
        if (authManager.register(username, password)) {
            protocol.sendMessage(out, protocol.ok("Utilizador registado"));
            System.out.println("Novo utilizador: " + username);
        } else {
            protocol.sendMessage(out, protocol.error("Utilizador já existe"));
        }
    }
    
    private void handleLogin(protocol.Message msg, DataOutputStream out) throws IOException {
        if (msg.args.length < 2) {
            protocol.sendMessage(out, protocol.error("Uso: LOGIN username password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (authManager.authenticate(username, password)) {
            currentUser = username;
            protocol.sendMessage(out, protocol.ok("Login bem-sucedido"));
            System.out.println("Login: " + username);
        } else {
            protocol.sendMessage(out, protocol.error("Credenciais inválidas"));
        }
    }
    
    private void handleAddEvent(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 3) {
            protocol.sendMessage(out, protocol.error("Uso: ADD_EVENT produto quantidade preco"));
            return;
        }
        
        try {
            String produto = msg.args[0];
            int quantidade = Integer.parseInt(msg.args[1]);
            float preco = Float.parseFloat(msg.args[2]);
            
            if (quantidade <= 0 || preco < 0) {
                protocol.sendMessage(out, protocol.error("Quantidade deve ser positiva e preço não-negativo"));
                return;
            }
            
            Event event = new Event(produto, quantidade, preco);
            tsManager.addEvent(currentUser, event);
            
            protocol.sendMessage(out, protocol.ok("Evento registado"));
        } catch (NumberFormatException e) {
            protocol.sendMessage(out, protocol.error("Quantidade/preço inválidos"));
        }
    }
    
    private void handleQueryProduct(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 1) {
            protocol.sendMessage(out, protocol.error("Uso: QUERY_PRODUCT produto"));
            return;
        }
        
        String produto = msg.args[0];
        java.util.Map<String, Object> stats = tsManager.getStats(currentUser, produto);
        
        if (stats.isEmpty()) {
            protocol.sendMessage(out, protocol.ok("0"));
        } else {
            String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
                stats.get("quantidade_total"),
                stats.get("preco_total"),
                stats.get("preco_max"),
                stats.get("preco_min"),
                stats.get("preco_medio")
            );
            protocol.sendMessage(out, protocol.ok(response));
        }
    }
    
    private void handleListProducts(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        
        java.util.Set<String> produtos = tsManager.getAllProdutos(currentUser);
        
        if (produtos.isEmpty()) {
            protocol.sendMessage(out, protocol.ok(""));
        } else {
            String response = String.join(",", produtos);
            protocol.sendMessage(out, protocol.ok(response));
        }
    }
    
    private void handleNextDay(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        
        tsManager.nextDay();
        int currentDay = tsManager.getCurrentDay();
        protocol.sendMessage(out, protocol.ok("Dia atual: " + currentDay));
        System.out.println(currentUser + " avançou para dia " + currentDay);
    }
    
    private void handleLogout(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser != null) {
            System.out.println("Logout: " + currentUser);
            currentUser = null;
        }
        protocol.sendMessage(out, protocol.ok("Logout efetuado"));
    }
    
    // Placeholder para funcionalidades futuras
    private void handleAggregateRange(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        protocol.sendMessage(out, protocol.error("Funcionalidade não implementada"));
    }
    
    private void handleFilterEvents(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        protocol.sendMessage(out, protocol.error("Funcionalidade não implementada"));
    }
    
    private void handleWaitSimultaneous(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        protocol.sendMessage(out, protocol.error("Funcionalidade não implementada"));
    }
    
    private void handleWaitConsecutive(protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            protocol.sendMessage(out, protocol.unauthorized("Login necessário"));
            return;
        }
        protocol.sendMessage(out, protocol.error("Funcionalidade não implementada"));
    }
}