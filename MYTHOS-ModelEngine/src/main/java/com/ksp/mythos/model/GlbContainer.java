package com.ksp.mythos.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

/** Strict embedded GLB subset. Structural validation only, before JSON/mesh decoding. */
public final class GlbContainer {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_JSON = 4 * 1024 * 1024;
    private GlbContainer() {}
    public record Parts(String json, ByteBuffer binary) {}
    public static Parts parse(byte[] bytes) {
        if (bytes.length < 20 || bytes.length > MAX_BYTES) throw invalid();
        var in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (in.getInt() != 0x46546c67 || in.getInt() != 2 || in.getInt() != bytes.length)
            throw invalid();
        int length = in.getInt();
        if (in.getInt() != 0x4e4f534a || length <= 0 || length > MAX_JSON
                || (length & 3) != 0 || length > in.remaining()) throw invalid();
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(in.slice(in.position(), length)).toString();
        } catch (CharacterCodingException e) { throw invalid(); }
        in.position(in.position() + length);
        ByteBuffer binary = ByteBuffer.allocate(0).asReadOnlyBuffer();
        if (in.hasRemaining()) {
            if (in.remaining() < 8) throw invalid();
            int size = in.getInt();
            if (in.getInt() != 0x004e4942 || size < 0 || (size & 3) != 0
                    || size != in.remaining()) throw invalid();
            byte[] copy = new byte[size];
            in.get(copy);
            binary = ByteBuffer.wrap(copy).asReadOnlyBuffer();
        }
        return new Parts(json, binary.order(ByteOrder.LITTLE_ENDIAN));
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or malformed GLB container");
    }
}
