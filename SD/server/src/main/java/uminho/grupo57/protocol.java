package uminho.grupo57;

import java.io.*;

/**
 * Protocolo de comunicação entre cliente e servidor
 * Mensagens em formato texto simples com delimitador
 */
public class protocol {
    
    // Tipos de mensagens
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String ADD_EVENT = "ADD_EVENT";
    public static final String QUERY_PRODUCT = "QUERY_PRODUCT";
    public static final String LIST_PRODUCTS = "LIST_PRODUCTS";
    public static final String LOGOUT = "LOGOUT";
    
    // Respostas
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    
    /**
     * Mensagem genérica do protocolo
     */
    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final String type;
        public final String[] args;
        
        public Message(String type, String... args) {
            this.type = type;
            this.args = args;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(type);
            for (String arg : args) {
                sb.append("|").append(arg);
            }
            return sb.toString();
        }
        
        public static Message parse(String line) {
            if (line == null || line.isEmpty()) return null;
            
            String[] parts = line.split("\\|", -1);
            if (parts.length == 0) return null;
            
            String type = parts[0];
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            
            return new Message(type, args);
        }
    }
    
    /**
     * Envia mensagem via socket
     */
    public static void sendMessage(BufferedWriter out, Message msg) throws IOException {
        out.write(msg.toString());
        out.newLine();
        out.flush();
    }
    
    /**
     * Recebe mensagem do socket
     */
    public static Message receiveMessage(BufferedReader in) throws IOException {
        String line = in.readLine();
        if (line == null) return null;
        return Message.parse(line);
    }
}
