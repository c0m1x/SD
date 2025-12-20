package uminho.grupo57;

import java.io.*;
import java.util.*;

/**
 * Teste de Eficiência da Serialização de FILTER_EVENTS
 * 
 * TODO: Validar se a serialização atual é eficiente conforme enunciado:
 * 
 * CONTEXTO DO ENUNCIADO (Secção 4):
 * "Como a lista de eventos a devolver ao cliente é potencialmente grande e com 
 * nomes de produtos aparecendo repetidos, deverá ser implementada uma serialização 
 * de dados eficiente, que represente a lista de forma compacta."
 * 
 * IMPLEMENTAÇÃO ATUAL:
 * - ClientHandler.handleFilterEvents() retorna String no formato:
 *   "produto1|count|qtd1:preco1,qtd2:preco2||produto2|count|..."
 * - Problema: Nomes de produtos repetidos consomem muitos bytes
 * 
 * TESTES A IMPLEMENTAR:
 * 
 * 1. TESTE DE TAMANHO DE PAYLOAD
 *    - Criar cenário com 1000 eventos de 10 produtos diferentes
 *    - Medir tamanho em bytes da serialização atual (String)
 *    - Comparar com serialização binária otimizada
 *    - Objetivo: Reduzir pelo menos 30-50% do tamanho
 * 
 * 2. PROPOSTA DE SERIALIZAÇÃO BINÁRIA EFICIENTE
 *    - Usar dicionário de produtos: mapear nome -> ID (1 byte ou 2 bytes)
 *    - Formato compacto:
 *      [numProdutos:1byte]
 *      [produto1_length:1byte][produto1_name:bytes][numEventos:2bytes]
 *        [qtd:4bytes][preco:4bytes] ... (repetir para cada evento)
 *      [produto2_length:1byte]...
 *    - Total por produto: ~10-20 bytes + 8 bytes por evento
 *    - vs String atual: ~30-50 bytes + ~15 bytes por evento
 * 
 * 3. TESTE DE DESEMPENHO
 *    - Medir tempo de serialização: String vs Binário
 *    - Medir tempo de deserialização no cliente
 *    - Comparar com diferentes tamanhos de resposta (10, 100, 1000, 10000 eventos)
 * 
 * 4. IMPLEMENTAÇÃO SUGERIDA (se necessário otimizar)
 *    - Criar classe CompactSerializer em common/
 *    - Métodos: serializeEvents(Map<String, List<Event>>) -> byte[]
 *    - Métodos: deserializeEvents(byte[]) -> Map<String, List<Event>>
 *    - Atualizar Protocol.java para novo tipo de mensagem ou campo binário
 *    - Atualizar ClientHandler.handleFilterEvents()
 *    - Atualizar TimeSeriesClient.filterEvents()
 * 
 * CRITÉRIO DE DECISÃO:
 * - Se tamanho médio > 1KB e redução > 40%: Vale a pena implementar
 * - Se tamanho médio < 1KB: Serialização atual pode ser aceitável
 * - Documentar decisão no RELATÓRIO com dados dos testes!
 */
public class SerializationEfficiencyTest {
    
    // TODO: Implementar medição de tamanho da serialização atual
    public static void testCurrentSerializationSize() {
        System.out.println("\n=== TESTE: Tamanho da Serialização Atual (String) ===");
        System.out.println("TODO: Criar cenário com diferentes quantidades de eventos");
        System.out.println("      Medir bytes consumidos pela String atual");
        System.out.println("      Formato atual: produto|count|qtd:preco,qtd:preco||produto2|...");
        
        // Exemplo de implementação:
        // Map<String, List<Event>> eventos = createTestData(1000, 10);
        // String serialized = serializeCurrentFormat(eventos);
        // int bytes = serialized.getBytes("UTF-8").length;
        // System.out.println("Tamanho: " + bytes + " bytes para 1000 eventos");
    }
    
    // TODO: Implementar serialização binária otimizada (proposta)
    public static void testBinarySerializationSize() {
        System.out.println("\n=== TESTE: Tamanho da Serialização Binária Otimizada ===");
        System.out.println("TODO: Implementar serialização binária conforme proposta acima");
        System.out.println("      Usar DataOutputStream para formato compacto");
        System.out.println("      Medir bytes consumidos");
        System.out.println("      Calcular % de redução vs formato String");
        
        // Exemplo:
        // Map<String, List<Event>> eventos = createTestData(1000, 10);
        // byte[] serialized = serializeBinaryFormat(eventos);
        // int bytes = serialized.length;
        // System.out.println("Tamanho: " + bytes + " bytes para 1000 eventos");
        // double reduction = ((currentSize - bytes) / (double)currentSize) * 100;
        // System.out.println("Redução: " + reduction + "%");
    }
    
    // TODO: Implementar comparação de performance
    public static void testSerializationPerformance() {
        System.out.println("\n=== TESTE: Performance de Serialização ===");
        System.out.println("TODO: Medir tempo de serialização para ambos os formatos");
        System.out.println("      Testar com 10, 100, 1000, 10000 eventos");
        System.out.println("      Calcular ops/segundo para cada abordagem");
        
        int[] eventCounts = {10, 100, 1000, 10000};
        
        for (int count : eventCounts) {
            System.out.println("\n--- " + count + " eventos ---");
            // TODO: Benchmark String serialization
            // TODO: Benchmark Binary serialization
            // TODO: Comparar tempos
        }
    }
    
    // TODO: Criar dados de teste realistas
    private static Map<String, List<Event>> createTestData(int totalEvents, int numProducts) {
        System.out.println("TODO: Gerar dados de teste com distribuição realista");
        System.out.println("      " + totalEvents + " eventos distribuídos por " + numProducts + " produtos");
        System.out.println("      Simular nomes de produtos reais (ex: 'Produto_A', 'Produto_B', ...)");
        
        // Implementação sugerida:
        // Map<String, List<Event>> data = new HashMap<>();
        // String[] produtos = gerarNomesProdutos(numProducts);
        // Random rand = new Random();
        // for (int i = 0; i < totalEvents; i++) {
        //     String produto = produtos[rand.nextInt(numProducts)];
        //     int qtd = 1 + rand.nextInt(100);
        //     float preco = 1.0f + rand.nextFloat() * 999.0f;
        //     data.computeIfAbsent(produto, k -> new ArrayList<>()).add(new Event(produto, qtd, preco));
        // }
        // return data;
        
        return null; // TODO: Implementar
    }
    
    // TODO: Serializar no formato String atual (para comparação)
    private static String serializeCurrentFormat(Map<String, List<Event>> eventos) {
        System.out.println("TODO: Replicar lógica de ClientHandler.handleFilterEvents()");
        System.out.println("      Formato: produto|count|qtd:preco,qtd:preco||produto2|...");
        
        // StringBuilder response = new StringBuilder();
        // boolean first = true;
        // for (Map.Entry<String, List<Event>> entry : eventos.entrySet()) {
        //     if (!first) response.append("||");
        //     first = false;
        //     String produto = entry.getKey();
        //     List<Event> eventList = entry.getValue();
        //     response.append(produto).append("|").append(eventList.size()).append("|");
        //     boolean firstEvento = true;
        //     for (Event e : eventList) {
        //         if (!firstEvento) response.append(",");
        //         firstEvento = false;
        //         response.append(e.getQuantidade()).append(":").append(e.getPreco());
        //     }
        // }
        // return response.toString();
        
        return null; // TODO: Implementar
    }
    
    // TODO: Serializar em formato binário otimizado
    private static byte[] serializeBinaryFormat(Map<String, List<Event>> eventos) throws IOException {
        System.out.println("TODO: Implementar serialização binária compacta");
        System.out.println("      Usar DataOutputStream");
        System.out.println("      Formato: [numProdutos][produto1_len][produto1_name][numEventos][eventos...]");
        
        // ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // DataOutputStream dos = new DataOutputStream(baos);
        // 
        // dos.writeByte(eventos.size()); // Número de produtos
        // 
        // for (Map.Entry<String, List<Event>> entry : eventos.entrySet()) {
        //     String produto = entry.getKey();
        //     List<Event> eventList = entry.getValue();
        //     
        //     // Escrever nome do produto
        //     byte[] produtoBytes = produto.getBytes("UTF-8");
        //     dos.writeByte(produtoBytes.length);
        //     dos.write(produtoBytes);
        //     
        //     // Escrever número de eventos
        //     dos.writeShort(eventList.size());
        //     
        //     // Escrever cada evento
        //     for (Event e : eventList) {
        //         dos.writeInt(e.getQuantidade());
        //         dos.writeFloat(e.getPreco());
        //     }
        // }
        // 
        // dos.flush();
        // return baos.toByteArray();
        
        return null; // TODO: Implementar
    }
    
    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("  TESTE DE EFICIÊNCIA DA SERIALIZAÇÃO");
        System.out.println("=====================================================");
        System.out.println("\nOBJETIVO: Validar se serialização atual é eficiente");
        System.out.println("          conforme requisito do enunciado (Secção 4)");
        System.out.println("\nPASSOS:");
        System.out.println("  1. Implementar todos os TODOs acima");
        System.out.println("  2. Executar testes e coletar dados");
        System.out.println("  3. Decidir se otimização é necessária");
        System.out.println("  4. Documentar decisão e resultados no RELATÓRIO");
        System.out.println("\nCRITÉRIO: Redução > 40% e tamanho médio > 1KB");
        System.out.println("          → Implementar serialização binária");
        System.out.println("          Caso contrário → Manter formato atual\n");
        
        try {
            // Descomentar à medida que implementa
            // testCurrentSerializationSize();
            // testBinarySerializationSize();
            // testSerializationPerformance();
            
            System.out.println("\n=====================================================");
            System.out.println("  TESTES CONCLUÍDOS - Verificar resultados!");
            System.out.println("=====================================================");
            
        } catch (Exception e) {
            System.err.println("Erro durante testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
