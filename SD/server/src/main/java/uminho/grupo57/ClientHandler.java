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
            
            Protocol.Message msg;
            while ((msg = Protocol.receiveMessage(in)) != null) {
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
    
    private void handleMessage(Protocol.Message msg, DataOutputStream out) throws IOException {
        switch (msg.type) {
            case Protocol.REGISTER:
                handleRegister(msg, out);
                break;
            case Protocol.LOGIN:
                handleLogin(msg, out);
                break;
            case Protocol.ADD_EVENT:
                handleAddEvent(msg, out);
                break;
            case Protocol.QUERY_PRODUCT:
                handleQueryProduct(msg, out);
                break;
            case Protocol.LIST_PRODUCTS:
                handleListProducts(msg, out);
                break;
            case Protocol.NEXT_DAY:
                handleNextDay(msg, out);
                break;
            case Protocol.LOGOUT:
                handleLogout(msg, out);
                break;
            case Protocol.AGGREGATE_RANGE:
                handleAggregateRange(msg, out);
                break;
            case Protocol.FILTER_EVENTS:
                handleFilterEvents(msg, out);
                break;
            case Protocol.WAIT_SIMULTANEOUS:
                handleWaitSimultaneous(msg, out);
                break;
            case Protocol.WAIT_CONSECUTIVE:
                handleWaitConsecutive(msg, out);
                break;
            default:
                Protocol.sendMessage(out, Protocol.error("Comando desconhecido"));
        }
    }
    
    private void handleRegister(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: REGISTER username password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (username.isEmpty() || password.isEmpty()) {
            Protocol.sendMessage(out, Protocol.error("Username e password não podem estar vazios"));
            return;
        }
        
        if (authManager.register(username, password)) {
            Protocol.sendMessage(out, Protocol.ok("Utilizador registado"));
            System.out.println("Novo utilizador: " + username);
        } else {
            Protocol.sendMessage(out, Protocol.error("Utilizador já existe"));
        }
    }
    
    private void handleLogin(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: LOGIN username password"));
            return;
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (authManager.authenticate(username, password)) {
            currentUser = username;
            Protocol.sendMessage(out, Protocol.ok("Login bem-sucedido"));
            System.out.println("Login: " + username);
        } else {
            Protocol.sendMessage(out, Protocol.error("Credenciais inválidas"));
        }
    }
    
    private void handleAddEvent(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 3) {
            Protocol.sendMessage(out, Protocol.error("Uso: ADD_EVENT produto quantidade preco"));
            return;
        }
        
        try {
            String produto = msg.args[0];
            int quantidade = Integer.parseInt(msg.args[1]);
            float preco = Float.parseFloat(msg.args[2]);
            
            if (quantidade <= 0 || preco < 0) {
                Protocol.sendMessage(out, Protocol.error("Quantidade deve ser positiva e preço não-negativo"));
                return;
            }
            
            Event event = new Event(produto, quantidade, preco);
            tsManager.addEvent(currentUser, event);
            
            Protocol.sendMessage(out, Protocol.ok("Evento registado"));
        } catch (NumberFormatException e) {
            Protocol.sendMessage(out, Protocol.error("Quantidade/preço inválidos"));
        }
    }
    
    private void handleQueryProduct(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 1) {
            Protocol.sendMessage(out, Protocol.error("Uso: QUERY_PRODUCT produto"));
            return;
        }
        
        String produto = msg.args[0];
        java.util.Map<String, Object> stats = tsManager.getStats(currentUser, produto);
        
        if (stats.isEmpty()) {
            Protocol.sendMessage(out, Protocol.ok("0"));
        } else {
            String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
                stats.get("quantidade_total"),
                stats.get("preco_total"),
                stats.get("preco_max"),
                stats.get("preco_min"),
                stats.get("preco_medio")
            );
            Protocol.sendMessage(out, Protocol.ok(response));
        }
    }
    
    private void handleListProducts(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        java.util.Set<String> produtos = tsManager.getAllProdutos(currentUser);
        
        if (produtos.isEmpty()) {
            Protocol.sendMessage(out, Protocol.ok(""));
        } else {
            String response = String.join(",", produtos);
            Protocol.sendMessage(out, Protocol.ok(response));
        }
    }
    
    private void handleNextDay(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        tsManager.nextDay();
        int currentDay = tsManager.getCurrentDay();
        Protocol.sendMessage(out, Protocol.ok("Dia atual: " + currentDay));
        System.out.println(currentUser + " avançou para dia " + currentDay);
    }
    
    private void handleLogout(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser != null) {
            System.out.println("Logout: " + currentUser);
            currentUser = null;
        }
        Protocol.sendMessage(out, Protocol.ok("Logout efetuado"));
    }
    
    // Placeholder para funcionalidades futuras
    private void handleAggregateRange(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: AGGREGATE_RANGE produto numeroDias"));
            return;
        }
        
        try {
            String produto = msg.args[0];
            int numeroDias = Integer.parseInt(msg.args[1]);
            
            if (numeroDias <= 0 || numeroDias > tsManager.getD()) {
                Protocol.sendMessage(out, Protocol.error("Número de dias inválido (1 a " + tsManager.getD() + ")"));
                return;
            }
            
            java.util.Map<String, Object> stats = tsManager.getStatsUltimosDias(currentUser, produto, numeroDias);
            
            if (stats.isEmpty()) {
                Protocol.sendMessage(out, Protocol.ok("0"));
            } else {
                String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
                    stats.get("quantidade_total"),
                    stats.get("preco_total"),
                    stats.get("preco_max"),
                    stats.get("preco_min"),
                    stats.get("preco_medio")
                );
                Protocol.sendMessage(out, Protocol.ok(response));
            }
        } catch (NumberFormatException e) {
            Protocol.sendMessage(out, Protocol.error("Número de dias inválido"));
        }
    }
    
    private void handleFilterEvents(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: FILTER_EVENTS dia produto1 [produto2 ...]"));
            return;
        }
        
        try {
            int dia = Integer.parseInt(msg.args[0]);
            
            if (dia < 1 || dia > tsManager.getD()) {
                Protocol.sendMessage(out, Protocol.error("Dia inválido (1 a " + tsManager.getD() + ")"));
                return;
            }
            
            String[] produtos = new String[msg.args.length - 1];
            System.arraycopy(msg.args, 1, produtos, 0, msg.args.length - 1);
            
            java.util.Map<String, java.util.List<Event>> eventosFiltrados = 
                tsManager.getEventosFiltrados(currentUser, dia, produtos);
            
            if (eventosFiltrados.isEmpty()) {
                Protocol.sendMessage(out, Protocol.ok(""));
                return;
            }
            
            // Serialização eficiente: produto|numEventos|qtd:preco,qtd:preco||produto2|...
            StringBuilder response = new StringBuilder();
            boolean first = true;
            
            for (java.util.Map.Entry<String, java.util.List<Event>> entry : eventosFiltrados.entrySet()) {
                if (!first) response.append("||");
                first = false;
                
                String produto = entry.getKey();
                java.util.List<Event> eventos = entry.getValue();
                
                response.append(produto).append("|").append(eventos.size()).append("|");
                
                boolean firstEvento = true;
                for (Event e : eventos) {
                    if (!firstEvento) response.append(",");
                    firstEvento = false;
                    response.append(e.getQuantidade()).append(":").append(String.format("%.2f", e.getPreco()));
                }
            }
            
            Protocol.sendMessage(out, Protocol.ok(response.toString()));
            
        } catch (NumberFormatException e) {
            Protocol.sendMessage(out, Protocol.error("Dia inválido"));
        }
    }
    
    private void handleWaitSimultaneous(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: WAIT_SIMULTANEOUS produto1 produto2"));
            return;
        }
        
        String produto1 = msg.args[0];
        String produto2 = msg.args[1];
        
        try {
            boolean satisfied = tsManager.getNotificationManager()
                .waitSimultaneous(currentUser, produto1, produto2);
            
            if (satisfied) {
                Protocol.sendMessage(out, Protocol.ok("true"));
            } else {
                Protocol.sendMessage(out, Protocol.ok("false"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Protocol.sendMessage(out, Protocol.error("Interrompido"));
        }
    }
    
    private void handleWaitConsecutive(Protocol.Message msg, DataOutputStream out) throws IOException {
        if (currentUser == null) {
            Protocol.sendMessage(out, Protocol.unauthorized("Login necessário"));
            return;
        }
        
        if (msg.args.length < 2) {
            Protocol.sendMessage(out, Protocol.error("Uso: WAIT_CONSECUTIVE produto n"));
            return;
        }
        
        try {
            String produto = msg.args[0];
            int n = Integer.parseInt(msg.args[1]);
            
            if (n <= 0) {
                Protocol.sendMessage(out, Protocol.error("Número de vendas deve ser positivo"));
                return;
            }
            
            boolean satisfied = tsManager.getNotificationManager()
                .waitConsecutive(currentUser, produto, n);
            
            if (satisfied) {
                Protocol.sendMessage(out, Protocol.ok(produto));
            } else {
                Protocol.sendMessage(out, Protocol.ok("null"));
            }
        } catch (NumberFormatException e) {
            Protocol.sendMessage(out, Protocol.error("Número de vendas inválido"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Protocol.sendMessage(out, Protocol.error("Interrompido"));
        }
    }
}
