package com.ksp.mythos.sponge;

import com.ksp.mythos.core.character.CharacterClass;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Small first-milestone persistence adapter. Replace with PostgreSQL in the data milestone. */
final class ClassStore {
    private final Path file;
    private final Properties values = new Properties();

    ClassStore(final Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                this.values.load(input);
            }
        }
    }

    synchronized Optional<CharacterClass> find(final UUID playerId) {
        return CharacterClass.fromId(this.values.getProperty(playerId.toString()));
    }

    synchronized boolean assignIfAbsent(final UUID playerId, final CharacterClass characterClass) throws IOException {
        final String key = playerId.toString();
        if (this.values.containsKey(key)) return false;
        this.assign(playerId, characterClass);
        return true;
    }

    synchronized void assign(final UUID playerId, final CharacterClass characterClass) throws IOException {
        final String key = playerId.toString();
        final Properties updated = new Properties();
        updated.putAll(this.values);
        updated.setProperty(key, characterClass.id());
        this.save(updated);
        this.values.setProperty(key, characterClass.id());
    }

    private void save(final Properties updated) throws IOException {
        final Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            updated.store(output, "MYTHOS character class assignments; server-authoritative");
        }
        try {
            Files.move(temporary, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
