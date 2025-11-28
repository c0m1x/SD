package uminho.grupo57;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable
{
    private final ReentrantLock ls = new ReentrantLock();
    private final ReentrantLock lr = new ReentrantLock();
    private final DataOutputStream output;
    private final DataInputStream input;

    public static class Frame
    {
        public final int tag;
        public final byte[] data;
        public Frame(int tag, byte[] data)
        {
            this.tag = tag; this.data = data;
        }
    }

    public TaggedConnection(Socket socket) throws IOException
    {
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    public void send(int tag, byte[] data) throws IOException
    {
        ls.lock();
        try
        {
            output.writeInt(4 + data.length);
            output.writeInt(tag);
            output.write(data);
            output.flush();
        }finally{
            ls.unlock();
        }

    }

    public void send(Frame frame) throws IOException
    {
        send(frame.tag, frame.data);
    }

    public Frame receive() throws IOException
    {
        lr.lock();
        try
        {
            int tam = input.readInt();
            int tag = input.readInt();
            byte[] mensagem = new byte[tam - 4];
            input.readFully(mensagem);
            return new Frame(tag, mensagem);
        }finally{
            lr.unlock();
        }
    }

    public void close() throws IOException
    {
        output.close();
        input.close();
    }
}
