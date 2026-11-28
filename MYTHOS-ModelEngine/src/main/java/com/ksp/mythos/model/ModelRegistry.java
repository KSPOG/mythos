package com.ksp.mythos.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Shared, renderer-independent asset registry. Never grants gameplay permissions. */
public final class ModelRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    public enum Mode { VANILLA, ENHANCED }

    public record Asset(String id, String fallback, String meshPath, String sha256,
                        long bytes, int triangles) {
        public Asset {
            Objects.requireNonNull(id);
            Objects.requireNonNull(fallback);
            Objects.requireNonNull(meshPath);
            Objects.requireNonNull(sha256);
            if (!ID.matcher(id).matches() || !ID.matcher(fallback).matches()
                    || id.contains("..") || fallback.contains(".."))
                throw new IllegalArgumentException("Invalid resource identifier");
            if (!meshPath.matches("[a-z0-9_/-]+\\.glb") || meshPath.startsWith("/")
                    || !HASH.matcher(sha256).matches())
                throw new IllegalArgumentException("Invalid mesh metadata");
            if (bytes <= 0 || bytes > 64L * 1024 * 1024
                    || triangles <= 0 || triangles > 100_000)
                throw new IllegalArgumentException("Asset exceeds limits");
        }
    }

    private final Map<String, Asset> assets = new ConcurrentHashMap<>();
    public void register(Asset asset) {
        Objects.requireNonNull(asset);
        if (assets.putIfAbsent(asset.id(), asset) != null)
            throw new IllegalArgumentException("Duplicate model: " + asset.id());
    }
    public Optional<Asset> find(String id) { return Optional.ofNullable(assets.get(id)); }
    public Map<String, Asset> snapshot() { return Map.copyOf(assets); }
    public Mode selectMode(boolean compatibleClient, boolean assetVerified) {
        return compatibleClient && assetVerified ? Mode.ENHANCED : Mode.VANILLA;
    }
}
