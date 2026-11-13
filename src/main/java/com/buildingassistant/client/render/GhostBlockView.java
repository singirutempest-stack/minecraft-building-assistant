package com.buildingassistant.client.render;

import com.buildingassistant.transform.SchematicProjection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * A pretend world that contains only the ghost blocks of one build, addressed relative to the build's
 * anchor. The block renderer reads neighbours from it, so faces between two ghost blocks are culled and
 * ambient occlusion is computed between them, exactly as for real blocks.
 *
 * <p>Everything is fully lit: ghosts must stay readable at night and underground.
 */
final class GhostBlockView implements BlockAndTintGetter {

    private static final int FULL_LIGHT = 15;

    private final SchematicProjection projection;
    private final ClientLevel level;
    private final BlockPos anchor;

    GhostBlockView(SchematicProjection projection, ClientLevel level) {
        this.projection = projection;
        this.level = level;
        this.anchor = projection.placement().anchor();
    }

    @Override
    public BlockState getBlockState(BlockPos relative) {
        int index = projection.indexOf(BlockPos.asLong(
                anchor.getX() + relative.getX(), anchor.getY() + relative.getY(), anchor.getZ() + relative.getZ()));
        return index < 0 ? Blocks.AIR.defaultBlockState() : projection.stateAt(index);
    }

    @Override
    public FluidState getFluidState(BlockPos relative) {
        return getBlockState(relative).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos relative) {
        return null;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos relative, ColorResolver resolver) {
        // Grass and leaves take the colour of the biome the build stands in.
        return level.getBlockTint(anchor.offset(relative), resolver);
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos relative) {
        return FULL_LIGHT;
    }

    @Override
    public int getRawBrightness(BlockPos relative, int skyDarken) {
        return FULL_LIGHT;
    }

    @Override
    public boolean canSeeSky(BlockPos relative) {
        return true;
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
