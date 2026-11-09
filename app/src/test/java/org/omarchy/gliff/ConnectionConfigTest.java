package org.omarchy.gliff;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionConfigTest {
    @Test public void commandHasNoUserControlledShellSyntax() {
        ConnectionConfig c = new ConnectionConfig("host", "me", 22, false, "DP-1");
        assertEquals("gliff-server --stdio --low-bandwidth --output 'DP-1'", c.command());
        assertEquals("gliff-server --stdio --low-bandwidth --headless",
                new ConnectionConfig("::1", "me", 2222, true, "").command());
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("host", "me", 0, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("host", "me", 22, false, "';touch /tmp/pwn;#"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("host", "me", 22, false, "$(id)"));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("-oProxyCommand=x", "me", 22, false, ""));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("me@host", "me", 22, false, ""));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionConfig("a\nb\nc", "me", 22, false, ""));
    }
}
