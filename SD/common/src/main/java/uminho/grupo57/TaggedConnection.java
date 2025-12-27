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
        public final Protocol.Message data;
        public Frame(int tag, Protocol.Message data)
        {
            this.tag = tag; this.data = data;
        }
    }

    public TaggedConnection(Socket socket) throws IOException
    {
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    public void send(int tag, Protocol.Message data) throws IOException
    {
        ls.lock();
        try
        {
            output.writeInt(tag);
            Protocol.sendMessage(output, data);
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
            int tag = input.readInt();
            Protocol.Message mensagem = Protocol.receiveMessage(input);
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