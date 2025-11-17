package uminho.grupo57;

import java.util.Scanner;
import java.io.IOException;

/**
 * Interface de linha de comando para gestão de séries temporais de eventos
 */
public class Main {
    private static TimeSeriesClient client = new TimeSeriesClient();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║   Sistema de Gestão de Compras        ║");
        System.out.println("╚═══════════════════════════════════════╝\n");

        // Conectar ao servidor
        try {
            String host = args.length > 0 ? args[0] : "localhost";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
            
            client.connect(host, port);
        } catch (IOException e) {
            System.err.println("✗ Erro ao conectar: " + e.getMessage());
            return;
        }

        // Autenticação obrigatória
        if (!autenticar()) {
            try {
                client.disconnect();
            } catch (IOException e) {
                // ignora erro ao desconectar
            }
            return;
        }

        // Menu principal
        boolean sair = false;
        while(!sair) {
            mostrarMenu();
            int opcao = lerOpcao();
            
            try {
                switch(opcao) {
                    case 1:
                        registarCompra();
                        break;
                    case 2:
                        consultarProduto();
                        break;
                    case 3:
                        listarProdutos();
                        break;
                    case 0:
                        sair = true;
                        System.out.println("\nAté breve!");
                        break;
                    default:
                        System.out.println("Opção inválida!");
                }
            } catch (IOException e) {
                System.err.println("Erro de comunicação: " + e.getMessage());
            }
            
            if(!sair) {
                System.out.println("\nPressione ENTER para continuar...");
                scanner.nextLine();
            }
        }
        
        try {
            client.disconnect();
        } catch (IOException e) {
            // ignorar
        }
        scanner.close();
    }

    private static boolean autenticar() {
        System.out.println("\n┌─────────────────────────────┐");
        System.out.println("│       AUTENTICAÇÃO          │");
        System.out.println("├─────────────────────────────┤");
        System.out.println("│ 1. Login                    │");
        System.out.println("│ 2. Registar novo utilizador │");
        System.out.println("└─────────────────────────────┘");
        System.out.print("Opção: ");
        
        int opcao = lerOpcao();
        
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        
        try {
            if (opcao == 2) {
                if (!client.register(username, password)) {
                    return false;
                }
            }
            return client.login(username, password);
        } catch (IOException e) {
            System.err.println("Erro de autenticação: " + e.getMessage());
            return false;
        }
    }
    
    private static void mostrarMenu() {
        System.out.println("\n┌─────────────────────────────┐");
        System.out.println("│         MENU PRINCIPAL      │");
        System.out.println("├─────────────────────────────┤");
        System.out.println("│ 1. Registar Compra          │");
        System.out.println("│ 2. Consultar Estatísticas   │");
        System.out.println("│ 3. Listar Produtos          │");
        System.out.println("│ 0. Sair                     │");
        System.out.println("└─────────────────────────────┘");
        System.out.print("Opção: ");
    }

    private static int lerOpcao() {
        try {
            int opcao = Integer.parseInt(scanner.nextLine());
            return opcao;
        } catch(NumberFormatException e) {
            return -1;
        }
    }

    private static void registarCompra() throws IOException {
        System.out.println("\n═══ Registar Nova Compra ═══");
        
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        
        if(produto.isEmpty()) {
            System.out.println("Nome inválido!");
            return;
        }
        
        System.out.print("Quantidade: ");
        int quantidade;
        try {
            quantidade = Integer.parseInt(scanner.nextLine());
            if(quantidade <= 0) {
                System.out.println("Quantidade deve ser positiva!");
                return;
            }
        } catch(NumberFormatException e) {
            System.out.println("Quantidade inválida!");
            return;
        }
        
        System.out.print("Preço (€): ");
        float preco;
        try {
            preco = Float.parseFloat(scanner.nextLine());
            if(preco < 0) {
                System.out.println("Preço inválido!");
                return;
            }
        } catch(NumberFormatException e) {
            System.out.println("Preço inválido!");
            return;
        }
        
        if (client.registarCompra(produto, quantidade, preco)) {
            System.out.println("\n Compra registada com sucesso!");
            System.out.printf("  %s - %d unidades - %.2f€%n", produto, quantidade, preco);
        }
    }

    private static void consultarProduto() throws IOException {
        System.out.println("\n═══ Consultar Estatísticas ═══");
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        
        client.consultarProduto(produto);
    }

    private static void listarProdutos() throws IOException {
        client.listarProdutos();
    }
}