package com.ksp.mythos.model;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Verifies content identity and GLB envelope, not mesh semantics or GPU safety. */
public final class AssetVerifier {
    private AssetVerifier() {}
    public static boolean verify(ModelRegistry.Asset asset, InputStream stream) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        if (asset.bytes() < 20) return false;
        byte[] header = stream.readNBytes(12);
        if (header.length != 12) return false;
        var buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt() != 0x46546c67 || buf.getInt() != 2
                || Integer.toUnsignedLong(buf.getInt()) != asset.bytes()) return false;
        digest.update(header);
        long remaining = asset.bytes() - 12;
        byte[] chunk = new byte[8192];
        while (remaining > 0) {
            int n = stream.read(chunk, 0, (int)Math.min(chunk.length, remaining));
            if (n <= 0) return false;
            digest.update(chunk, 0, n);
            remaining -= n;
        }
        return stream.read() == -1 && MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(asset.sha256()));
    }
}
