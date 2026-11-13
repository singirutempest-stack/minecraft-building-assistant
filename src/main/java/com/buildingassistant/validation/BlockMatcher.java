package com.buildingassistant.validation;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Decides whether the block found in the world satisfies the block the schematic expects.
 *
 * <p>Strict mode requires the same block with the same state. Flexible mode additionally accepts a
 * different block from the same {@link MaterialGroups material group}, as long as the properties both
 * blocks share (facing, half, axis and so on) still agree, so a spruce stair facing the wrong way is
 * still an error.
 *
 * <p>Two kinds of properties are never compared: the ones the player listed in the config, and the ones
 * the game derives from neighbouring blocks (fence connections, stair corner shape). The latter change
 * while a build is incomplete and would otherwise flag correctly placed blocks.
 *
 * <p>Instances are immutable snapshots of the settings; create a new one when settings change.
 */
public final class BlockMatcher {

    private static final Set<String> CONNECTION_PROPERTIES = Set.of("north", "east", "south", "west", "up");

    private final ValidationMode mode;
    private final Set<String> ignoredProperties;
    private final MaterialGroups groups;
    /** Per block: the properties that take part in comparisons. */
    private final Reference2ObjectOpenHashMap<Block, Property<?>[]> comparedProperties = new Reference2ObjectOpenHashMap<>();

    public BlockMatcher(ValidationMode mode, Collection<String> ignoredProperties, MaterialGroups groups) {
        this.mode = mode;
        this.ignoredProperties = Set.copyOf(ignoredProperties);
        this.groups = groups;
    }

    public ValidationMode mode() {
        return mode;
    }

    public boolean matches(BlockState expected, BlockState actual) {
        return compare(expected, actual) == Result.MATCH;
    }

    public Result compare(BlockState expected, BlockState actual) {
        if (expected == actual) {
            return Result.MATCH;
        }
        Block expectedBlock = expected.getBlock();
        Block actualBlock = actual.getBlock();
        if (expectedBlock == actualBlock) {
            for (Property<?> property : compared(expectedBlock)) {
                if (!expected.getValue(property).equals(actual.getValue(property))) {
                    return Result.WRONG_STATE;
                }
            }
            return Result.MATCH;
        }
        if (mode != ValidationMode.FLEXIBLE || !groups.interchangeable(expectedBlock, actualBlock)) {
            return Result.WRONG_BLOCK;
        }
        // Different blocks have different property objects, so shared properties are matched by name.
        for (Property<?> property : compared(expectedBlock)) {
            Property<?> counterpart = actualBlock.getStateDefinition().getProperty(property.getName());
            if (counterpart != null && !valueName(expected, property).equals(valueName(actual, counterpart))) {
                return Result.WRONG_STATE;
            }
        }
        return Result.MATCH;
    }

    private Property<?>[] compared(Block block) {
        Property<?>[] cached = comparedProperties.get(block);
        if (cached == null) {
            List<Property<?>> list = new ArrayList<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                if (!ignoredProperties.contains(property.getName()) && !isDerivedFromNeighbours(block, property)) {
                    list.add(property);
                }
            }
            cached = list.toArray(Property<?>[]::new);
            comparedProperties.put(block, cached);
        }
        return cached;
    }

    private static boolean isDerivedFromNeighbours(Block block, Property<?> property) {
        String name = property.getName();
        if (block instanceof StairBlock) {
            return name.equals("shape");
        }
        if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof RedStoneWireBlock) {
            return CONNECTION_PROPERTIES.contains(name);
        }
        return false;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public enum Result {
        MATCH,
        /** Right block, wrong orientation or other state. */
        WRONG_STATE,
        WRONG_BLOCK
    }
}
