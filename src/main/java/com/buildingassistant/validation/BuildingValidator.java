package com.buildingassistant.validation;

import com.buildingassistant.transform.SchematicProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compares a placed build with the blocks that are really in the world and records the result in a
 * {@link ValidationState}.
 *
 * <p>Three ways to check, from cheapest to most thorough: one position after a block change, a slice of
 * the build per tick as a background safety net, and the whole build when it is placed or settings
 * change. It only reads the world and never changes it.
 */
public final class BuildingValidator {

    private final BlockMatcher matcher;
    private final boolean checkClearance;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /**
     * @param checkClearance whether blocks standing in positions the schematic wants free are reported
     */
    public BuildingValidator(BlockMatcher matcher, boolean checkClearance) {
        this.matcher = matcher;
        this.checkClearance = checkClearance;
    }

    public BlockMatcher matcher() {
        return matcher;
    }

    /** Checks every entry. At most 15x15x15 block reads. */
    public void checkAll(ValidationState state, Level level) {
        for (int i = 0; i < state.projection().size(); i++) {
            checkEntry(state, level, i);
        }
        state.setScanCursor(0);
    }

    /**
     * Checks the next {@code count} entries, continuing where the previous call stopped. Repeated calls
     * eventually cover the whole build, catching changes that arrived without a block update, such as a
     * chunk being loaded.
     */
    public void checkSlice(ValidationState state, Level level, int count) {
        int size = state.projection().size();
        if (size == 0) {
            return;
        }
        int index = state.scanCursor() % size;
        for (int n = Math.min(count, size); n > 0; n--) {
            checkEntry(state, level, index);
            index = (index + 1) % size;
        }
        state.setScanCursor(index);
    }

    /**
     * Re-checks the entry at a world position, if the position belongs to the build.
     *
     * @return true when the entry's status changed
     */
    public boolean checkPosition(ValidationState state, Level level, BlockPos pos) {
        int index = state.projection().indexOf(pos);
        return index >= 0 && checkEntry(state, level, index);
    }

    private boolean checkEntry(ValidationState state, Level level, int index) {
        SchematicProjection projection = state.projection();
        cursor.set(projection.positionAt(index));
        BlockState expected = projection.stateAt(index);
        if (!level.isLoaded(cursor)) {
            return state.set(index, expected.isAir() ? ValidationState.CLEAR : ValidationState.UNKNOWN);
        }
        return state.set(index, classify(expected, level.getBlockState(cursor)));
    }

    /** Status of one position given what the schematic expects and what is there. */
    public byte classify(BlockState expected, BlockState actual) {
        boolean free = actual.isAir() || actual.canBeReplaced();
        if (expected.isAir()) {
            return free || !checkClearance ? ValidationState.CLEAR : ValidationState.OBSTRUCTED;
        }
        if (matcher.matches(expected, actual)) {
            return ValidationState.CORRECT;
        }
        // Grass, snow layers or water do not stop the player from placing the block, so the position
        // still counts as empty rather than wrong.
        return free ? ValidationState.MISSING : ValidationState.WRONG;
    }
}
