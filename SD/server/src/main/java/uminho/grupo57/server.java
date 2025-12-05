package uminho.grupo57;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Servidor multi-threaded para gestão de séries temporais
 * Usa thread pool para gerir vários clientes ao mesmo tempo
 */
public class server {

    private final int port;
    private final int maxDays;
    private final int maxSeriesInMemory;
    private volatile boolean isRunning = true;
    private final autenticathionManager authManager;
    private final timeSeriesManager tsManager;
    private final ExecutorService clientHandlers;
    
    public server(int port, int maxDays, int maxSeriesInMemory){
        this.port = port;
        this.maxDays = maxDays;
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.authManager = new autenticathionManager();
        this.tsManager = new timeSeriesManager(maxDays, maxSeriesInMemory);
        this.clientHandlers = Executors.newCachedThreadPool();
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("┌─────────────────────────────────────────────┐");
        System.out.println("│  SERVIDOR TIMESERIES INICIADO               │");
        System.out.println(String.format("│  Porta: %-36s│", port));
        System.out.println(String.format("│  Dias Máximos (D): %-26s│", maxDays));
        System.out.println(String.format("│  Séries em Memória (S): %-21s│", maxSeriesInMemory));
        System.out.println("└─────────────────────────────────────────────┘");

        // Guardar dados automaticamente quando o servidor fechar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nA encerrar - a guardar dados...");
            tsManager.persistAll();
            authManager.persistAll();
        }));

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nova ligação de: " + clientSocket.getRemoteSocketAddress());
                clientHandlers.submit(new ClientHandler(clientSocket, authManager, tsManager));
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Erro ao aceitar ligação: " + e.getMessage());
                }
            }
        }
        
        serverSocket.close();
        clientHandlers.shutdown();
        System.out.println("Servidor encerrado.");
    }

    public void nextDay() {
        tsManager.nextDay();
    }

    public void stop() {
        isRunning = false;
        tsManager.persistAll(); // Guardar antes de parar
            authManager.persistAll();
        System.out.println("A parar o servidor...");
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int maxDays = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int maxSeries = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        
        server srv = new server(port, maxDays, maxSeries);
        try {
            srv.start();
        } catch (IOException e) {
            System.err.println("Erro grave no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}