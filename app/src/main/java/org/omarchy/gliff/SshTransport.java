package org.omarchy.gliff;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Base64;

public final class SshTransport implements AutoCloseable {
    public interface Trust {
        boolean approve(String host, String algorithm, String fingerprint) throws Exception;
    }

    private volatile Session session;
    private volatile ChannelExec channel;
    private volatile boolean closed;
    public InputStream input;
    public OutputStream output;
    private final StringBuilder diagnostics = new StringBuilder();

    public void connect(ConnectionConfig config, String password, byte[] key, File knownHosts, Trust trust)
            throws Exception {
        JSch jsch = new JSch();
        if (!knownHosts.exists() && !knownHosts.createNewFile()) throw new IOException("Cannot create SSH trust store");
        jsch.setKnownHosts(knownHosts.getPath());
        HostKeyRepository saved = jsch.getHostKeyRepository();
        jsch.setHostKeyRepository(new HostKeyRepository() {
            @Override public int check(String host, byte[] bytes) {
                int result = saved.check(host, bytes);
                if (result != NOT_INCLUDED) return result; // A changed pin is never replaced automatically.
                try {
                    HostKey hostKey = new HostKey(host, bytes);
                    String fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding()
                            .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (closed || !trust.approve(host, hostKey.getType(), fingerprint)) return NOT_INCLUDED;
                    if (closed) return NOT_INCLUDED;
                    saved.add(hostKey, null);
                    return saved.check(host, bytes);
                } catch (Exception e) { return NOT_INCLUDED; }
            }
            @Override public void add(HostKey key, UserInfo ui) { saved.add(key, ui); }
            @Override public void remove(String host, String type) { saved.remove(host, type); }
            @Override public void remove(String host, String type, byte[] key) { saved.remove(host, type, key); }
            @Override public String getKnownHostsRepositoryID() { return saved.getKnownHostsRepositoryID(); }
            @Override public HostKey[] getHostKey() { return saved.getHostKey(); }
            @Override public HostKey[] getHostKey(String host, String type) { return saved.getHostKey(host, type); }
        });
        if (key != null) jsch.addIdentity("imported-key", key, null,
                password.isEmpty() ? null : password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (closed) throw new IOException("Connection cancelled");
        session = jsch.getSession(config.user, config.host, config.port);
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", key == null ? "password,keyboard-interactive" : "publickey");
        session.setPassword(password);
        session.setServerAliveInterval(5000);
        session.setServerAliveCountMax(3);
        try {
            session.connect(15000);
            if (closed) throw new IOException("Connection cancelled");
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(config.command());
            channel.setPty(false);
            channel.setErrStream(new OutputStream() {
                @Override public void write(int b) {
                    synchronized (diagnostics) {
                        if (diagnostics.length() < 4096) diagnostics.append((char) b);
                    }
                }
            });
            input = channel.getInputStream();
            output = channel.getOutputStream();
            channel.connect(15000);
            if (closed) throw new IOException("Connection cancelled");
        } catch (Exception e) {
            close();
            if (e instanceof JSchException)
                throw new IOException("SSH: " + e.getMessage() + ". Check credentials and the saved host key.", e);
            throw e;
        }
    }

    public String diagnostics() {
        synchronized (diagnostics) { return diagnostics.toString().trim(); }
    }

    @Override public void close() {
        closed = true;
        ChannelExec c = channel;
        if (c != null) c.disconnect();
        Session s = session;
        if (s != null) s.disconnect();
    }
}
