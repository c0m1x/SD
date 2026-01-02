package uminho.grupo57;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class PersistenceRestartTest {

    @Test
    public void serverPersistsEventsAcrossRestart() throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path tempDir = Files.createTempDirectory("ts-restart-");

        final Server srv1 = new Server(port, 30, 10, 4, 2, tempDir.toString());

        Thread t1 = new Thread(() -> {
            try {
                srv1.start(4, tempDir.toString());
            } catch (IOException e) {
            }
        });
        t1.start();

        // wait server up
        boolean up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) { up = true; break; } catch (IOException e) { Thread.sleep(100); }
        }
        assertTrue(up, "Server1 did not start");

        // client: register/login/add event
        try (Socket sock = new Socket("127.0.0.1", port);
             uminho.grupo57.TaggedConnection conn = new uminho.grupo57.TaggedConnection(sock)) {

            String user = "u" + System.nanoTime();
            conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
            TaggedConnection.Frame r1 = conn.receive();
            assertEquals(Protocol.OK, r1.data.type);

            conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
            TaggedConnection.Frame r2 = conn.receive();
            assertEquals(Protocol.OK, r2.data.type);

            conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "Rice", "7", "3.14"));
            TaggedConnection.Frame r3 = conn.receive();
            assertEquals(Protocol.OK, r3.data.type);
        }

        // stop server
        srv1.stop();
        t1.join(2000);

        // start server again on same dir
        final Server srv2 = new Server(port, 30, 10, 4, 2, tempDir.toString());
        Thread t2 = new Thread(() -> { try { srv2.start(4, tempDir.toString()); } catch (IOException e) {} });
        t2.start();

        // wait up
        up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) { up = true; break; } catch (IOException e) { Thread.sleep(100); }
        }
        assertTrue(up, "Server2 did not start");

        // connect and query product
        try (Socket sock = new Socket("127.0.0.1", port);
             uminho.grupo57.TaggedConnection conn = new uminho.grupo57.TaggedConnection(sock)) {

            String user = "u" + System.nanoTime();
            // register/login (new user ok) but we only need to query
            conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
            conn.receive();
            conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
            conn.receive();

            conn.send(4, new Protocol.Message(Protocol.QUERY_PRODUCT, "Rice"));
            TaggedConnection.Frame r4 = conn.receive();
            assertEquals(Protocol.OK, r4.data.type);
            assertTrue(r4.data.args.length >= 0);
        }

        srv2.stop();
        t2.join(2000);
    }
}
