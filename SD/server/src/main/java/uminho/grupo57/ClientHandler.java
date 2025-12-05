package uminho.grupo57;

import java.io.*;
import java.net.Socket;

/**
 * Handler que trata um cliente em thread separada
 * Implementa protocolo binário de comunicação request-response COM TAGS
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
        TaggedConnection connection = null;
        try {
            connection = new TaggedConnection(socket);
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente conectado");
            
            while (true) {
                // Receber frame do cliente
                TaggedConnection.Frame frame = connection.receive();
                
                // Deserializar mensagem
                ByteArrayInputStream bais = new ByteArrayInputStream(frame.data);
                DataInputStream dis = new DataInputStream(bais);
                Protocol.Message msg = Protocol.receiveMessage(dis);
                
                // Processar
                Protocol.Message response = handleMessage(msg);
                
                // Serializar resposta
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                Protocol.sendMessage(dos, response);
                dos.flush();
                
                // Enviar resposta com mesmo tag
                connection.send(frame.tag, baos.toByteArray());
            }
            
        } catch (EOFException e) {
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente desconectou");
        } catch (IOException e) {
            System.err.println("[" + socket.getRemoteSocketAddress() + "] Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
                socket.close();
                System.out.println("[" + socket.getRemoteSocketAddress() + "] Desconectado");
            } catch (IOException e) {
                // ignore
            }
        }
    }
    
    private Protocol.Message handleMessage(Protocol.Message msg) {
        switch (msg.type) {
            case Protocol.REGISTER:
                return handleRegister(msg);
            case Protocol.LOGIN:
                return handleLogin(msg);
            case Protocol.ADD_EVENT:
                return handleAddEvent(msg);
            case Protocol.QUERY_PRODUCT:
                return handleQueryProduct(msg);
            case Protocol.LIST_PRODUCTS:
                return handleListProducts(msg);
            case Protocol.NEXT_DAY:
                return handleNextDay(msg);
            case Protocol.LOGOUT:
                return handleLogout(msg);
            case Protocol.AGGREGATE_RANGE:
                return handleAggregateRange(msg);
            case Protocol.FILTER_EVENTS:
                return handleFilterEvents(msg);
            case Protocol.WAIT_SIMULTANEOUS:
                return handleWaitSimultaneous(msg);
            case Protocol.WAIT_CONSECUTIVE:
                return handleWaitConsecutive(msg);
            default:
                return Protocol.error("Comando desconhecido");
        }
    }
    
    private Protocol.Message handleRegister(Protocol.Message msg) {
        if (msg.args.length < 2) {
            return Protocol.error("Uso: REGISTER username password");
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (username.isEmpty() || password.isEmpty()) {
            return Protocol.error("Username e password não podem estar vazios");
        }
        
        if (authManager.register(username, password)) {
            System.out.println("Novo utilizador: " + username);
            return Protocol.ok("Utilizador registado");
        } else {
            return Protocol.error("Utilizador já existe");
        }
    }
    
    private Protocol.Message handleLogin(Protocol.Message msg) {
        if (msg.args.length < 2) {
            return Protocol.error("Uso: LOGIN username password");
        }
        
        String username = msg.args[0];
        String password = msg.args[1];
        
        if (authManager.authenticate(username, password)) {
            currentUser = username;
            System.out.println("Login: " + username);
            return Protocol.ok("Login bem-sucedido");
        } else {
            return Protocol.error("Credenciais inválidas");
        }
    }
    
    private Protocol.Message handleAddEvent(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        if (msg.args.length < 3) {
            return Protocol.error("Uso: ADD_EVENT produto quantidade preco");
        }
        
        try {
            String produto = msg.args[0];
            int quantidade = Integer.parseInt(msg.args[1]);
            float preco = Float.parseFloat(msg.args[2]);
            
            if (quantidade <= 0 || preco < 0) {
                return Protocol.error("Quantidade deve ser positiva e preço não-negativo");
            }
            
            Event event = new Event(produto, quantidade, preco);
            tsManager.addEvent(currentUser, event);
            
            return Protocol.ok("Evento registado");
        } catch (NumberFormatException e) {
            return Protocol.error("Quantidade/preço inválidos");
        }
    }

    private Protocol.Message handleQueryProduct(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }

        if (msg.args.length < 1) {
            return Protocol.error("Uso: QUERY_PRODUCT produto");
        }

        String produto = msg.args[0];
        java.util.Map<String, Object> stats = tsManager.getStats(currentUser, produto);

        System.out.println("[DEBUG] Stats para produto '" + produto + "': " + stats);

        if (stats.isEmpty()) {
            return Protocol.ok("0");
        }

        // Verificar se tem quantidade > 0
        Object qtdObj = stats.get("quantidade_total");
        if (qtdObj == null || ((Number) qtdObj).intValue() == 0) {
            return Protocol.ok("0");
        }

        // Extrair valores
        int qtdTotal = ((Number) qtdObj).intValue();
        float volumeTotal = ((Number) stats.get("volume_total")).floatValue();
        float precoMax = ((Number) stats.get("preco_max")).floatValue();
        float precoMin = ((Number) stats.get("preco_min")).floatValue();
        float precoMedio = ((Number) stats.get("preco_medio")).floatValue();

        String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
            qtdTotal, volumeTotal, precoMax, precoMin, precoMedio);
        return Protocol.ok(response);
    }
    
    private Protocol.Message handleListProducts(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        java.util.Set<String> produtos = tsManager.getAllProdutos(currentUser);
        
        if (produtos.isEmpty()) {
            return Protocol.ok("");
        } else {
            String response = String.join(",", produtos);
            return Protocol.ok(response);
        }
    }
    
    private Protocol.Message handleNextDay(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        tsManager.nextDay();
        int currentDay = tsManager.getCurrentDay();
        System.out.println(currentUser + " avançou para dia " + currentDay);
        return Protocol.ok("Dia atual: " + currentDay);
    }
    
    private Protocol.Message handleLogout(Protocol.Message msg) {
        if (currentUser != null) {
            System.out.println("Logout: " + currentUser);
            currentUser = null;
        }
        return Protocol.ok("Logout efetuado");
    }
    
    private Protocol.Message handleAggregateRange(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        if (msg.args.length < 2) {
            return Protocol.error("Uso: AGGREGATE_RANGE produto numeroDias");
        }
        
        try {
            String produto = msg.args[0];
            int numeroDias = Integer.parseInt(msg.args[1]);
            
            if (numeroDias <= 0 || numeroDias > tsManager.getD()) {
                return Protocol.error("Número de dias inválido (1 a " + tsManager.getD() + ")");
            }
            
            java.util.Map<String, Object> stats = tsManager.getStatsUltimosDias(currentUser, produto, numeroDias);
            
            if (stats.isEmpty()) {
                return Protocol.ok("0");
            } else {
                String response = String.format("%d|%.2f|%.2f|%.2f|%.2f",
                    stats.get("quantidade_total"),
                    stats.get("preco_total"),
                    stats.get("preco_max"),
                    stats.get("preco_min"),
                    stats.get("preco_medio")
                );
                return Protocol.ok(response);
            }
        } catch (NumberFormatException e) {
            return Protocol.error("Número de dias inválido");
        }
    }
    
    private Protocol.Message handleFilterEvents(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        if (msg.args.length < 2) {
            return Protocol.error("Uso: FILTER_EVENTS dia produto1 [produto2 ...]");
        }
        
        try {
            int dia = Integer.parseInt(msg.args[0]);
            
            if (dia < 1 || dia > tsManager.getD()) {
                return Protocol.error("Dia inválido (1 a " + tsManager.getD() + ")");
            }
            
            String[] produtos = new String[msg.args.length - 1];
            System.arraycopy(msg.args, 1, produtos, 0, msg.args.length - 1);
            
            java.util.Map<String, java.util.List<Event>> eventosFiltrados = 
                tsManager.getEventosFiltrados(currentUser, dia, produtos);
            
            if (eventosFiltrados.isEmpty()) {
                return Protocol.ok("");
            }
            
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
            
            return Protocol.ok(response.toString());
            
        } catch (NumberFormatException e) {
            return Protocol.error("Dia inválido");
        }
    }
    
    private Protocol.Message handleWaitSimultaneous(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        if (msg.args.length < 2) {
            return Protocol.error("Uso: WAIT_SIMULTANEOUS produto1 produto2");
        }
        
        String produto1 = msg.args[0];
        String produto2 = msg.args[1];
        
        try {
            boolean satisfied = tsManager.getNotificationManager()
                .waitSimultaneous(currentUser, produto1, produto2);
            
            if (satisfied) {
                return Protocol.ok("true");
            } else {
                return Protocol.ok("false");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Protocol.error("Interrompido");
        }
    }
    
    private Protocol.Message handleWaitConsecutive(Protocol.Message msg) {
        if (currentUser == null) {
            return Protocol.unauthorized("Login necessário");
        }
        
        if (msg.args.length < 2) {
            return Protocol.error("Uso: WAIT_CONSECUTIVE produto n");
        }
        
        try {
            String produto = msg.args[0];
            int n = Integer.parseInt(msg.args[1]);
            
            if (n <= 0) {
                return Protocol.error("Número de vendas deve ser positivo");
            }
            
            boolean satisfied = tsManager.getNotificationManager()
                .waitConsecutive(currentUser, produto, n);
            
            if (satisfied) {
                return Protocol.ok(produto);
            } else {
                return Protocol.ok("null");
            }
        } catch (NumberFormatException e) {
            return Protocol.error("Número de vendas inválido");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Protocol.error("Interrompido");
        }
    }
}