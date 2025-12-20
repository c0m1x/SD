    package uminho.grupo57;

    import java.io.IOException;
    import java.util.*;
    import java.util.concurrent.locks.Condition;
    import java.util.concurrent.locks.ReentrantLock;

    /**
     * Demultiplexer para TaggedConnection
     * Permite múltiplas threads enviarem e receberem mensagens concorrentemente
     * Cada thread lê frames do socket até encontrar o seu tag
     */
    public class Demultiplexer implements AutoCloseable
    {
        private final TaggedConnection connection;
        private final ReentrantLock lockGlobal = new ReentrantLock();
        private final ReentrantLock lockIO = new ReentrantLock();
        private Map<Integer, Entrada> mensagens = new HashMap<>();
        private IOException ioe = null;

        class Entrada
        {
            public Condition condition = lockGlobal.newCondition();
            public Deque<byte[]> mensagens = new ArrayDeque<>();
        }



        public Demultiplexer(TaggedConnection conn)
        {
            this.connection = conn;
        }


        public void close() throws IOException
        {
            lockGlobal.lock();
            try{
                for (Entrada entrada : mensagens.values())
                    entrada.condition.signalAll();
            }finally{
                lockGlobal.unlock();
            }
            connection.close();
        }


        public void send(int tag, byte[] data) throws IOException
        {
            connection.send(tag, data);
        }

        /**
         * Recebe mensagem com o tag especificado
         * Thread fica a ler frames do socket até encontrar o seu tag
         */
        public byte[] receive(int tag) throws IOException, InterruptedException
        {
            Entrada entrada;
            
            // Criar entrada para este tag se não existir
            lockGlobal.lock();
            try {
                entrada = mensagens.get(tag);
                if (entrada == null) {
                    entrada = new Entrada();
                    mensagens.put(tag, entrada);
                }
            } finally {
                lockGlobal.unlock();
            }

            // Loop até conseguir a mensagem com o tag correto
            while (true) {
                // Verificar se já existe mensagem na fila
                lockGlobal.lock();
                try {
                    if (!entrada.mensagens.isEmpty()) {
                        return entrada.mensagens.poll();
                    }
                    
                    // Verificar se houve erro de I/O
                    if (ioe != null) {
                        throw ioe;
                    }
                } finally {
                    lockGlobal.unlock();
                }

                // Ler próximo frame do socket (lock de I/O para serializar leituras)
                TaggedConnection.Frame frame;
                lockIO.lock();
                try {
                    frame = connection.receive();
                } catch (IOException e) {
                    // Propagar erro para todas as threads
                    lockGlobal.lock();
                    try {
                        ioe = e;
                        for (Entrada ent : mensagens.values()) {
                            ent.condition.signalAll();
                        }
                    } finally {
                        lockGlobal.unlock();
                    }
                    throw e;
                } finally {
                    lockIO.unlock();
                }

                // Colocar frame na fila correta
                lockGlobal.lock();
                try {
                    Entrada entradaFrame = mensagens.get(frame.tag);
                    if (entradaFrame == null) {
                        entradaFrame = new Entrada();
                        mensagens.put(frame.tag, entradaFrame);
                    }
                    entradaFrame.mensagens.add(frame.data);
                    entradaFrame.condition.signalAll();

                    // Se é o nosso tag, retornar imediatamente
                    if (frame.tag == tag) {
                        return entrada.mensagens.poll();
                    }
                } finally {
                    lockGlobal.unlock();
                }
            }
        }
    }
