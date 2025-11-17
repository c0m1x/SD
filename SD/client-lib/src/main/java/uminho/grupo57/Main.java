package uminho.grupo57;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * API cliente para gestão de TimeSeries
 * Fornece métodos simplificados para adicionar e consultar eventos de produtos
 */
public class Main {
    private TimeSeries serie;

    public Main() {
        this.serie = new TimeSeries();
    }

    /**
     * Regista um novo evento de compra
     */
    public void registarCompra(String produto, int quantidade, float preco) {
        Event evento = new Event(produto, quantidade, preco);
        serie.addEvent(evento);
        System.out.println("✓ Compra registada: " + produto);
    }

    /**
     * Consulta estatísticas de um produto
     */
    public void consultarProduto(String produto) {
        List<Event> eventos = serie.getEventosProduto(produto);
        
        if(eventos.isEmpty()) {
            System.out.println("Produto '" + produto + "' não encontrado.");
            return;
        }

        System.out.println("\n═══ Estatísticas: " + produto + " ═══");
        System.out.println("Total de eventos: " + eventos.size());
        System.out.println("Quantidade total: " + serie.getTotalQuantidadeProduto(produto) + " unidades");
        System.out.printf("Valor total gasto: %.2f€%n", serie.getTotalPrecoProduto(produto));
        
        Optional<Float> max = serie.getPrecoMaximoProduto(produto);
        Optional<Float> min = serie.getPrecoMinimoProduto(produto);
        Optional<Float> media = serie.getPrecoMedioProduto(produto);
        
        max.ifPresent(p -> System.out.printf("Preço máximo: %.2f€%n", p));
        min.ifPresent(p -> System.out.printf("Preço mínimo: %.2f€%n", p));
        media.ifPresent(p -> System.out.printf("Preço médio: %.2f€%n", p));
    }

    /**
     * Lista todos os produtos registados
     */
    public void listarProdutos() {
        Set<String> produtos = serie.getAllProdutos();
        
        if(produtos.isEmpty()) {
            System.out.println("Nenhum produto registado.");
            return;
        }

        System.out.println("\n═══ Produtos Registados ═══");
        produtos.forEach(p -> System.out.println("  • " + p));
    }

    public TimeSeries getSerie() {
        return serie.clone();
    }

    // Exemplo de uso
    public static void main(String[] args) {
        Main api = new Main();
        
        // Registar algumas compras de teste
        api.registarCompra("Pão", 2, 1.50f);
        api.registarCompra("Leite", 1, 0.89f);
        api.registarCompra("Pão", 3, 1.45f);
        api.registarCompra("Café", 1, 3.20f);
        api.registarCompra("Pão", 1, 1.55f);

        // Listar produtos
        api.listarProdutos();

        // Consultar estatísticas
        api.consultarProduto("Pão");
        api.consultarProduto("Leite");
        api.consultarProduto("Café");
    }
}