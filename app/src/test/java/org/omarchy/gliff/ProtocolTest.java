package org.omarchy.gliff;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;
import static org.omarchy.gliff.Protocol.*;

public class ProtocolTest {
    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    @Test public void matchesUpstreamWireGoldens() throws Exception {
        // omacom/gliff 32bb0622, crates/gliff-proto/src/msg.rs::encodings_are_stable.
        assertArrayEquals(hex("82008301616b858101190f00190870820102816166"),
                encode(array(0, array(1, "k", array(array(1), 3840, 2160, array(1, 2), array("f"))))));
        assertArrayEquals(hex("820282181ef5"), encode(array(2, array(30, true))));
        assertArrayEquals(hex("820382fb4029000000000000fb401d000000000000"), encode(array(3, array(12.5, 7.25))));
        assertArrayEquals(hex("820682182a1903e8"), encode(array(6, array(42, 1000))));
        assertArrayEquals(hex("820780"), encode(array(7, array())));
        Object[] config = list(decode(hex("82018b0101001907801904381903e840f6190780190438183c")));
        assertEquals(1, integer(config, 0));
        assertEquals(1920, integer(list(config[1]), 3));
        assertArrayEquals(new byte[0], (byte[]) list(config[1])[6]);
    }

    @Test public void preservesFramingAndUnknownMessages() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        send(out, 999, array("future", array(1, null, false)), new byte[]{1, 2, 3});
        send(out, 2, array(30, true), new byte[0]);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Frame future = receive(in);
        assertEquals(999, future.type);
        assertArrayEquals(new byte[]{1, 2, 3}, future.payload);
        assertEquals(30, integer(receive(in).fields, 0));
        assertEquals(0, in.available());
    }

    @Test public void rejectsInvalidLengthBeforeAllocatingPayload() {
        for (int body : new int[]{-1, 0, MAX_BODY + 1}) {
            byte[] header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(body).putInt(0).array();
            assertThrows(IOException.class, () -> receive(new ByteArrayInputStream(header)));
        }
        byte[] header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(MAX_PAYLOAD + 1).array();
        assertThrows(IOException.class, () -> receive(new ByteArrayInputStream(header)));
    }

    @Test public void rejectsMalformedCborAndTruncatedFrames() throws Exception {
        assertThrows(IOException.class, () -> decode(hex("8202")));
        assertThrows(IOException.class, () -> decode(hex("0102")));
        byte[] deep = new byte[70];
        java.util.Arrays.fill(deep, (byte) 0x81);
        assertThrows(IOException.class, () -> decode(deep));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        send(out, 2, array(30, true), new byte[]{1});
        byte[] truncated = java.util.Arrays.copyOf(out.toByteArray(), out.size() - 1);
        assertThrows(IOException.class, () -> receive(new ByteArrayInputStream(truncated)));
    }

    @Test public void acceptsFutureFieldsAndIndefiniteArrays() throws Exception {
        Object[] msg = list(decode(hex("9f029f181ef501ffff")));
        assertEquals(2, integer(msg, 0));
        assertEquals(3, list(msg[1]).length);
    }
}
