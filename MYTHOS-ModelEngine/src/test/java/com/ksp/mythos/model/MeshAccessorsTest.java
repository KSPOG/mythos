package com.ksp.mythos.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class MeshAccessorsTest {
    public static void main(String[] args) {
        ByteBuffer data = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        data.putFloat(4, 1).putFloat(8, 2).putFloat(12, 3);
        data.putFloat(20, 4).putFloat(24, 5).putFloat(28, 6);
        data.position(4);
        if (!Arrays.equals(new float[]{1,2,3,4,5,6},
                MeshAccessors.floats(data, 0, 28, 0, 2, 3, 16)))
            throw new AssertionError("Interleaved attributes");
        if (data.position() != 4) throw new AssertionError("Buffer position changed");
        reject(() -> MeshAccessors.floats(data, 0, 27, 0, 2, 3, 16));
        reject(() -> MeshAccessors.floats(data, 0, 28, 1, 2, 3, 16));
        reject(() -> MeshAccessors.floats(data, 0, 28, 0, 2, 3, 8));
        reject(() -> MeshAccessors.floats(data, 0, 28, 0, 1_000_001, 3, 0));
        reject(() -> MeshAccessors.floats(data, Integer.MAX_VALUE, 28, 0, 1, 3, 0));
        data.putFloat(4, Float.NaN);
        reject(() -> MeshAccessors.floats(data, 0, 28, 0, 2, 3, 16));

        for (int type : new int[]{5121, 5123, 5125}) {
            int width = type == 5121 ? 1 : type == 5123 ? 2 : 4;
            ByteBuffer indices = ByteBuffer.allocate(3 * width).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 3; i++) {
                if (width == 1) indices.put((byte)i);
                else if (width == 2) indices.putShort((short)i);
                else indices.putInt(i);
            }
            indices.flip();
            if (!Arrays.equals(new int[]{0,1,2},
                    MeshAccessors.triangles(indices, 0, 3 * width, 0, 3, type, 3)))
                throw new AssertionError("Index type " + type);
            reject(() -> MeshAccessors.triangles(indices, 0, 3 * width, 0, 3, type, 2));
            reject(() -> MeshAccessors.triangles(indices, 0, 3 * width - 1, 0, 3, type, 3));
            reject(() -> MeshAccessors.triangles(indices, 0, 3 * width, 0, 2, type, 3));
        }
        ByteBuffer unsigned = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        unsigned.putInt(0, -1);
        reject(() -> MeshAccessors.triangles(unsigned, 0, 12, 0, 3, 5125, 3));
    }

    private static void reject(Runnable operation) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed accessor accepted");
    }
}
