package com.ksp.mythos.sponge;

import com.ksp.mythos.core.character.CharacterClass;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;

/** Block layout for the Rift Sanctum, relative to the original arrival lodestone. */
final class HubArchitecture {
    private HubArchitecture() {
    }

    static Map<ServerLocation, BlockState> layout(final ServerLocation center) {
        final var blocks = new LinkedHashMap<ServerLocation, BlockState>();
        final BlockState frame = BlockTypes.POLISHED_BLACKSTONE_BRICKS.get().defaultState();
        final BlockState trim = BlockTypes.CHISELED_POLISHED_BLACKSTONE.get().defaultState();
        final BlockState copper = BlockTypes.WAXED_OXIDIZED_COPPER.get().defaultState();
        final BlockState light = BlockTypes.SEA_LANTERN.get().defaultState();

        // Four arches frame views down the main paths and leave the central walkways open.
        for (final int direction : new int[] {-1, 1}) {
            for (final int side : new int[] {-3, 3}) {
                for (int y = 0; y <= 5; y++) {
                    final BlockState state = y == 0 || y == 5 ? trim : y == 2 ? light : frame;
                    blocks.put(center.add(side, y, direction * 9), state);
                    blocks.put(center.add(direction * 9, y, side), state);
                }
            }
            for (int across = -3; across <= 3; across++) {
                final int y = Math.abs(across) >= 2 ? 5 : 6;
                blocks.put(center.add(across, y, direction * 9), frame);
                blocks.put(center.add(direction * 9, y, across), frame);
            }
            for (int across = -1; across <= 1; across++) {
                final BlockState state = across == 0 ? light : copper;
                blocks.put(center.add(across, 7, direction * 9), state);
                blocks.put(center.add(direction * 9, 7, across), state);
            }
        }

        // An amethyst shard hangs above the arrival point. Gaps around it show the smaller fragments.
        for (int y = 4; y <= 9; y++) {
            final int width = Math.min(y - 3, 10 - y) - 1;
            for (int x = -width; x <= width; x++) {
                for (int z = -width; z <= width; z++) {
                    if (Math.abs(x) + Math.abs(z) > width) continue;
                    blocks.put(center.add(x, y, z), x == 0 && z == 0 && (y == 6 || y == 7)
                            ? light : BlockTypes.AMETHYST_BLOCK.get().defaultState());
                }
            }
        }
        final BlockState fragment = BlockTypes.CYAN_STAINED_GLASS.get().defaultState();
        for (final int x : new int[] {-5, 5}) {
            for (final int z : new int[] {-3, 3}) {
                blocks.put(center.add(x, 0, z), trim);
                blocks.put(center.add(x, 1, z), BlockTypes.OCHRE_FROGLIGHT.get().defaultState());
                blocks.put(center.add(x, 2, z), copper);
            }
        }
        blocks.put(center.add(-3, 6, 1), fragment);
        blocks.put(center.add(3, 7, -1), fragment);
        blocks.put(center.add(1, 5, 3), fragment);
        blocks.put(center.add(-1, 8, -3), fragment);

        for (final CharacterClass character : CharacterClass.values()) {
            final Vector3i position = plinth(character);
            final BlockState emblem = switch (character) {
                case WARRIOR -> BlockTypes.IRON_BLOCK.get().defaultState();
                case BERSERKER -> BlockTypes.RED_TERRACOTTA.get().defaultState();
                case RANGER -> BlockTypes.GREEN_TERRACOTTA.get().defaultState();
                case ASSASSIN -> BlockTypes.BLACK_TERRACOTTA.get().defaultState();
                case MAGE -> BlockTypes.BLUE_TERRACOTTA.get().defaultState();
                case CLERIC -> BlockTypes.QUARTZ_BLOCK.get().defaultState();
                case PALADIN -> BlockTypes.YELLOW_TERRACOTTA.get().defaultState();
                case SUMMONER -> BlockTypes.PURPLE_TERRACOTTA.get().defaultState();
            };
            blocks.put(center.add(position.add(0, -1, 0)), trim);
            blocks.put(center.add(position), emblem);
            blocks.put(center.add(position.add(0, 0, position.z() > 0 ? -1 : 1)), light);
        }
        return blocks;
    }

    static Optional<CharacterClass> classAt(final Vector3i position) {
        for (final CharacterClass character : CharacterClass.values()) {
            if (position.equals(plinth(character)) || position.equals(plinth(character).add(0, -1, 0))) {
                return Optional.of(character);
            }
        }
        return Optional.empty();
    }

    private static Vector3i plinth(final CharacterClass character) {
        final int index = character.ordinal();
        final int x = switch (index % 4) {
            case 0 -> -7;
            case 1 -> -4;
            case 2 -> 4;
            default -> 7;
        };
        return new Vector3i(x, 1, index < 4 ? -6 : 6);
    }
}
