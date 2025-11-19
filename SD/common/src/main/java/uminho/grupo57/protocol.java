package uminho.grupo57;

import java.io.*;

/**
 * Protocolo de comunicação cliente-servidor
 * Formato: TYPE|arg1|arg2|...
 */
public class protocol {
    
    // Tipos de mensagem
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String ADD_EVENT = "ADD_EVENT";
    public static final String QUERY_PRODUCT = "QUERY_PRODUCT";
    public static final String LIST_PRODUCTS = "LIST_PRODUCTS";
    public static final String NEXT_DAY = "NEXT_DAY"; 
    public static final String LOGOUT = "LOGOUT";
    
    // Respostas
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    
    /**
     * Representa uma mensagem do protocolo
     */
    public static class Message {
        public final String type;
        public final String[] args;
        
        public Message(String type, String... args) {
            this.type = type;
            this.args = args;
        }
        
        /**
         * Serializa mensagem para string no formato: TYPE|arg1|arg2|...
         */
        public String serialize() {
            if (args.length == 0) {
                return type;
            }
            return type + "|" + String.join("|", args);
        }
        
        /**
         * Deserializa string para Message
         */
        public static Message parse(String line) {
            if (line == null || line.isEmpty()) {
                return null;
            }
            
            String[] parts = line.split("\\|");
            String type = parts[0];
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            
            return new Message(type, args);
        }
    }
    
    /**
     * Envia mensagem
     */
    public static void sendMessage(BufferedWriter out, Message msg) throws IOException {
        out.write(msg.serialize());
        out.newLine();
        out.flush();
    }
    
    /**
     * Recebe mensagem (bloqueante)
     */
    public static Message receiveMessage(BufferedReader in) throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        return Message.parse(line);
    }
}