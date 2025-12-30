package uminho.grupo57.clientHandling;

import uminho.grupo57.ThreadPool;
import uminho.grupo57.Protocol;
import uminho.grupo57.TaggedConnection;
import uminho.grupo57.storage.SeriesPersistence;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.Arrays.copyOfRange;

/**
 * Handler que trata um cliente em thread separada
 * Implementa protocolo binário de comunicação request-response COM TAGS
 */
public class ClientHandler implements Runnable {
    
    private final Socket socket;
    private final AutenticathionManager authManager;
    private final TimeSeriesManager tsManager;
    private final SeriesPersistence persistence;
    private final ThreadPool threadPool;
    private final ThreadPool diskWriters;
    private ReentrantLock lock = new ReentrantLock();
    private String currentUser = null;
    
    public ClientHandler(Socket socket, AutenticathionManager authManager, TimeSeriesManager tsManager, ThreadPool threadPool, ThreadPool diskWriters, SeriesPersistence persistence)
    {
        this.socket = socket;
        this.authManager = authManager;
        this.tsManager = tsManager;
        this.threadPool = threadPool;
        this.diskWriters = diskWriters;
        this.persistence = persistence;
    }

    private void changeCurrentUser(String user)
    {
        lock.lock();
        try{
            if(user == null)
                currentUser = null;
            currentUser = user;
        }finally{
            lock.unlock();
        }
    }


    private String getCurrentUser()
    {
        lock.lock();
        try{
            return currentUser;
        }finally{
            lock.unlock();
        }
    }


    public void run()
    {
        TaggedConnection connection = null;
        try
        {
            connection = new TaggedConnection(socket);
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente conectado");

            while(true)
            {
                TaggedConnection.Frame frame = connection.receive();
                threadPool.submitTask(new TaskHandler(frame.tag, frame.data, connection));
            }

        }catch (EOFException e){
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Cliente desconectou");
        }catch (IOException | InterruptedException e){
            System.err.println("[" + socket.getRemoteSocketAddress() + "] Erro: " + e.getMessage());
        }finally{
            try{
                if(connection != null)
                    connection.close();
                socket.close();
            }catch (IOException ignored) {}
            System.out.println("[" + socket.getRemoteSocketAddress() + "] Desconectado");
        }
    }


    private class TaskHandler implements Runnable
    {
        private final Protocol.Message message;
        private final int tag;
        private final TaggedConnection connection;

        public TaskHandler(int tag, Protocol.Message message, TaggedConnection conn)
        {
            this.tag = tag;
            this.message = message;
            this.connection = conn;
        }

        public void run()
        {
            Skeleton skeleton = new Skeleton(authManager, tsManager, diskWriters);
            Protocol.Message reply;

            try{
                reply = dispatchMessage(skeleton, message);
            }catch (IOException | InterruptedException e){
                try{
                    Thread.currentThread().join();
                }catch (InterruptedException ex){
                    reply = Protocol.error("Interrompido");
                }
                reply = Protocol.error("Interrompido");
            }

            try{
                connection.send(tag, reply);
            }catch (IOException e){
                throw new RuntimeException(e);
            }
        }

        private Protocol.Message dispatchMessage(Skeleton skeleton, Protocol.Message msg) throws InterruptedException, IOException {
            switch (msg.type)
            {
                case Protocol.REGISTER:
                    if(msg.args.length != 2)
                        return Protocol.error("Uso: REGISTER username password");
                    return skeleton.register(msg.args[0], msg.args[1]);

                case Protocol.LOGIN:
                    if(msg.args.length != 2)
                        return Protocol.error("Uso: LOGIN username password");

                    Protocol.Message res = skeleton.login(msg.args[0], msg.args[1]);
                    if(res.type == Protocol.OK)
                        changeCurrentUser(msg.args[0]);
                    return res;

                case Protocol.ADD_EVENT:
                    if(msg.args.length != 3)
                        return Protocol.error("Uso: ADD_EVENT produto quantidade preco");

                    System.out.println("A adicionar evento...");
                    return skeleton.addEvent
                            (
                                getCurrentUser(),
                                msg.args[0],
                                Integer.parseInt(msg.args[1]),
                                Float.parseFloat(msg.args[2])
                            );

                case Protocol.QUERY_PRODUCT:
                    if(msg.args.length != 1)
                        return Protocol.error("Uso: QUERY_PRODUCT produto");
                    System.out.println("A recolher estatisticas de um produto...");
                    return skeleton.queryProduct(getCurrentUser(), msg.args[0]);

                case Protocol.LIST_PRODUCTS:
                    System.out.println("A listar produtos...");
                    return skeleton.listProducts(getCurrentUser());

                case Protocol.NEXT_DAY:
                    return skeleton.nextDay(getCurrentUser());

                case Protocol.AGGREGATE_RANGE:
                    if(msg.args.length != 2)
                        return Protocol.error("Uso: AGGREGATE_RANGE produto numeroDias");
                    System.out.println("A recolhas estatisticas de agregação num periodo...");
                    return skeleton.aggregateRange(getCurrentUser(), msg.args[0], Integer.parseInt(msg.args[1]));

                case Protocol.FILTER_EVENTS:
                    if(msg.args.length < 2)
                        return Protocol.error("Uso: FILTER_EVENTS dia produto1 [produto2 ...]");

                    System.out.println("A filtrar evento...");
                    int dia = Integer.parseInt(msg.args[0]);
                    Set<String> produtos = Arrays.stream(copyOfRange(msg.args, 1, msg.args.length)).collect(Collectors.toSet());
                    return skeleton.filterEvents(getCurrentUser(), dia, produtos);

                case Protocol.WAIT_SIMULTANEOUS:
                    if(msg.args.length != 2)
                        return Protocol.error("Uso: WAIT_SIMULTANEOUS produto1 produto2");
                    System.out.println("A esperar por eventos simultaneos...");
                    return skeleton.waitSimultaneous(getCurrentUser(), msg.args[0], msg.args[1]);

                case Protocol.WAIT_CONSECUTIVE:
                    if(msg.args.length != 2)
                        return Protocol.error("Uso: WAIT_CONSECUTIVE produto n");
                    System.out.println("A esperar por eventos consecutivos...");
                    return skeleton.waitConsecutive(getCurrentUser(), msg.args[0], Integer.parseInt(msg.args[1]));

                case Protocol.LOGOUT:
                    changeCurrentUser(null);
                    return Protocol.ok("Logout efetuado");

                default:
                    return Protocol.error("Comando desconhecido");
            }
        }
    }
}