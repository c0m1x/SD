package uminho.grupo57;

import java.util.Scanner;
import java.io.IOException;

/**
 * Interface de linha de comando para gestão de séries temporais de eventos
 */
public class Main
{
    private static TimeSeriesClient client = new TimeSeriesClient();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws InterruptedException {
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
                client.close();
            } catch (IOException e) {
                // ignorar erro ao desconectar
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
                        consultarAgregacaoRange();
                    case 4:
                        listarProdutos();
                        break;
                    case 5:
                        filtrarEventos();
                        break;
                    case 6:
                        aguardVendaConsecutiva();
                        break;
                    case 7:
                        aguardarVendaSimultaneas();
                        break;
                    case 8:
                        avancarDia();
                        break;
                    case 0:
                        sair = true;
                        System.out.println("\nAté breve!");
                        break;
                    default:
                        System.out.println("Opção inválida!");
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Erro de comunicação: " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().join();
                }
            }
            
            if(!sair) {
                System.out.println("\nPressione ENTER para continuar...");
                scanner.nextLine();
            }
        }
        
        try {
            client.close();
        } catch (IOException e) {
            // ignorar
        }
           scanner.close();
       }

        private static boolean autenticar() throws InterruptedException {
        while (true) {
            System.out.println("\n┌─────────────────────────────┐");
            System.out.println("│       AUTENTICAÇÃO          │");
            System.out.println("├─────────────────────────────┤");
            System.out.println("│ 1. Login                    │");
            System.out.println("│ 2. Registar novo utilizador │");
            System.out.println("│ 0. Sair                     │");
            System.out.println("└─────────────────────────────┘");
            System.out.print("Opção: ");

            int opcao = lerOpcao();

            if (opcao == 0) {
                System.out.println("A sair...");
                return false;
            }

            if (opcao != 1 && opcao != 2) {
                System.out.println("Opção inválida!");
                continue;
            }

            System.out.print("Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            try {
                if(opcao == 2) // Tentar registar
                {
                    if(client.register(username, password))
                    {
                        if(client.login(username, password))
                            return true;
                    }
                    // Se falhou, continuar o loop para tentar novamente
                    System.out.println("\nPressione ENTER para tentar novamente...");
                    scanner.nextLine();
                } else {
                    // Login direto
                    if (client.login(username, password)) {
                        return true;
                    }
                    // Se falhou, continuar o loop
                    System.out.println("\nPressione ENTER para tentar novamente...");
                    scanner.nextLine();
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Erro de comunicação: " + e.getMessage());
                if (e instanceof InterruptedException)
                    Thread.currentThread().join();
                return false;
            }
        }
    }
    
    private static void mostrarMenu() {
        System.out.println("\n┌─────────────────────────────────┐");
        System.out.println("│         MENU PRINCIPAL          │");
        System.out.println("├─────────────────────────────────┤");
        System.out.println("│ 1. Registar Compra              │");
        System.out.println("│ 2. Consultar Estatísticas       │");
        System.out.println("│ 3. Consultar Agregação          │");
        System.out.println("│ 4. Listar Produtos do Dia       │");
        System.out.println("│ 5. Filtrar Eventos              │");
        System.out.println("│ 6. Aguardar Vendas Consecutivas │");
        System.out.println("│ 7. Aguardar Vendas Simultaneas  │");
        System.out.println("│ 8. Avançar Dia                  │");
        System.out.println("│ 0. Sair                         │");
        System.out.println("└─────────────────────────────────┘");
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

    private static void registarCompra() throws IOException, InterruptedException {
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
        
        client.handleRegistarCompra(produto, quantidade, preco);
        System.out.println("\nCompra registada com sucesso!");
        System.out.printf("  %s - %d unidades - %.2f€%n", produto, quantidade, preco);
    }

    public static void consultarProduto() throws IOException, InterruptedException {
        System.out.println("\n═══ Consultar Estatísticas ═══");
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        
        client.handleConsultarProduto(produto);
    }

    public static void listarProdutos() throws IOException, InterruptedException {
        client.handleListarProdutos();
    }

    private static void consultarAgregacaoRange() throws IOException, InterruptedException
    {
        System.out.println("\n═══ Consultar Agregação nos últimos dias de um Produto ═══");
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        int intervalo;
        try{
            System.out.print("Número de dias anteriores: ");
            intervalo = Integer.parseInt(scanner.nextLine());
            if(intervalo < 0)
            {
                System.out.println("Intervalo inválido!");
                return;
            }
        }catch(NumberFormatException e){
            System.out.println("Intervalo inválido!");
            return;
        }
        client.handleConsultarAgregacaoRange(produto, intervalo);
    }

    private static void filtrarEventos() throws IOException, InterruptedException
    {
        System.out.println("\n═══ Filtrar Eventos ═══");
        int prodNum, intervalo;
        String[] products;
        try{
            System.out.print("Número de produtos: ");
            prodNum = Integer.parseInt(scanner.nextLine());
            if(prodNum <= 0)
            {
                System.out.println("Número de produtos inválido!");
                return;
            }
            products = new String[prodNum];
        }catch(NumberFormatException e){
            System.out.println("Número de produtos inválido!");
            return;
        }

        for(int i=0; i<prodNum; i++)
        {
            System.out.println("Nome do Produto " + (i+1) + ": ");
            products[i] = scanner.nextLine().trim();
        }

        try{
            System.out.print("Número de dias anteriores: ");
            intervalo = Integer.parseInt(scanner.nextLine());
            if(intervalo < 0)
            {
                System.out.println("Intervalo inválido!");
                return;
            }
        }catch(NumberFormatException e){
            System.out.println("Intervalo inválido!");
            return;
        }
        System.out.print("Número de Produtos: ");
        client.handleFiltrarEventos(intervalo, products);
    }

    private static void aguardarVendaSimultaneas() throws IOException, InterruptedException
    {
        System.out.println("\n═══ Aguardar Vendas Simultaneas ═══");
        System.out.print("Nome do Produto 1: ");
        String prod1 = scanner.nextLine().trim();
        System.out.print("Nome do Produto 2: ");
        String prod2 = scanner.nextLine().trim();

        client.handleAguardarVendasSimultaneas(prod1, prod2);
    }

    private static void aguardVendaConsecutiva() throws IOException, InterruptedException {
        System.out.println("\n═══ Aguardar Vendas Consecutivas ═══");
        System.out.print("Nome do Produto: ");
        String prod = scanner.nextLine().trim();
        int numeroVendas;
        try{
            System.out.print("Número de venas (maior que 0): ");
            numeroVendas = Integer.parseInt(scanner.nextLine());
            if(numeroVendas <= 0)
            {
                System.out.println("Número de vendas inválido!");
                return;
            }
        }catch(NumberFormatException e){
            System.out.println("Número de vendas inválido!");
            return;
        }
        client.handleAguardarVendasConsecutivas(prod, numeroVendas);
    }

    private static void avancarDia() throws IOException, InterruptedException {
        System.out.println("\n═══ Avançar Dia ═══");
        System.out.print("Deseja avançar para o dia seguinte? (s/n): ");
        String resposta = scanner.nextLine().trim().toLowerCase();
        
        if(resposta.equals("s") || resposta.equals("sim")) {
            client.handleNextDay();
        } else {
            System.out.println("Operação cancelada.");
        }
    }
}