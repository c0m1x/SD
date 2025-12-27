package uminho.grupo57;

import uminho.grupo57.clientHandling.AutenticathionManager;
import uminho.grupo57.clientHandling.ClientHandler;
import uminho.grupo57.clientHandling.TimeSeriesManager;

import java.io.*;
import java.net.*;

/**
 * Servidor multi-threaded para gestão de séries temporais
 * Usa thread pool para gerir vários clientes ao mesmo tempo
 */
public class Server
{
    private final ServerSocket serverSocket;
    private final int port;
    private final int maxDays;
    private final int maxSeriesInMemory;

    private boolean isRunning = true;

    private final AutenticathionManager authManager = new AutenticathionManager();
    private final TimeSeriesManager tsManager;
    private final ThreadPool threadPool;
    
    public Server(int port, int maxDays, int maxSeriesInMemory, int numRunnerThreads, String dataDir) throws IOException
    {
        this.port = port;
        this.maxDays = maxDays;
        this.maxSeriesInMemory = maxSeriesInMemory;
        this.tsManager = new TimeSeriesManager(maxDays, maxSeriesInMemory, dataDir);
        this.threadPool = new ThreadPool(numRunnerThreads);
        this.serverSocket = new ServerSocket(port);
    }

    public void start(int numThreads, String directory) throws IOException
    {

        System.out.println("┌─────────────────────────────────────────────┐");
        System.out.println("│  SERVIDOR TIMESERIES INICIADO               │");
        System.out.println(String.format("│  Porta: %-36s│", port));
        System.out.println(String.format("│  Dias Máximos (D): %-25s│", maxDays));
        System.out.println(String.format("│  Séries em Memória (S): %-20s│", maxSeriesInMemory));
        System.out.println(String.format("│  Número de Threads: %-24s│", numThreads));
        System.out.println(String.format("│  Diretoria de Persistência: %-16s│", directory));
        System.out.println("└─────────────────────────────────────────────┘");

        try {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nova ligação de: " + clientSocket.getRemoteSocketAddress());
                    threadPool.submitTask(new ClientHandler(clientSocket, authManager, tsManager, threadPool));

                }catch (SocketException | InterruptedException e){
                    if(isRunning)
                        System.err.println("Erro no socket: " + e.getMessage());
                    break;
                }
            }
        }finally{
            if(isRunning)
                stop();
            System.out.println("Servidor encerrado.");
        }
    }


    public void stop() throws IOException
    {
        if(!isRunning)
            return;
        isRunning = false;

        if(!serverSocket.isClosed())
            serverSocket.close();

        threadPool.shutdown();
        tsManager.shutdownPersist();
        authManager.persistAll();
    }


    public static void main(String[] args) throws IOException
    {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int maxDays = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int maxSeries = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int threads = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        String nDirs = args.length > 4 ? args[4] : "data/series";

        Server srv = new Server(port, maxDays, maxSeries, threads, nDirs);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nA encerrar servidor (Ctrl+C)...");
            try {
                srv.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        try {
            srv.start(threads, nDirs);
        } catch (IOException e) {
            System.err.println("Erro grave no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}