package org.omarchy.gliff;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.omarchy.gliff.Protocol.*;

public class TextClipboardTest {
    private static final class Sent {
        int type; Object[] f; byte[] data;
        Sent(int t, Object[] f, byte[] data) { type = t; this.f = f; this.data = data; }
    }
    private final List<Sent> sent = new ArrayList<>();
    private final List<String> received = new ArrayList<>(), warnings = new ArrayList<>();
    private final TextClipboard bridge = new TextClipboard((t, f, d) -> sent.add(new Sent(t, f, d)), received::add, warnings::add);

    @Test public void disabledClipboardNeverSharesText() throws Exception {
        bridge.localChanged("secret", false);
        bridge.handle(new Frame(6, array(2, 1, array(0, array("text/plain"))), new byte[0]));
        assertEquals(1, sent.size()); assertEquals(14, sent.get(0).type);
    }

    @Test public void sensitiveTextWithdrawsPreviousOffer() throws Exception {
        bridge.enable(true); bridge.localChanged("ordinary", false); bridge.localChanged("password", true);
        assertEquals(0, list(sent.get(1).f[1]).length);
        bridge.handle(new Frame(6, array(2, 2, array(0, array("text/plain"))), new byte[0]));
        assertEquals(14, sent.get(2).type);
    }

    @Test public void refusesStaleOffersAndStreamsCurrentText() throws Exception {
        bridge.enable(true); bridge.localChanged("hello", false); bridge.localChanged("world", false);
        bridge.handle(new Frame(6, array(2, 1, array(0, array("text/plain"))), new byte[0]));
        assertEquals(14, sent.get(2).type);
        bridge.handle(new Frame(6, array(4, 2, array(0, array("text/plain"))), new byte[0]));
        assertEquals(10, sent.get(3).type);
        assertArrayEquals("world".getBytes(java.nio.charset.StandardCharsets.UTF_8), sent.get(3).data);
        assertEquals(true, sent.get(3).f[3]);
    }

    @Test public void receivesChunksWithAckAndDoesNotEchoClipboard() throws Exception {
        bridge.enable(true);
        bridge.handle(new Frame(5, array(40, array("text/plain"), array()), new byte[0]));
        assertEquals(9, sent.get(0).type);
        bridge.handle(new Frame(7, array(1, 0, 2, false), new byte[]{'h', 'i'}));
        bridge.handle(new Frame(7, array(1, 2, 1, true), new byte[]{'!'}));
        assertEquals(java.util.Arrays.asList("hi!"), received);
        assertEquals(13, sent.get(2).type);
        int count = sent.size(); bridge.localChanged("hi!", false);
        assertEquals(count, sent.size());
    }

    @Test public void rejectsOutOfOrderChunkAndCancelsReplacedSelection() throws Exception {
        bridge.enable(true);
        bridge.handle(new Frame(5, array(40, array("text/plain"), array()), new byte[0]));
        bridge.handle(new Frame(7, array(1, 4, 2, true), new byte[]{'n', 'o'}));
        assertEquals(14, sent.get(1).type); assertFalse(warnings.isEmpty()); assertTrue(received.isEmpty());
        bridge.handle(new Frame(5, array(41, array("text/plain"), array()), new byte[0]));
        bridge.localChanged("local wins", false);
        assertEquals(14, sent.get(3).type);
    }

    @Test public void maxTextFitsFourChunkWindowAndOversizedTextIsNotShared() throws Exception {
        bridge.enable(true);
        String value = "a".repeat(TextClipboard.LIMIT);
        bridge.localChanged(value, false);
        bridge.handle(new Frame(6, array(2, 1, array(0, array("text/plain"))), new byte[0]));
        assertEquals(5, sent.size());
        assertEquals(true, sent.get(4).f[3]);
        bridge.localChanged(value + "b", false);
        assertEquals(0, list(sent.get(5).f[1]).length);
        assertEquals(1, warnings.size());
    }
}
