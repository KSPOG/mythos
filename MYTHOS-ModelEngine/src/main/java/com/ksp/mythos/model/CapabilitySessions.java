package com.ksp.mythos.model;

import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Connection-scoped, visual-only capability negotiation. Transport adapters must
 * bind session tokens to authenticated connections, never to packet-supplied player IDs. */
public final class CapabilitySessions {
    public static final int PROTOCOL = 1;
    public static final int MESH = 1;
    public static final int SKINNING = 2;
    public static final int MATERIALS = 4;
    private static final int SUPPORTED = MESH | SKINNING | MATERIALS;
    public record Session(UUID token, int features, boolean negotiated) {}
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    public UUID connect(UUID player) {
        Objects.requireNonNull(player);
        UUID token = UUID.randomUUID();
        sessions.put(player, new Session(token, 0, false));
        return token;
    }
    public boolean negotiate(UUID player, UUID token, int protocol, int features) {
        var result = sessions.computeIfPresent(player, (id, old) -> {
            if (!old.token().equals(token) || old.negotiated()) return old;
            return new Session(token, protocol == PROTOCOL ? features & SUPPORTED : 0, true);
        });
        return result != null && result.token().equals(token) && result.negotiated()
                && (result.features() & MESH) != 0;
    }
    public ModelRegistry.Mode mode(UUID player, UUID token, int required, boolean verified) {
        var session = sessions.get(player);
        int mask = required | MESH;
        return verified && session != null && session.token().equals(token)
                && (session.features() & mask) == mask
                ? ModelRegistry.Mode.ENHANCED : ModelRegistry.Mode.VANILLA;
    }
    public void disconnect(UUID player, UUID token) {
        sessions.computeIfPresent(player, (id, old) -> old.token().equals(token) ? null : old);
    }
}
