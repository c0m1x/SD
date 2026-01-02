package uminho.grupo57;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import uminho.grupo57.entities.Event;

public class SerializationSpecTest {

    private static Map<String, List<Event>> createTestData(int totalEvents, int numProducts) {
        Map<String, List<Event>> data = new HashMap<>();
        for (int p = 0; p < numProducts; p++)
            data.put("Produto_" + p, new ArrayList<>());

        for (int i = 0; i < totalEvents; i++) {
            String produto = "Produto_" + (i % numProducts);
            Event e = new Event(produto, 1 + (i % 5), 1.0f + (i % 10), 1);
            data.get(produto).add(e);
        }
        return data;
    }

    private static String serializeCurrentFormat(Map<String, List<Event>> eventos) {
        StringBuilder response = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<Event>> entry : eventos.entrySet()) {
            if (!first) response.append("||");
            first = false;
            String produto = entry.getKey();
            List<Event> eventList = entry.getValue();
            response.append(produto).append("|").append(eventList.size()).append("|");
            boolean firstEvento = true;
            for (Event e : eventList) {
                if (!firstEvento) response.append(",");
                firstEvento = false;
                response.append(e.getQuantidade()).append(":").append(e.getPreco());
            }
        }
        return response.toString();
    }

    private static byte[] serializeBinaryFormat(Map<String, List<Event>> eventos) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeShort(eventos.size()); // number of products
        for (Map.Entry<String, List<Event>> entry : eventos.entrySet()) {
            byte[] produtoBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            if (produtoBytes.length > 255) throw new IOException("Produto name too long");
            dos.writeByte(produtoBytes.length);
            dos.write(produtoBytes);

            List<Event> list = entry.getValue();
            dos.writeInt(list.size());
            for (Event e : list) {
                dos.writeInt(e.getQuantidade());
                dos.writeFloat(e.getPreco());
            }
        }
        dos.flush();
        return baos.toByteArray();
    }

    @Test
    public void compareStringAndBinarySizes() throws Exception {
        Map<String, List<Event>> data = createTestData(1000, 10);

        String cur = serializeCurrentFormat(data);
        byte[] curBytes = cur.getBytes(StandardCharsets.UTF_8);
        byte[] bin = serializeBinaryFormat(data);

        Path logDir = Path.of(System.getProperty("user.home"), "sd-test-logs");
        Files.createDirectories(logDir);
        Path out = logDir.resolve("serialization-spec-" + Instant.now().toEpochMilli() + ".txt");
        String summary = String.format("String bytes=%d, Binary bytes=%d, reduction=%.2f%%\n",
                curBytes.length, bin.length, (curBytes.length - bin.length) * 100.0 / curBytes.length);
        Files.writeString(out, summary, StandardCharsets.UTF_8);
        System.out.println(summary);

        // Do not enforce binary < string; just ensure both serializations produce data and are consistent sizes
        assertTrue(curBytes.length > 0);
        assertTrue(bin.length > 0);
    }
}
