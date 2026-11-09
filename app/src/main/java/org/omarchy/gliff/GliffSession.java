package org.omarchy.gliff;

import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.omarchy.gliff.Protocol.*;

public final class GliffSession implements AutoCloseable {
    public interface Listener {
        void status(String text);
        void configured(int width, int height, int viewWidth, int viewHeight, int scaleMilli);
        void cursor(Frame frame) throws IOException;
        void clipboard(Frame frame) throws IOException;
        void ended(String reason);
    }
    private static final class Outgoing {
        final int type; final Object[] fields; final byte[] payload;
        Outgoing(int type, Object[] fields, byte[] payload) { this.type = type; this.fields = fields; this.payload = payload; }
    }
    private final Listener listener;
    private final SshTransport ssh = new SshTransport();
    private final ArrayBlockingQueue<Outgoing> outgoing = new ArrayBlockingQueue<>(512);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final VideoDecoder decoder;
    private Thread reader, writer;
    private volatile boolean configured;

    public GliffSession(Surface surface, Listener listener) {
        this.listener = listener;
        decoder = new VideoDecoder(surface, new VideoDecoder.Listener() {
            @Override public void decoded(long id) { send(6, id, SystemClock.elapsedRealtime()); }
            @Override public void failed(Exception error) { fail("Video: " + error.getMessage()); }
        });
    }

    public void start(ConnectionConfig config, String password, byte[] key, File knownHosts, SshTransport.Trust trust,
                      String keymap, int width, int height) {
        reader = new Thread(() -> {
            try {
                listener.status("Connecting over SSH…");
                ssh.connect(config, password, key, knownHosts, trust);
                if (closed.get()) return;
                // Hello must precede clipboard offers or input queued by the UI during SSH setup.
                Protocol.send(ssh.output, 0, array(1, keymap, array(array(1), 3840, 2160, array(2), array())), new byte[0]);
                Protocol.send(ssh.output, 1, array(Math.max(640, Math.min(3840, width)),
                        Math.max(480, Math.min(2160, height)), 1.0f), new byte[0]);
                writer = new Thread(this::writeLoop, "gliff-send");
                writer.start();
                listener.status("Starting remote desktop…");
                while (!closed.get()) handle(receive(ssh.input));
            } catch (Exception e) {
                String detail = ssh.diagnostics();
                fail((e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        + (detail.isEmpty() ? "" : "\n" + detail));
            } finally { close(); }
        }, "gliff-receive");
        reader.start();
    }

    private void handle(Frame frame) throws IOException {
        Object[] f = frame.fields;
        switch (frame.type) {
            case 0:
                if (integer(f, 0) != 1) throw new IOException("Incompatible Gliff protocol version");
                break;
            case 1:
                if (integer(f, 0) != 1 || integer(f, 1) != 2) throw new IOException("Server did not select H.264 Single420");
                int w = integer(f, 3), h = integer(f, 4), scale = integer(f, 5);
                if (scale < 1 || scale > 16000) throw new IOException("Invalid stream scale");
                Object extra = field(f, 6);
                if (!(extra instanceof byte[])) throw new IOException("Invalid H.264 configuration");
                decoder.configure(w, h, (byte[]) extra);
                int vw = integer(f, 8), vh = integer(f, 9);
                if (vw < 1 || vh < 1 || vw > 16384 || vh > 16384) throw new IOException("Invalid desktop size");
                listener.configured(w, h, vw, vh, scale);
                configured = true;
                listener.status("Connected · " + w + "×" + h + " · H.264 · SSH");
                break;
            case 2:
                if (!configured) throw new IOException("Video before stream configuration");
                if (integer(f, 4) != frame.payload.length || integer(f, 5) != 0)
                    throw new IOException("Invalid video payload length");
                decoder.submit(number(f, 0), frame.payload);
                break;
            case 3: case 4: listener.cursor(frame); break;
            case 5: case 6: case 7: case 10: case 11: listener.clipboard(frame); break;
            case 9: throw new IOException("Remote server: " + text(f, 1));
            case 12: send(16, number(f, 0)); break;
            default: break; // Unknown messages include their payload in the framing.
        }
    }

    public boolean send(int type, Object... fields) { return sendPayload(type, fields, new byte[0]); }

    public boolean sendPayload(int type, Object[] fields, byte[] payload) {
        if (closed.get() || closing.get()) return false;
        if (outgoing.offer(new Outgoing(type, fields, payload))) return true;
        fail("Input queue filled; disconnected to avoid stuck keys.");
        return false;
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                Outgoing message = outgoing.take();
                Protocol.send(ssh.output, message.type, message.fields, message.payload);
                if (message.type == 12) { finish(); return; }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { fail("SSH write failed: " + e.getMessage()); }
    }

    private void fail(String reason) {
        if (closed.compareAndSet(false, true)) {
            shutdown();
            if (!closing.get()) listener.ended(reason);
        }
    }

    private void shutdown() {
        // Disconnecting SSH may wait on its writer, so never do it on the Android UI thread.
        new Thread(ssh::close, "gliff-disconnect").start();
        decoder.close();
        if (writer != null) writer.interrupt();
        outgoing.clear();
    }

    @Override public void close() {
        if (!closing.compareAndSet(false, true) || closed.get()) return;
        if (writer != null && outgoing.offer(new Outgoing(12, array(), new byte[0]))) {
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finish();
            }, "gliff-close-timeout").start();
        } else finish();
    }

    private void finish() { if (closed.compareAndSet(false, true)) shutdown(); }
}
