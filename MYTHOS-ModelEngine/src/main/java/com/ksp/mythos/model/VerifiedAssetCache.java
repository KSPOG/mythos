package com.ksp.mythos.model;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.Comparator;
import java.util.ArrayList;
import java.nio.file.attribute.FileTime;

/** Content-addressed cache. Root must be private to the client process.
 * Caller owns the input stream. Transport timeouts are the caller's responsibility. */
public final class VerifiedAssetCache {
    private final Path root;
    private final long quotaBytes;
    private record Entry(Path path, long bytes, FileTime modified) {}
    public VerifiedAssetCache(Path root) throws IOException {
        this(root, 512L * 1024 * 1024);
    }
    public VerifiedAssetCache(Path root, long quotaBytes) throws IOException {
        if (quotaBytes <= 0) throw new IllegalArgumentException("Invalid cache quota");
        this.root = Files.createDirectories(root).toRealPath();
        this.quotaBytes = quotaBytes;
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
        if (asset.bytes() > quotaBytes) throw new IOException("Asset exceeds cache quota");
        if (contains(asset)) {
            reserve(asset);
            Files.setLastModifiedTime(path(asset), FileTime.fromMillis(System.currentTimeMillis()));
            return path(asset);
        }
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
            reserve(asset);
            return Files.move(temporary, path(asset), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    /** Evict oldest published entries first. Only one cache instance may own a root.
     * Temporary downloads are bounded separately by the per-asset limit. */
    private void reserve(ModelRegistry.Asset asset) throws IOException {
        var entries = new ArrayList<Entry>();
        long used = 0;
        try (var files = Files.list(root)) {
            for (Path file : files.toList()) {
                if (file.equals(path(asset)) || !file.getFileName().toString().matches("[a-f0-9]{64}\\.glb")
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                long bytes = Files.size(file);
                used = Math.addExact(used, bytes);
                entries.add(new Entry(file, bytes, Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)));
            }
        }
        entries.sort(Comparator.comparing(Entry::modified).thenComparing(e -> e.path().toString()));
        for (Entry entry : entries) {
            if (used <= quotaBytes - asset.bytes()) break;
            Files.delete(entry.path());
            used -= entry.bytes();
        }
    }
}
