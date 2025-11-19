package uminho.grupo57;

import java.io.*;
import java.net.Socket;

/**
 * Cliente TCP para comunicação com servidor de TimeSeries
 * Utiliza protocolo text-based com mensagens pipe-delimited
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("client-lib é uma biblioteca (API).");
        System.out.println("Use a classe TimeSeriesClient nos seus programas.");
        System.out.println();
        System.out.println("Exemplo:");
        System.out.println("  TimeSeriesClient client = new TimeSeriesClient();");
        System.out.println("  client.connect(\"localhost\", 8080);");
        System.out.println("  client.register(\"user\", \"pass\");");
        System.out.println("  client.login(\"user\", \"pass\");");
        System.out.println("  client.registarCompra(\"Arroz\", 2, 1.50f);");
        System.out.println("  client.disconnect();");
    }
}