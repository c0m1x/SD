//Trata tudo sobre o servidor

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class server {

    private final int port;
    private final int maxDays;
    private final int maxSeriesInMemory;
    private volatile boolean isRunning = true;
    //private final AuthenticationManager authManager;
    //private final TimeSeriesManager tsManager;
    //private final ExecutorService clientHandler;
    
    public Server(int port, int maxDays, int maxSeriesInMemory){
        this.port = port;
        this.maxDays = maxDays;
        this.maxSeriesInMemory = maxSeriesInMemory;
        //this.authManager = new AuthenticationManager();
        //this.tsManager = new TimeSeriesManager(maxDays, maxSeriesInMemory);
        //this.clientHandler = Executors.newCachedThreadPool();
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        //DEBUG FUTURO
        System.out.println("Servidor iniciado na porta " + port);
        System.out.println("Parâmetros: D=" + maxDays + ", S=" + maxSeriesInMemory);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nova conexão: " + clientSocket.getRemoteSocketAddress());
                clientHandlers.submit(new ClientHandler(clientSocket, authManager, timeSeriesManager));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
        
        serverSocket.close();
        clientHandlers.shutdown();
    }

    public void nextDay() {
        //tsManager.nextDay();
    }

    public void stop() {
        isRunning = false;
    }


}
