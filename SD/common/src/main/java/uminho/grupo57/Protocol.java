package uminho.grupo57;

import java.io.*;

/**
 * Protocolo binário de comunicação cliente-servidor
 * Usa DataInputStream/DataOutputStream
 *
 * Formato de mensagem:
 * - byte: tipo de comando
 * - int: número de argumentos
 * - Para cada argumento:
 *   - int: tamanho do argumento
 *   - bytes: dados do argumento
 */
public class Protocol {

    // Tipos de mensagem (comandos)
    public static final byte REGISTER = 1;
    public static final byte LOGIN = 2;
    public static final byte ADD_EVENT = 3;
    public static final byte QUERY_PRODUCT = 4;
    public static final byte LIST_PRODUCTS = 5;
    public static final byte NEXT_DAY = 6;
    public static final byte LOGOUT = 7;
    public static final byte AGGREGATE_RANGE = 8;
    public static final byte FILTER_EVENTS = 9;
    public static final byte WAIT_SIMULTANEOUS = 10;
    public static final byte WAIT_CONSECUTIVE = 11;

    // Respostas
    public static final byte OK = 100;
    public static final byte ERROR = 101;
    public static final byte UNAUTHORIZED = 102;

    /**
     * Representa uma mensagem do protocolo
     */
    public static class Message {
        public final byte type;
        public final String[] args;

        public Message(byte type, String... args) {
            this.type = type;
            this.args = args != null ? args : new String[0];
        }

        /**
         * Serializa mensagem para DataOutputStream
         */
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeByte(type);
            out.writeInt(args.length);

            for(String arg : args)
            {
                if(arg == null)
                    out.writeInt(0);
                else
                    out.writeUTF(arg);
            }
            out.flush();
        }

        /**
         * Deserializa mensagem de DataInputStream
         */
        public static Message readFrom(DataInputStream in) throws IOException {
            byte type = in.readByte();
            int numArgs = in.readInt();

            String[] args = new String[numArgs];
            for (int i = 0; i < numArgs; i++) {
                args[i] = in.readUTF();
            }

            return new Message(type, args);
        }

        @Override
        public String toString() {
            return "Message{type=" + type + ", args=" + java.util.Arrays.toString(args) + "}";
        }
    }

    /**
     * Envia mensagem binária
     */
    public static void sendMessage(DataOutputStream out, Message msg) throws IOException {
        msg.writeTo(out);
    }

    /**
     * Recebe mensagem binária (bloqueante)
     */
    public static Message receiveMessage(DataInputStream in) throws IOException {
        return Message.readFrom(in);
    }

    /**
     * Cria mensagem de resposta OK
     */
    public static Message ok(String... args) {
        return new Message(OK, args);
    }

    /**
     * Cria mensagem de erro
     */
    public static Message error(String message) {
        return new Message(ERROR, message);
    }

    /**
     * Cria mensagem de não autorizado
     */
    public static Message unauthorized(String message) {
        return new Message(UNAUTHORIZED, message);
    }
}