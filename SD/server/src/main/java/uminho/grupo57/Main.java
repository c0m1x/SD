package uminho.grupo57;

import java.io.IOException;

/**
 * Ponto de entrada principal do servidor
 */
public class Main {
    public static void main(String[] args) throws IOException
    {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int maxDays = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int maxSeries = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int threads = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        int threadsDisk = args.length > 4 ? Integer.parseInt(args[4]) : 5;
        String nDirs = args.length > 5 ? args[5] : "data/series";

        Server srv = new Server(port, maxDays, maxSeries, threads, threadsDisk, nDirs);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            System.out.println("\nA encerrar servidor (Ctrl+C)...");
            try{
                srv.stop();
            }catch (IOException e){e.printStackTrace();}
        }));

        try{
            srv.start(threads, nDirs);
        }catch (IOException e){
            System.err.println("Erro grave no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
