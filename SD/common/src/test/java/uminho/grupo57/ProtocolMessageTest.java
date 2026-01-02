package uminho.grupo57;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class ProtocolMessageTest {

    @Test
    public void roundTripMessageSerialization() throws IOException {
        Protocol.Message msg = new Protocol.Message(Protocol.OK, "abc", "123", "Olá");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        msg.writeTo(out);

        byte[] bytes = baos.toByteArray();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        Protocol.Message read = Protocol.Message.readFrom(in);

        assertEquals(msg.type, read.type);
        assertArrayEquals(msg.args, read.args);
    }
}
