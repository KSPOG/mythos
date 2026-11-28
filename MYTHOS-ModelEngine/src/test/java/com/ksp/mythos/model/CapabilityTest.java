package com.ksp.mythos.model;
import java.util.UUID;
public final class CapabilityTest {
    public static void main(String[] args) {
        var sessions = new CapabilitySessions();
        var player = UUID.randomUUID();
        var old = sessions.connect(player);
        check(sessions.mode(player, old, 0, true) == ModelRegistry.Mode.VANILLA);
        check(!sessions.negotiate(player, old, 2, 7));
        check(!sessions.negotiate(player, old, 1, 7));
        var token = sessions.connect(player);
        check(!sessions.negotiate(player, old, 1, 7));
        check(sessions.negotiate(player, token, 1, 1));
        check(sessions.mode(player, token, 0, true) == ModelRegistry.Mode.ENHANCED);
        check(sessions.mode(player, token, 2, true) == ModelRegistry.Mode.VANILLA);
        check(sessions.mode(player, token, 0, false) == ModelRegistry.Mode.VANILLA);
        sessions.disconnect(player, old);
        check(sessions.mode(player, token, 0, true) == ModelRegistry.Mode.ENHANCED);
        sessions.disconnect(player, token);
        check(sessions.mode(player, token, 0, true) == ModelRegistry.Mode.VANILLA);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
