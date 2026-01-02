package uminho.grupo57;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ServerIntegrationTest {

    @Test
    public void basicRegisterLoginAddQueryFlow() throws Exception {
        // escolher porta livre
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path tempDir = Files.createTempDirectory("ts-integ-");

        final Server srv = new Server(port, 30, 10, 4, 2, tempDir.toString());

        Thread serverThread = new Thread(() -> {
            try {
                srv.start(4, tempDir.toString());
            } catch (IOException e) {
                // servidor pode ser parado durante o teste
            }
        });
        serverThread.start();

        // aguardar servidor aceitar ligações (tentativas)
        boolean up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket sock = new Socket("127.0.0.1", port)) {
                up = true;
                break;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        assertTrue(up, "Servidor não arrancou a tempo");

        Socket client = new Socket("127.0.0.1", port);
        try (uminho.grupo57.TaggedConnection conn = new uminho.grupo57.TaggedConnection(client)) {
            String user = "testuser" + System.nanoTime();

            // REGISTER
            conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "pass"));
            TaggedConnection.Frame r1 = conn.receive();
            assertEquals(1, r1.tag);
            assertEquals(Protocol.OK, r1.data.type);

            // LOGIN
            conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "pass"));
            TaggedConnection.Frame r2 = conn.receive();
            assertEquals(2, r2.tag);
            assertEquals(Protocol.OK, r2.data.type);

            // ADD_EVENT
            conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "Arroz", "2", "1.5"));
            TaggedConnection.Frame r3 = conn.receive();
            assertEquals(3, r3.tag);
            assertEquals(Protocol.OK, r3.data.type);

            // QUERY_PRODUCT
            conn.send(4, new Protocol.Message(Protocol.QUERY_PRODUCT, "Arroz"));
            TaggedConnection.Frame r4 = conn.receive();
            assertEquals(4, r4.tag);
            assertEquals(Protocol.OK, r4.data.type);
            assertTrue(r4.data.args.length >= 1);
        } finally {
            // parar servidor e cleanup
            try {
                srv.stop();
            } catch (IOException ignored) {}
            serverThread.join(2000);
            // apagar diretório temporal
            try { Files.walk(tempDir).map(Path::toFile).forEach(f -> f.delete()); } catch (Exception ignored) {}
        }
    }
}
