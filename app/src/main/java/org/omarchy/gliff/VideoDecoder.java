package org.omarchy.gliff;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** MediaCodec owns its buffers and Surface on a single callback thread. */
public final class VideoDecoder implements AutoCloseable {
    public interface Listener {
        void decoded(long frameId);
        void failed(Exception error);
    }
    private static final class Packet {
        final long id;
        final byte[] bytes;
        Packet(long id, byte[] bytes) { this.id = id; this.bytes = bytes; }
    }
    private final HandlerThread thread = new HandlerThread("gliff-codec");
    private final Handler handler;
    private final Listener listener;
    private final Surface surface;
    private final ArrayBlockingQueue<Packet> packets = new ArrayBlockingQueue<>(8);
    private final ArrayDeque<Integer> inputs = new ArrayDeque<>();
    private MediaCodec codec;
    private volatile boolean closed;
    private int width, height;

    public VideoDecoder(Surface surface, Listener listener) {
        this.surface = surface;
        this.listener = listener;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void configure(int width, int height, byte[] extra) throws IOException {
        if (width < 2 || height < 2 || width > 4096 || height > 4096 || (long) width * height > 3840L * 2160)
            throw new IOException("Unsupported video size: " + width + "×" + height);
        CountDownLatch ready = new CountDownLatch(1);
        Exception[] failure = new Exception[1];
        handler.post(() -> {
            try {
                if (closed) throw new IOException("Decoder closed");
                if (codec != null && this.width == width && this.height == height) return;
                releaseCodec();
                this.width = width; this.height = height;
                codec = MediaCodec.createDecoderByType("video/avc");
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Math.max(1 << 20, width * height * 3 / 2));
                if (android.os.Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
                if (extra.length > 0) format.setByteBuffer("csd-0", ByteBuffer.wrap(extra));
                codec.setCallback(new MediaCodec.Callback() {
                    @Override public void onInputBufferAvailable(MediaCodec c, int index) {
                        if (c != codec || closed) return;
                        inputs.add(index);
                        pump();
                    }
                    @Override public void onOutputBufferAvailable(MediaCodec c, int index, MediaCodec.BufferInfo info) {
                        if (c != codec || closed) return;
                        try {
                            c.releaseOutputBuffer(index, true);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) listener.decoded(info.presentationTimeUs);
                        } catch (Exception e) { listener.failed(e); }
                    }
                    @Override public void onOutputFormatChanged(MediaCodec c, MediaFormat format) {}
                    @Override public void onError(MediaCodec c, MediaCodec.CodecException e) {
                        if (c == codec && !closed) listener.failed(e);
                    }
                }, handler);
                codec.configure(format, surface, null, 0);
                codec.start();
            } catch (Exception e) { failure[0] = e; }
            finally { ready.countDown(); }
        });
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IOException("Video decoder timed out");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        if (failure[0] != null) throw new IOException("Cannot start H.264 decoder", failure[0]);
    }

    public void submit(long id, byte[] bytes) throws IOException {
        try {
            if (closed || !packets.offer(new Packet(id, bytes), 2, TimeUnit.SECONDS))
                throw new IOException("Video decoder is not keeping up");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        handler.post(this::pump);
    }

    private void pump() {
        try {
            while (!closed && codec != null && !inputs.isEmpty() && !packets.isEmpty()) {
                int index = inputs.remove();
                Packet packet = packets.remove();
                ByteBuffer buffer = codec.getInputBuffer(index);
                if (buffer == null || buffer.capacity() < packet.bytes.length) throw new IOException("H.264 frame exceeds decoder capacity");
                buffer.clear();
                buffer.put(packet.bytes);
                codec.queueInputBuffer(index, 0, packet.bytes.length, packet.id, 0);
            }
        } catch (Exception e) { listener.failed(e); }
    }

    private void releaseCodec() {
        inputs.clear();
        packets.clear();
        if (codec != null) {
            MediaCodec old = codec;
            codec = null;
            old.release();
        }
    }

    @Override public void close() {
        closed = true;
        handler.post(() -> { try { releaseCodec(); } finally { thread.quitSafely(); } });
    }
}
