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
        String nDirs = args.length > 4 ? args[4] : "data/series";

        Server srv = new Server(port, maxDays, maxSeries, threads, nDirs);
        try {
            srv.start(threads, nDirs);
        } catch (IOException e) {
            System.err.println("Erro fatal no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
