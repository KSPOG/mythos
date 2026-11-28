package com.ksp.mythos.model;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
public final class AssetVerifierTest {
    public static void main(String[] args) throws Exception {
        byte[] data = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546c67).putInt(2).putInt(24).putInt(4).putInt(0x4e4f534a)
            .put(new byte[]{123,125,32,32}).array();
        var asset = new ModelRegistry.Asset("mythos:test", "mythos:fallback", "test.glb",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)), 24, 1);
        check(AssetVerifier.verify(asset, new ByteArrayInputStream(data)));
        check(!AssetVerifier.verify(asset, new ByteArrayInputStream(Arrays.copyOf(data, 23))));
        check(!AssetVerifier.verify(asset, new ByteArrayInputStream(Arrays.copyOf(data, 25))));
        data[23] = 0;
        check(!AssetVerifier.verify(asset, new ByteArrayInputStream(data)));
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
