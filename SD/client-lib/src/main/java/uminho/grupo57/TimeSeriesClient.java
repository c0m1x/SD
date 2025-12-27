package uminho.grupo57;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cliente API para comunicação com servidor de TimeSeries
 * THREAD-SAFE: Suporta múltiplas threads fazendo pedidos concorrentes
 * Utiliza TaggedConnection e Demultiplexer para demultiplexagem de respostas
 */
public class TimeSeriesClient implements AutoCloseable {
    private TaggedConnection connection;
    private Demultiplexer demux;
    private ReentrantLock lock = new ReentrantLock();
    private int tagGenerator = 0;
    private boolean connected = false;

    // Conecta ao servidor
    public void connect(String host, int port) throws IOException
    {
        Socket socket = new Socket(host, port);
        connection = new TaggedConnection(socket);
        demux = new Demultiplexer(connection);
        demux.start();
        connected = true;
        System.out.println("Conectado a " + host + ":" + port);
    }

    // Desconecta do servidor (AutoCloseable)
    @Override
    public void close() throws IOException, InterruptedException
    {
        if(connected)
        {
            try
            {
                sendRequest(Protocol.LOGOUT);
            } catch (Exception e) {}
            demux.close();
            connected = false;
            System.out.println("Desconectado");
        }
    }

    // Regista novo utilizador no servidor
    public boolean register(String username, String password) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.REGISTER, username, password);

        if(response.type == Protocol.OK)
        {
            System.out.println(response.args[0]);
            return true;
        }else{
            System.err.println(response.args[0]);
            return false;
        }
    }

    // Efetua login no servidor
    public boolean login(String username, String password) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LOGIN, username, password);

        if(response.type == Protocol.OK)
        {
            System.out.println("correct " + response.args[0]);
            return true;
        }else{
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    // Regista um novo evento de compra
    public boolean registarCompra(String produto, int quantidade, float preco) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.ADD_EVENT,
                produto, String.valueOf(quantidade), String.valueOf(preco));

        if(response.type == Protocol.OK)
        {
            System.out.println("Compra registada: " + produto);
            return true;
        }else{
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    // Consulta estatísticas de um produto
    public void consultarProduto(String produto) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.QUERY_PRODUCT, produto);

        if(response.type == Protocol.OK)
        {
            if(response.args.length == 1 && "0".equals(response.args[0]))
            {
                System.out.println("Produto '" + produto + "' não encontrado.");
                return;
            }

            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);

            System.out.println("\nEstatisticas: " + produto);
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preco maximo: %.2f€%n", precoMax);
            System.out.printf("Preco minimo: %.2f€%n", precoMin);
            System.out.printf("Preco medio: %.2f€%n", precoMedio);
        }else{
            System.err.println(response.args[0]);
        }
    }

    // Consulta agregação de um produto nos últimos N dias
    public void consultarAgregacaoRange(String produto, int numeroDias) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.AGGREGATE_RANGE, produto, String.valueOf(numeroDias));

        if(response.type == Protocol.OK)
        {
            if(response.args.length == 1 && "0".equals(response.args[0]))
            {
                System.out.println("Produto '" + produto + "' sem dados nos últimos " + numeroDias + " dias.");
                return;
            }

            String[] parts = response.args[0].split("\\|");
            int qtdTotal = Integer.parseInt(parts[0]);
            float precoTotal = Float.parseFloat(parts[1]);
            float precoMax = Float.parseFloat(parts[2]);
            float precoMin = Float.parseFloat(parts[3]);
            float precoMedio = Float.parseFloat(parts[4]);

            System.out.println("\nEstatisticas " + produto + " (ultimos " + numeroDias + " dias)");
            System.out.println("Quantidade total: " + qtdTotal + " unidades");
            System.out.printf("Valor total gasto: %.2f€%n", precoTotal);
            System.out.printf("Preco maximo: %.2f€%n", precoMax);
            System.out.printf("Preco minimo: %.2f€%n", precoMin);
            System.out.printf("Preco medio: %.2f€%n", precoMedio);
        }else{
            System.err.println("error " + response.args[0]);
        }
    }

    public void filtrarEventos(int dia, String[] produtos) throws IOException, InterruptedException
    {
        checkConnection();
        String[] args = new String[produtos.length + 1];
        args[0] = String.valueOf(dia);
        System.arraycopy(produtos, 0, args, 1, produtos.length);

        Protocol.Message response = sendRequest(Protocol.FILTER_EVENTS, args);

        if (response.type == Protocol.OK)
        {
            if(response.args == null || response.args.length == 0 || response.args[0] == null || response.args[0].isEmpty())
            {
                System.out.println("Nenhum evento encontrado para os produtos especificados.");
                return;
            }

            System.out.println("\nEventos Filtrados:");
            for(String produtoData : response.args[0].split("\\|\\|"))
            {
                String[] parts = produtoData.split("\\|");
                if(parts.length < 2)
                    continue;

                String produto = parts[0];
                System.out.println(produto + ":");

                if(parts.length > 2 && !parts[2].isEmpty())
                {
                    for (String evento : parts[2].split(","))
                    {
                        String[] eventoParts = evento.split(":");
                        if(eventoParts.length == 3)
                            System.out.printf("     %s | %s€ | %s%n", eventoParts[0], eventoParts[1], eventoParts[2]);
                    }
                }
            }
        }else{
            if(response.args != null && response.args.length > 0)
                System.err.println("error " + response.args[0]);
            else
                System.err.println("error: resposta inválida do servidor");
        }
    }


    public boolean aguardarVendasSimultaneas(String produto1, String produto2) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_SIMULTANEOUS, produto1, produto2);

        if (response.type == Protocol.OK)
        {
            boolean result = "true".equals(response.args[0]);
            if(result)
                System.out.println("Vendas simultaneas detectadas: " + produto1 + " e " + produto2);
            else
                System.out.println("Dia avancou sem vendas simultaneas de " + produto1 + " e " + produto2);
            return result;
        }else{
            System.err.println("Erro: " + response.args[0]);
            return false;
        }
    }

    public String aguardarVendasConsecutivas(String produto, int n) throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.WAIT_CONSECUTIVE, produto, String.valueOf(n));

        if(response.type == Protocol.OK)
        {
            String result = response.args[0];
            if(!"null".equals(result))
            {
                System.out.println(n + " vendas consecutivas de " + result + " detectadas!");
                return result;
            }else{
                System.out.println("Dia avancou sem " + n + " vendas consecutivas de " + produto);
                return null;
            }
        }else{
            System.err.println("Erro: " + response.args[0]);
            return null;
        }
    }


    public void listarProdutos() throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.LIST_PRODUCTS);

        if(response.type == Protocol.OK)
        {
            if(response.args.length == 0 || response.args[0].isEmpty())
            {
                System.out.println("Nenhum produto registado.");
                return;
            }

            String[] produtos = response.args[0].split(",");
            System.out.println("\nProdutos Registados");
            for(String p : produtos)
                System.out.println("  " + p);
        }else{
            System.err.println(response.args[0]);
        }
    }

    public boolean nextDay() throws IOException, InterruptedException
    {
        checkConnection();
        Protocol.Message response = sendRequest(Protocol.NEXT_DAY);

        if (response.type == Protocol.OK) {
            System.out.println("correct " + response.args[0]);
            return true;
        } else {
            System.err.println("error " + response.args[0]);
            return false;
        }
    }

    public boolean isConnected()
    {
        return connected;
    }

    private void checkConnection() throws IOException
    {
        if (!connected)
            throw new IOException("Não conectado ao servidor");
    }

    private Protocol.Message sendRequest(byte type, String... args) throws IOException, InterruptedException
    {
        int tag;
        lock.lock();
        try{
            tagGenerator++;
            tag = tagGenerator;
        }finally{
            lock.unlock();
        }

        Protocol.Message mensagem = new Protocol.Message(type, args);
        demux.send(tag, mensagem);
        return demux.receive(tag);
    }

    private class RegisterHandler extends Thread
    {
        private String username;
        private String password;

        public RegisterHandler(String username, String password)
        {
            this.username = username;
            this.password = password;
        }

        public void run()
        {
            try{
                register(username, password);
            }catch (Exception e){
                System.err.println("Erro no register: " + e.getMessage());
            }
        }
    }

    private class LoginHandler extends Thread
    {
        private String username;
        private String password;

        public LoginHandler(String username, String password)
        {
            this.username = username;
            this.password = password;
        }

        public void run()
        {
            try{
                login(username, password);
            }catch (Exception e){
                System.err.println("Erro no login: " + e.getMessage());
            }
        }
    }

    private class RegistarCompraHandler extends Thread
    {
        private String produto;
        private int quantidade;
        private float preco;

        public RegistarCompraHandler(String produto, int quantidade, float preco)
        {
            this.produto = produto;
            this.quantidade = quantidade;
            this.preco = preco;
        }

        public void run()
        {
            try{
                registarCompra(produto, quantidade, preco);
            }catch (Exception e){
                System.err.println("Erro ao registar compra: " + e.getMessage());
            }
        }
    }

    private class ConsultarProdutoHandler extends Thread
    {
        private String produto;
        public ConsultarProdutoHandler(String produto) {this.produto = produto;}

        public void run()
        {
            try{
                consultarProduto(produto);
            }catch (Exception e){
                System.err.println("Erro ao consultar produto: " + e.getMessage());
            }
        }
    }

    private class ConsultarAgregacaoRangeHandler extends Thread
    {
        private String produto;
        private int numeroDias;

        public ConsultarAgregacaoRangeHandler(String produto, int numeroDias)
        {
            this.produto = produto;
            this.numeroDias = numeroDias;
        }

        public void run()
        {
            try{
                consultarAgregacaoRange(produto, numeroDias);
            }catch (Exception e){
                System.err.println("Erro ao consultar agregação: " + e.getMessage());
            }
        }
    }

    private class FiltrarEventosHandler extends Thread
    {
        private int dia;
        private String[] produtos;

        public FiltrarEventosHandler(int dia, String[] produtos)
        {
            this.dia = dia;
            this.produtos = produtos;
        }

        public void run()
        {
            try{
                filtrarEventos(dia, produtos);
            }catch (Exception e){
                System.err.println("Erro ao filtrar eventos: " + e.getMessage());
            }
        }
    }

    private class AguardarVendasSimultaneasHandler extends Thread
    {
        private String produto1;
        private String produto2;

        public AguardarVendasSimultaneasHandler(String produto1, String produto2)
        {
            this.produto1 = produto1;
            this.produto2 = produto2;
        }

        public void run()
        {
            try{
                aguardarVendasSimultaneas(produto1, produto2);
            }catch (Exception e){
                System.err.println("Erro ao aguardar vendas simultâneas: " + e.getMessage());
            }
        }
    }

    private class AguardarVendasConsecutivasHandler extends Thread
    {
        private String produto;
        private int n;

        public AguardarVendasConsecutivasHandler(String produto, int n)
        {
            this.produto = produto;
            this.n = n;
        }

        public void run()
        {
            try {
                aguardarVendasConsecutivas(produto, n);
            }catch (Exception e){
                System.err.println("Erro ao aguardar vendas consecutivas: " + e.getMessage());
            }
        }
    }

    private class ListarProdutosHandler extends Thread
    {
        public void run()
        {
            try{
                listarProdutos();
            }catch (Exception e){
                System.err.println("Erro ao listar produtos: " + e.getMessage());
            }
        }
    }

    private class NextDayHandler extends Thread
    {
        public void run()
        {
            try{
                nextDay();
            } catch (Exception e) {
                System.err.println("Erro ao avançar dia: " + e.getMessage());
            }
        }
    }

    public void handleRegister(String username, String password)
    {
        new RegisterHandler(username, password).start();
    }

    public void handleLogin(String username, String password)
    {
        new LoginHandler(username, password).start();
    }

    public void handleRegistarCompra(String produto, int quantidade, float preco)
    {
        new RegistarCompraHandler(produto, quantidade, preco).start();
    }

    public void handleConsultarProduto(String produto)
    {
        new ConsultarProdutoHandler(produto).start();
    }

    public void handleConsultarAgregacaoRange(String produto, int numeroDias)
    {
        new ConsultarAgregacaoRangeHandler(produto, numeroDias).start();
    }

    public void handleFiltrarEventos(int dia, String[] produtos)
    {
        new FiltrarEventosHandler(dia, produtos).start();
    }

    public void handleAguardarVendasSimultaneas(String produto1, String produto2)
    {
        new AguardarVendasSimultaneasHandler(produto1, produto2).start();
    }

    public void handleAguardarVendasConsecutivas(String produto, int n)
    {
        new AguardarVendasConsecutivasHandler(produto, n).start();
    }

    public void handleListarProdutos()
    {
        new ListarProdutosHandler().start();
    }

    public void handleNextDay()
    {
        new NextDayHandler().start();
    }
}