# MYTHOS Model Engine — 0.1.0

Phase 1: renderer-independent Java 21 registry and fallback policy for the existing Forge 1.21.1 server ecosystem. This is a library, not an installable Forge mod yet.

Each asset declares a namespaced identifier, vanilla fallback resource, relative GLB path, SHA-256, byte size and triangle count. Limits are initial policy choices, not Minecraft limits. Metadata validation does not inspect the GLB or verify downloaded bytes; the future asset loader must do that before reporting verification.

Enhanced rendering requires a compatible client handshake and verified asset; all other cases default to vanilla. Gameplay, hitboxes and ownership remain server-authoritative. Never trust a client capability report for gameplay authorization.

Run `gradle check` with Java 21 to exercise registry tests.

Next: Forge optional handshake, per-viewer fallback display management, bounded GLB parsing and actual mesh renderer, then animated dragon asset and mount integration. No high-poly asset or client renderer is included in this phase.
