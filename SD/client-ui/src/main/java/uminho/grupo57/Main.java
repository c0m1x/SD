package uminho.grupo57;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Interface de linha de comando para gestão de séries temporais de eventos
 */
public class Main {
    private static TimeSeries serie = new TimeSeries();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║   Sistema de Gestão de Compras       ║");
        System.out.println("╚═══════════════════════════════════════╝\n");

        boolean sair = false;
        while(!sair) {
            mostrarMenu();
            int opcao = lerOpcao();
            
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
                case 4:
                    listarEventosProduto();
                    break;
                case 0:
                    sair = true;
                    System.out.println("\n👋 Até breve!");
                    break;
                default:
                    System.out.println("⚠ Opção inválida!");
            }
            
            if(!sair) {
                System.out.println("\nPressione ENTER para continuar...");
                scanner.nextLine();
            }
        }
        
        scanner.close();
    }

    private static void mostrarMenu() {
        System.out.println("\n┌─────────────────────────────┐");
        System.out.println("│         MENU PRINCIPAL      │");
        System.out.println("├─────────────────────────────┤");
        System.out.println("│ 1. Registar Compra          │");
        System.out.println("│ 2. Consultar Estatísticas   │");
        System.out.println("│ 3. Listar Produtos          │");
        System.out.println("│ 4. Histórico de Produto     │");
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

    private static void registarCompra() {
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
        
        Event evento = new Event(produto, quantidade, preco);
        serie.addEvent(evento);
        
        System.out.println("\n✓ Compra registada com sucesso!");
        System.out.printf("  %s - %d unidades - %.2f€%n", produto, quantidade, preco);
    }

    private static void consultarProduto() {
        System.out.println("\n═══ Consultar Estatísticas ═══");
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        
        List<Event> eventos = serie.getEventosProduto(produto);
        
        if(eventos.isEmpty()) {
            System.out.println("⚠ Produto '" + produto + "' não encontrado.");
            return;
        }

        System.out.println("\n┌───────────────────────────────────────┐");
        System.out.println("│  Estatísticas: " + produto);
        System.out.println("├───────────────────────────────────────┤");
        System.out.println("│ Total de eventos: " + eventos.size());
        System.out.println("│ Quantidade total: " + serie.getTotalQuantidadeProduto(produto) + " unidades");
        System.out.printf("│ Valor total gasto: %.2f€%n", serie.getTotalPrecoProduto(produto));
        
        Optional<Float> max = serie.getPrecoMaximoProduto(produto);
        Optional<Float> min = serie.getPrecoMinimoProduto(produto);
        Optional<Float> media = serie.getPrecoMedioProduto(produto);
        
        max.ifPresent(p -> System.out.printf("│ Preço máximo: %.2f€%n", p));
        min.ifPresent(p -> System.out.printf("│ Preço mínimo: %.2f€%n", p));
        media.ifPresent(p -> System.out.printf("│ Preço médio: %.2f€%n", p));
        System.out.println("└───────────────────────────────────────┘");
    }

    private static void listarProdutos() {
        Set<String> produtos = serie.getAllProdutos();
        
        if(produtos.isEmpty()) {
            System.out.println("\nNenhum produto registado.");
            return;
        }

        System.out.println("\n═══ Produtos Registados ═══");
        int count = 1;
        for(String p : produtos) {
            int total = serie.getTotalQuantidadeProduto(p);
            System.out.printf("%2d. %-20s (%d compras)%n", count++, p, 
                serie.getEventosProduto(p).size());
        }
    }

    private static void listarEventosProduto() {
        System.out.println("\n═══ Histórico de Produto ═══");
        System.out.print("Nome do produto: ");
        String produto = scanner.nextLine().trim();
        
        List<Event> eventos = serie.getEventosProduto(produto);
        
        if(eventos.isEmpty()) {
            System.out.println("Produto '" + produto + "' não encontrado.");
            return;
        }

        System.out.println("\n┌───────────────────────────────────────┐");
        System.out.println("│  Histórico: " + produto);
        System.out.println("├───────────────────────────────────────┤");
        
        int count = 1;
        for(Event e : eventos) {
            System.out.printf("│ %d) %s%n", count++, e.getHoraEvento());
            System.out.printf("│    Qtd: %d | Preço: %.2f€%n", 
                e.getQuantidade(), e.getPreco());
        }
        System.out.println("└───────────────────────────────────────┘");
    }
}