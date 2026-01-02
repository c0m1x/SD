package uminho.grupo57;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SocketRobustnessTest {

    @Test
    public void slowClientsDoNotPreventServingNormalClient() throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }

        Path tempDir = Files.createTempDirectory("ts-socket-robust-");

        final Server srv = new Server(port, 30, 10, 4, 2, tempDir.toString());
        Thread st = new Thread(() -> { try { srv.start(4, tempDir.toString()); } catch (IOException e) {} });
        st.start();

        // wait up
        boolean up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) { up = true; break; } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up, "Server did not start");

        // spawn a bunch of slow clients that connect and do not read
        int slow = 15;
        ExecutorService ex = Executors.newFixedThreadPool(slow + 2);
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < slow; i++) {
            ex.submit(() -> {
                try {
                    Socket s = new Socket("127.0.0.1", port);
                    synchronized (sockets) { sockets.add(s); }
                    // keep connection open for a short while
                    Thread.sleep(1500);
                    s.close();
                } catch (Exception ignored) {}
            });
        }

        // Give slow clients a moment to connect
        Thread.sleep(200);

        // Now a normal client should be served promptly
        ex.submit(() -> {
            try (Socket s = new Socket("127.0.0.1", port);
                 uminho.grupo57.TaggedConnection conn = new uminho.grupo57.TaggedConnection(s)) {
                String user = "sr" + System.nanoTime();
                conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
                conn.receive();
                conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
                conn.receive();
                conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "B", "1", "1.0"));
                conn.receive();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ex.shutdown();
        boolean done = ex.awaitTermination(5, TimeUnit.SECONDS);

        // cleanup
        try { srv.stop(); } catch (IOException ignored) {}
        st.join(1000);

        assertTrue(done, "Background clients and normal client did not finish promptly");
    }
}
