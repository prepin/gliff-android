package org.omarchy.gliff;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** Gliff v1: little-endian lengths, CBOR [variant, fields], then raw payload. */
public final class Protocol {
    public static final int MAX_BODY = 1 << 20;
    // shortcut: cap Android frames at 32 MiB, increase only for a measured larger keyframe.
    public static final int MAX_PAYLOAD = 32 << 20;
    private static final CBORFactory CBOR = CBORFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_BODY).maxDocumentLength(MAX_BODY).build()).build();

    private Protocol() {}

    public static Object[] array(Object... values) { return values; }

    public static byte[] encode(Object value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CBORGenerator g = CBOR.createGenerator(out)) {
            g.enable(CBORGenerator.Feature.WRITE_MINIMAL_INTS);
            write(g, value);
        }
        return out.toByteArray();
    }

    private static void write(CBORGenerator g, Object value) throws IOException {
        if (value == null) g.writeNull();
        else if (value instanceof Object[]) {
            Object[] values = (Object[]) value;
            g.writeStartArray(value, values.length);
            for (Object item : values) write(g, item);
            g.writeEndArray();
        } else if (value instanceof Boolean) g.writeBoolean((Boolean) value);
        else if (value instanceof String) g.writeString((String) value);
        else if (value instanceof byte[]) g.writeBinary((byte[]) value);
        else if (value instanceof Float) g.writeNumber((Float) value);
        else if (value instanceof Double) g.writeNumber((Double) value);
        else if (value instanceof Number) g.writeNumber(((Number) value).longValue());
        else throw new IOException("Unsupported CBOR value");
    }

    public static Object decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BODY) throw new IOException("CBOR body too large");
        try (JsonParser p = CBOR.createParser(bytes)) {
            if (p.nextToken() == null) throw new IOException("Empty CBOR body");
            Object value = read(p);
            if (p.nextToken() != null) throw new IOException("Trailing CBOR data");
            return value;
        }
    }

    private static Object read(JsonParser p) throws IOException {
        JsonToken t = p.currentToken();
        if (t == JsonToken.START_ARRAY) {
            ArrayList<Object> result = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() == null) throw new EOFException("Truncated CBOR array");
                result.add(read(p));
            }
            return result.toArray();
        }
        // Future fields can contain maps, which old clients must ignore.
        if (t == JsonToken.START_OBJECT) { p.skipChildren(); return null; }
        if (t == JsonToken.VALUE_STRING) return p.getText();
        if (t == JsonToken.VALUE_EMBEDDED_OBJECT) return p.getBinaryValue();
        if (t == JsonToken.VALUE_NUMBER_INT) return p.getLongValue();
        if (t == JsonToken.VALUE_NUMBER_FLOAT) return p.getDoubleValue();
        if (t == JsonToken.VALUE_TRUE) return true;
        if (t == JsonToken.VALUE_FALSE) return false;
        if (t == JsonToken.VALUE_NULL) return null;
        throw new IOException("Invalid CBOR value: " + t);
    }

    public static final class Frame {
        public final int type;
        public final Object[] fields;
        public final byte[] payload;
        Frame(int type, Object[] fields, byte[] payload) {
            this.type = type; this.fields = fields; this.payload = payload;
        }
    }

    public static Frame receive(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        int body = Integer.reverseBytes(in.readInt());
        int payload = Integer.reverseBytes(in.readInt());
        if (body < 1 || body > MAX_BODY || payload < 0 || payload > MAX_PAYLOAD)
            throw new IOException("Invalid Gliff frame lengths");
        byte[] bytes = new byte[body];
        in.readFully(bytes);
        Object[] message = list(decode(bytes));
        if (message.length != 2) throw new IOException("Invalid Gliff message");
        int type = integer(message, 0);
        Object[] fields = list(message[1]);
        byte[] data = new byte[payload];
        in.readFully(data);
        return new Frame(type, fields, data);
    }

    public static void send(OutputStream out, int type, Object[] fields, byte[] payload) throws IOException {
        byte[] body = encode(array(type, fields));
        if (body.length > MAX_BODY || payload.length > MAX_PAYLOAD) throw new IOException("Frame too large");
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(body.length).putInt(payload.length).array());
        out.write(body);
        out.write(payload);
        out.flush();
    }

    public static Object[] list(Object value) throws IOException {
        if (!(value instanceof Object[])) throw new IOException("Expected CBOR array");
        return (Object[]) value;
    }

    public static Object field(Object[] fields, int index) throws IOException {
        if (index < 0 || index >= fields.length) throw new IOException("Missing protocol field " + index);
        return fields[index];
    }

    public static long number(Object[] fields, int index) throws IOException {
        Object value = field(fields, index);
        if (!(value instanceof Long) && !(value instanceof Integer)) throw new IOException("Expected protocol integer");
        return ((Number) value).longValue();
    }

    public static int integer(Object[] fields, int index) throws IOException {
        long value = number(fields, index);
        if (value < 0 || value > Integer.MAX_VALUE) throw new IOException("Integer out of range");
        return (int) value;
    }

    public static boolean bool(Object[] fields, int index) throws IOException {
        Object value = field(fields, index);
        if (!(value instanceof Boolean)) throw new IOException("Expected protocol boolean");
        return (Boolean) value;
    }

    public static String text(Object[] fields, int index) throws IOException {
        Object value = field(fields, index);
        if (!(value instanceof String)) throw new IOException("Expected protocol string");
        return (String) value;
    }
}
