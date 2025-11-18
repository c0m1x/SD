package uminho.grupo57;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Servidor multi-threaded para gestão de séries temporais
 * Usa thread pool para gerir múltiplos clientes simultaneamente
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
        System.out.println("│  Porta: " + port + "                        │");
        System.out.println("│  Max Dias: " + maxDays + "                  │");
        System.out.println("│  Séries em Memória: " + maxSeriesInMemory +"│");
        System.out.println("└─────────────────────────────────────────────┘");

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nova conexão: " + clientSocket.getRemoteSocketAddress());
                clientHandlers.submit(new ClientHandler(clientSocket, authManager, tsManager));
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
        
        serverSocket.close();
        clientHandlers.shutdown();
        System.out.println("Servidor encerrado.");
    }

    public void nextDay() {
        tsManager.nextDay();
        System.out.println("Avançou para o dia seguinte");
    }

    public void stop() {
        isRunning = false;
        System.out.println("Parando servidor...");
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int maxDays = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        int maxSeries = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        
        server srv = new server(port, maxDays, maxSeries);
        try {
            srv.start();
        } catch (IOException e) {
            System.err.println("Erro fatal no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}