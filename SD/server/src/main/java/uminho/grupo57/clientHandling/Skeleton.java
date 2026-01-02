package uminho.grupo57.clientHandling;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import uminho.grupo57.Protocol;
import uminho.grupo57.ThreadPool;
import uminho.grupo57.entities.Event;
import uminho.grupo57.storage.AggregationCache;

/**
 * Implementa a lógica de negócio do servidor (procedimentos expostos via protocolo).
 * <p>Este "skeleton" é usado pelo `ClientHandler` para despachar comandos.</p>
 */
public class Skeleton
{
    private final AutenticathionManager authManager;
    private final TimeSeriesManager tsManager;
    private final ThreadPool diskWriters;

    public Skeleton(AutenticathionManager authManager, TimeSeriesManager tsManager, ThreadPool diskWriters)
    {
        this.authManager = authManager;
        this.tsManager = tsManager;
        this.diskWriters = diskWriters;
    }

    /**
     * Regista novo utilizador.
     */
    public Protocol.Message register(String username, String password)
    {
        if(username.isEmpty() || password.isEmpty())
            return Protocol.error("Username e password não podem estar vazios");

        if(authManager.register(username, password))
        {
            System.out.println("Novo utilizador: " + username);
            return Protocol.ok("Utilizador registado");
        }
        return Protocol.error("Utilizador já existe");
    }

    /**
     * Autentica utilizador.
     */
    public Protocol.Message login(String username, String password)
    {
        if(authManager.authenticate(username, password))
        {
            System.out.println("Login: " + username);
            return Protocol.ok("Login bem-sucedido");
        }
        return Protocol.error("Credenciais inválidas");
    }



    /**
     * Adiciona evento (valida e agenda persistência em disco).
     */
    public Protocol.Message addEvent(String user, String produto, int quantidade, float preco) throws InterruptedException
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        if(quantidade <= 0 || preco < 0)
            return Protocol.error("Quantidade deve ser positiva e preço não-negativo");

        Event evento = tsManager.addEvent(produto, quantidade, preco);
        diskWriters.submitTask(() ->
        {
            try{
                tsManager.saveEvent(evento, produto);
            }catch (IOException e){
                System.err.println("Erro ao guardar evento no disco: " + e.getMessage());
            }
        });
        return Protocol.ok("Evento registado");
    }

    /**
     * Consulta estatísticas agregadas de um produto no dia atual.
     */
    public Protocol.Message queryProduct(String user, String produto)
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        AggregationCache cache = tsManager.getCacheForToday(produto);
        if(!cache.isCalculated())
            return Protocol.ok("0");

        int qtd = cache.getQuantidadeTotal();
        if(qtd == 0)
            return Protocol.ok("0");

        float vol = cache.getQuantidadeTotal();
        float max = cache.getPrecoMaximo();
        float min = cache.getPrecoMinimo();
        float avg = cache.getPrecoMedio();

        String res = String.format(Locale.US, "%d|%.2f|%.2f|%.2f|%.2f", qtd, vol, max, min, avg);
        return Protocol.ok(res);
    }

    /**
     * Lista produtos do dia atual.
     */
    public Protocol.Message listProducts(String user)
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        Set<String> produtos = tsManager.getAllProdutos();
        return Protocol.ok(String.join(",", produtos));
    }

    /**
     * Avança para o próximo dia (apenas admin).
     */
    public Protocol.Message nextDay(String user)
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        if(!authManager.isAdmin(user))
            return Protocol.error("Apenas o administrador pode avançar o dia");

        tsManager.nextDay();
        return Protocol.ok("Dia atual: " + tsManager.getCurrentDay());
    }


    /**
     * Calcula agregação para um intervalo de dias.
     */
    public Protocol.Message aggregateRange(String user, String produto, int dias)
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        AggregationCache cache = tsManager.getCacheRange(produto, dias);
        if(!cache.isCalculated())
            return Protocol.ok("0");

        int qtd = cache.getQuantidadeTotal();
        if(qtd == 0)
            return Protocol.ok("0");

        float vol = cache.getQuantidadeTotal();
        float max = cache.getPrecoMaximo();
        float min = cache.getPrecoMinimo();
        float avg = cache.getPrecoMedio();

        String res = String.format(Locale.US, "%d|%.2f|%.2f|%.2f|%.2f", qtd, vol, max, min, avg);
        return Protocol.ok(res);
    }



    /**
     * Filtra eventos por produtos num dia específico.
     */
    public Protocol.Message filterEvents(String user, int dia, Set<String> produtos) throws IOException
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        Map<String, List<Event>> map = tsManager.getFilteredProducts(produtos, dia);
        StringBuilder sb = new StringBuilder();
        boolean firstProd = true;

        for(Map.Entry<String, List<Event>> entry : map.entrySet())
        {
            if(!firstProd)
                sb.append("||");
            firstProd = false;

            sb.append(entry.getKey())
              .append("|")
              .append(entry.getValue().size())
              .append("|");

            boolean firstEv = true;
            for(Event e : entry.getValue())
            {
                if(!firstEv)
                    sb.append(",");
                firstEv = false;
                sb.append(e.getQuantidade())
                  .append(":")
                  .append(String.format(Locale.US, "%.2f", e.getPreco()))
                  .append(":Dia ")
                  .append(e.getDia());
            }
        }

        return Protocol.ok(sb.toString());
    }



    /**
     * Waits até dois produtos serem vendidos no mesmo dia.
     */
    public Protocol.Message waitSimultaneous(String user, String p1, String p2) throws InterruptedException
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        boolean ok = tsManager.getNotificationManager().waitSimultaneous(p1, p2);
        return Protocol.ok(Boolean.toString(ok));
    }

    /**
     * Waits até N vendas consecutivas do mesmo produto.
     */
    public Protocol.Message waitConsecutive(String user, String produto, int n) throws InterruptedException
    {
        if(user == null)
            return Protocol.unauthorized("Login necessário");

        boolean ok = tsManager.getNotificationManager().waitConsecutive(produto, n);
        return Protocol.ok(Boolean.toString(ok));
    }
}