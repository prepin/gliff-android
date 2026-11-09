package org.omarchy.gliff;

public final class ConnectionConfig {
    public final String host, user, output;
    public final int port;
    public final boolean headless;

    public ConnectionConfig(String host, String user, int port, boolean headless, String output) {
        this.host = host.trim();
        this.user = user.trim();
        this.port = port;
        this.headless = headless;
        this.output = output.trim();
        if (this.host.startsWith("-") || !this.host.matches("[^\\s/@]+"))
            throw new IllegalArgumentException("Enter an SSH hostname or IP address, without user@ or a port.");
        if (!this.user.matches("[a-zA-Z0-9_.@-]+")) throw new IllegalArgumentException("Enter an SSH username.");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("SSH port must be 1–65535.");
        if (!this.output.isEmpty() && !this.output.matches("[a-zA-Z0-9_.:-]+"))
            throw new IllegalArgumentException("Invalid output name (for example DP-1).");
    }

    public String command() {
        return "gliff-server --stdio --low-bandwidth" + (headless ? " --headless"
                : " --output '" + (output.isEmpty() ? "auto" : output) + "'");
    }
}
