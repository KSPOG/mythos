package com.ksp.mythos.model;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class VerifiedAssetCacheTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mythos-cache-test");
        try {
            byte[] data = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46546c67).putInt(2).putInt(24).putInt(4).putInt(0x4e4f534a)
                .put(new byte[]{123,125,32,32}).array();
            var asset = new ModelRegistry.Asset("mythos:test", "mythos:fallback", "test.glb",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)), 24, 1);
            var cache = new VerifiedAssetCache(root);
            check(!cache.contains(asset));
            reject(cache, asset, Arrays.copyOf(data, 23));
            reject(cache, asset, Arrays.copyOf(data, 25));
            byte[] corrupt = data.clone(); corrupt[23] = 0;
            reject(cache, asset, corrupt);
            try (var entries = Files.list(root)) { check(entries.count() == 0); }
            Path file = cache.store(asset, new ByteArrayInputStream(data));
            check(cache.contains(asset));
            Files.write(file, corrupt);
            check(!cache.contains(asset));
            check(cache.store(asset, new ByteArrayInputStream(data)).equals(file));
            check(cache.contains(asset));
            var limited = new VerifiedAssetCache(root, 24);
            byte[] other = data.clone(); other[22] = 9;
            var second = new ModelRegistry.Asset("mythos:other", "mythos:fallback", "other.glb",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(other)), 24, 1);
            reject(limited, second, corrupt);
            check(limited.contains(asset));
            limited.store(second, new ByteArrayInputStream(other));
            check(!limited.contains(asset));
            check(limited.contains(second));
            reject(new VerifiedAssetCache(root, 23), asset, data);
            check(limited.contains(second));
        } finally {
            try (var entries = Files.list(root)) {
                for (Path file : entries.toList()) Files.delete(file);
            }
            Files.delete(root);
        }
    }
    private static void reject(VerifiedAssetCache cache, ModelRegistry.Asset asset, byte[] data) throws Exception {
        try { cache.store(asset, new ByteArrayInputStream(data)); throw new AssertionError("Invalid asset accepted"); }
        catch (IOException expected) {}
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
