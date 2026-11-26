package com.ksp.mythos.model;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/** Content-addressed cache. Root must be private to the client process.
 * Caller owns the input stream. Transport timeouts are the caller's responsibility. */
public final class VerifiedAssetCache {
    private final Path root;
    public VerifiedAssetCache(Path root) throws IOException {
        this.root = Files.createDirectories(root).toRealPath();
    }
    private Path path(ModelRegistry.Asset asset) {
        return root.resolve(asset.sha256() + ".glb");
    }
    public synchronized boolean contains(ModelRegistry.Asset asset) throws IOException {
        Path file = path(asset);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return AssetVerifier.verify(asset, in);
        }
    }
    public synchronized Path store(ModelRegistry.Asset asset, InputStream source) throws IOException {
        Objects.requireNonNull(source);
        if (contains(asset)) return path(asset);
        Path temporary = Files.createTempFile(root, "download-", ".part");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                long remaining = asset.bytes();
                while (remaining > 0) {
                    int n = source.read(buffer, 0, (int)Math.min(remaining, buffer.length));
                    if (n <= 0) throw new IOException("Truncated asset");
                    out.write(buffer, 0, n);
                    remaining -= n;
                }
                if (source.read() != -1) throw new IOException("Oversized asset");
            }
            try (InputStream in = Files.newInputStream(temporary)) {
                if (!AssetVerifier.verify(asset, in)) throw new IOException("Asset verification failed");
            }
            return Files.move(temporary, path(asset), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
