package uminho.grupo57;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.storage.SeriesPersistence;

public class NextDayPersistenceTest {

    @Test
    public void nextDayPersistsDayDataToDisk() throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        Path tempDir = Files.createTempDirectory("ts-nextday-");

        final Server srv = new Server(port, 30, 10, 4, 2, tempDir.toString());

        Thread serverThread = new Thread(() -> {
            try {
                srv.start(4, tempDir.toString());
            } catch (IOException e) {
                // ignore
            }
        });
        serverThread.start();

        // wait server up
        boolean up = false;
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) { up = true; break; } catch (IOException e) { Thread.sleep(100); }
        }
        assertTrue(up, "Servidor não arrancou a tempo");

        // 1) as a normal user: register, login, add event
        try (Socket sock = new Socket("127.0.0.1", port);
             TaggedConnection conn = new TaggedConnection(sock)) {

            String user = "u" + System.nanoTime();
            conn.send(1, new Protocol.Message(Protocol.REGISTER, user, "p"));
            TaggedConnection.Frame f1 = conn.receive();
            assertEquals(Protocol.OK, f1.data.type);

            conn.send(2, new Protocol.Message(Protocol.LOGIN, user, "p"));
            TaggedConnection.Frame f2 = conn.receive();
            assertEquals(Protocol.OK, f2.data.type);

            conn.send(3, new Protocol.Message(Protocol.ADD_EVENT, "Arroz", "2", "1.5"));
            TaggedConnection.Frame f3 = conn.receive();
            assertEquals(Protocol.OK, f3.data.type);
        }

        // give background disk writer time to persist
        Thread.sleep(300);

        // 2) login as admin and advance day
        try (Socket adminSock = new Socket("127.0.0.1", port);
             TaggedConnection adminConn = new TaggedConnection(adminSock)) {

            adminConn.send(1, new Protocol.Message(Protocol.LOGIN, "admin", "1234"));
            TaggedConnection.Frame lg = adminConn.receive();
            assertEquals(Protocol.OK, lg.data.type);

            adminConn.send(2, new Protocol.Message(Protocol.NEXT_DAY));
            TaggedConnection.Frame nd = adminConn.receive();
            assertEquals(Protocol.OK, nd.data.type);
        }

        // Allow server to process and then stop for persistence of dia.dat
        Thread.sleep(200);
        try { srv.stop(); } catch (IOException ignored) {}
        serverThread.join(2000);

        // Verify that day directory exists and contains at least one product file
        SeriesPersistence sp = new SeriesPersistence(tempDir.toString());
        boolean existsDay1 = sp.exists(1);
        assertTrue(existsDay1, "Day 1 should exist on disk after NEXT_DAY and persistence");

        // load and check there is at least one event for product hash
        uminho.grupo57.entities.TimeSeries ts = sp.loadDayData(1);
        assertNotNull(ts);
        assertFalse(ts.isEmpty(), "TimeSeries for day 1 should not be empty");

        // cleanup files
        try { Files.walk(tempDir).map(Path::toFile).forEach(f -> f.delete()); } catch (Exception ignored) {}
    }
}
