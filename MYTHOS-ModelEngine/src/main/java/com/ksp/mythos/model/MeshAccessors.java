package com.ksp.mythos.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Bounded decoding of embedded float vertex attributes and unsigned triangle indices.
 * JSON descriptor validation is a separate responsibility. */
public final class MeshAccessors {
    private static final int MAX_VERTICES = 1_000_000;
    private static final int MAX_INDICES = 3_000_000;
    private MeshAccessors() {}

    public static float[] floats(ByteBuffer binary, int offset, int viewLength,
                                 int accessorOffset, int count, int components, int stride) {
        if (components < 1 || components > 4 || count > MAX_VERTICES)
            throw invalid();
        int width = components * Float.BYTES;
        ByteBuffer data = checked(binary, offset, viewLength, accessorOffset,
                                  count, width, stride, Float.BYTES);
        int step = stride == 0 ? width : stride;
        float[] result = new float[count * components];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < components; j++) {
                float value = data.getFloat(i * step + j * Float.BYTES);
                if (!Float.isFinite(value)) throw invalid();
                result[i * components + j] = value;
            }
        }
        return result;
    }

    public static int[] triangles(ByteBuffer binary, int offset, int viewLength,
                                  int accessorOffset, int count, int componentType,
                                  int vertexCount) {
        if (count > MAX_INDICES || count % 3 != 0
                || vertexCount <= 0 || vertexCount > MAX_VERTICES) throw invalid();
        int width = switch (componentType) {
            case 5121 -> 1;
            case 5123 -> 2;
            case 5125 -> 4;
            default -> throw invalid();
        };
        ByteBuffer data = checked(binary, offset, viewLength, accessorOffset,
                                  count, width, 0, width);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            int at = i * width;
            long index = switch (width) {
                case 1 -> Byte.toUnsignedInt(data.get(at));
                case 2 -> Short.toUnsignedInt(data.getShort(at));
                default -> Integer.toUnsignedLong(data.getInt(at));
            };
            if (index >= vertexCount) throw invalid();
            result[i] = (int) index;
        }
        return result;
    }

    private static ByteBuffer checked(ByteBuffer binary, int offset, int viewLength,
                                      int accessorOffset, int count, int width,
                                      int stride, int alignment) {
        Objects.requireNonNull(binary);
        int step = stride == 0 ? width : stride;
        long start = (long) offset + accessorOffset;
        long end = (long) accessorOffset + (long) (count - 1) * step + width;
        if (offset < 0 || viewLength < 0 || accessorOffset < 0 || count <= 0
                || step < width || step > 252 || step % alignment != 0
                || accessorOffset % alignment != 0 || start % alignment != 0
                || end > viewLength || (long) offset + viewLength > binary.remaining())
            throw invalid();
        return binary.slice().slice((int) start, (int) (end - accessorOffset))
                     .order(ByteOrder.LITTLE_ENDIAN);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or malformed mesh accessor");
    }
}
