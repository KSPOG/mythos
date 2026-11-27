package com.ksp.mythos.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class GlbContainerTest {
    public static void main(String[] args) {
        byte[] good = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546c67).putInt(2).putInt(36)
            .putInt(4).putInt(0x4e4f534a).put(new byte[]{123,125,32,32})
            .putInt(4).putInt(0x004e4942).putInt(42).array();
        var parts = GlbContainer.parse(good);
        if (!parts.json().equals("{}  ") || parts.binary().getInt(0) != 42
                || !parts.binary().isReadOnly()) throw new AssertionError();
        good[32] = 0;
        if (parts.binary().getInt(0) != 42) throw new AssertionError();
        reject(Arrays.copyOf(good, 35));
        for (int offset : new int[]{0,4,8,12,16,24,28}) {
            byte[] bad = good.clone();
            ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1);
            reject(bad);
        }
        byte[] bad = good.clone();
        bad[20] = (byte)0xff;
        reject(bad);
    }
    private static void reject(byte[] data) {
        try { GlbContainer.parse(data); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed container accepted");
    }
}
