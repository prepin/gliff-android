package org.omarchy.gliff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import static org.omarchy.gliff.Protocol.*;

/** Opt-in text clipboard. Android needs eager retrieval because it has no lazy text provider. */
public final class TextClipboard {
    public interface Sink { void send(int type, Object[] fields, byte[] payload); }
    public static final int LIMIT = 1 << 20;
    private static final int CHUNK = 256 << 10;
    private static final String[] MIMES = {"text/plain;charset=utf-8", "text/plain", "UTF8_STRING", "STRING", "TEXT"};
    private final Sink sink;
    private final Consumer<String> received;
    private final Consumer<String> warning;
    private boolean enabled;
    private long serial, nextId = 1, receivingId = -1;
    private byte[] local = new byte[0];
    private String remoteText;
    private ByteArrayOutputStream transfer;

    public TextClipboard(Sink sink, Consumer<String> received, Consumer<String> warning) {
        this.sink = sink; this.received = received; this.warning = warning;
    }

    public synchronized void enable(boolean value) {
        enabled = value;
        if (!value) { cancel(); local = new byte[0]; serial++; offer(); }
    }

    public synchronized void localChanged(String text, boolean sensitive) {
        if (!enabled) return;
        if (!sensitive && text != null && text.equals(remoteText)) return;
        remoteText = null;
        cancel();
        local = sensitive || text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        if (local.length > LIMIT) { local = new byte[0]; warning.accept("Clipboard text exceeds 1 MiB; not shared."); }
        serial = (serial + 1) & 0xffffffffL;
        offer();
    }

    private void offer() {
        sink.send(8, array(serial, local.length == 0 ? array() : MIMES, array()), new byte[0]);
    }

    public synchronized void handle(Frame frame) throws IOException {
        Object[] f = frame.fields;
        if (!enabled) {
            if (frame.type == 6 || frame.type == 7) sink.send(14, array(number(f, 0)), new byte[0]);
            return;
        }
        switch (frame.type) {
            case 5:
                cancel();
                long remoteSerial = unsigned(f, 0);
                Object[] types = list(field(f, 1));
                String selected = null;
                for (String mime : MIMES) {
                    for (Object type : types) if (mime.equals(type)) { selected = mime; break; }
                    if (selected != null) break;
                }
                if (selected == null) return;
                receivingId = nextId;
                nextId = nextId >= 0xfffffffdL ? 1 : nextId + 2;
                transfer = new ByteArrayOutputStream();
                sink.send(9, array(receivingId, remoteSerial, array(0, array(selected))), new byte[0]);
                break;
            case 6:
                long requestId = unsigned(f, 0);
                Object[] item = list(field(f, 2));
                boolean mimeOk = false;
                if (integer(item, 0) == 0) {
                    String requested = text(list(field(item, 1)), 0);
                    for (String mime : MIMES) if (mime.equals(requested)) mimeOk = true;
                }
                if ((requestId & 1) != 0 || unsigned(f, 1) != serial || !mimeOk || local.length == 0) {
                    sink.send(14, array(requestId), new byte[0]);
                    return;
                }
                // The 1 MiB cap fits exactly in the protocol's four-chunk ACK window.
                for (int offset = 0; offset < local.length; offset += CHUNK) {
                    int size = Math.min(CHUNK, local.length - offset);
                    byte[] data = java.util.Arrays.copyOfRange(local, offset, offset + size);
                    sink.send(10, array(requestId, offset, size, offset + size == local.length), data);
                }
                break;
            case 7:
                long id = unsigned(f, 0);
                if (id != receivingId || transfer == null) { sink.send(14, array(id), new byte[0]); return; }
                if (number(f, 1) != transfer.size() || integer(f, 2) != frame.payload.length
                        || frame.payload.length > CHUNK || transfer.size() + frame.payload.length > LIMIT) {
                    cancel();
                    warning.accept("Remote clipboard transfer rejected: invalid size or offset.");
                    return;
                }
                transfer.write(frame.payload);
                sink.send(13, array(id, transfer.size()), new byte[0]);
                if (bool(f, 3)) {
                    remoteText = new String(transfer.toByteArray(), StandardCharsets.UTF_8);
                    receivingId = -1;
                    transfer = null;
                    local = new byte[0]; // Never re-offer a remote selection back to its source.
                    received.accept(remoteText);
                }
                break;
            case 11:
                if (number(f, 0) == receivingId) {
                    receivingId = -1; transfer = null;
                    warning.accept("Remote clipboard transfer was cancelled.");
                }
                break;
            default: break;
        }
    }

    private static long unsigned(Object[] f, int i) throws IOException {
        long n = number(f, i);
        if (n < 0 || n > 0xffffffffL) throw new IOException("Invalid clipboard identifier");
        return n;
    }

    private void cancel() {
        if (receivingId >= 0) sink.send(14, array(receivingId), new byte[0]);
        receivingId = -1;
        transfer = null;
    }
}
