package uminho.grupo57;

import java.io.IOException;

/**
 * Ponto de entrada principal do servidor
 */
public class Main {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int maxDays = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int maxSeries = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        
        server srv = new server(port, maxDays, maxSeries);
        try {
            srv.start();
        } catch (IOException e) {
            System.err.println("Erro fatal no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
