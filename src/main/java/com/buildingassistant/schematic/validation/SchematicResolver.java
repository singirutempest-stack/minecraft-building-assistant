package com.buildingassistant.schematic.validation;

import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Turns a structurally valid {@link RawSchematic} into a {@link Schematic} by resolving every palette
 * string against the block registry. Unknown blocks and invalid properties are reported, never thrown.
 */
public final class SchematicResolver {

    private SchematicResolver() {
    }

    /** Parses a block state string such as {@code minecraft:oak_stairs[facing=east]}. */
    public static BlockState parseBlockState(String text) throws CommandSyntaxException {
        return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
    }

    /**
     * @param raw a schematic that already passed {@link RawSchematicValidator}
     * @return the resolved schematic, or {@code null} when any palette entry is invalid
     */
    @Nullable
    public static Schematic resolve(RawSchematic raw, IssueCollector issues) {
        List<String> paletteStrings = raw.palette();
        BlockState[] states = new BlockState[paletteStrings.size()];
        boolean[] used = new boolean[paletteStrings.size()];
        int[] blocks = raw.blocks();
        for (int offset = 0; offset < blocks.length; offset += SchematicFormat.BLOCK_STRIDE) {
            used[blocks[offset + 3]] = true;
        }

        boolean valid = true;
        for (int i = 0; i < states.length; i++) {
            if (!used[i]) {
                issues.warn("Palette entry " + i + " '" + paletteStrings.get(i) + "' is never used");
                continue;
            }
            try {
                states[i] = parseBlockState(paletteStrings.get(i));
            } catch (CommandSyntaxException e) {
                issues.error("Palette entry " + i + " '" + paletteStrings.get(i) + "' is not a valid block state: "
                        + e.getMessage());
                valid = false;
            }
        }
        if (!valid) {
            return null;
        }

        Schematic.Builder builder = new Schematic.Builder(
                raw.metadata(), raw.sizeX(), raw.sizeY(), raw.sizeZ(), raw.stages()).preview(raw.preview()).floors(raw.floors());
        for (int offset = 0; offset < blocks.length; offset += SchematicFormat.BLOCK_STRIDE) {
            boolean stored = builder.set(blocks[offset], blocks[offset + 1], blocks[offset + 2],
                    states[blocks[offset + 3]], blocks[offset + 4]);
            if (!stored) {
                // Cannot happen after structural validation; reported instead of asserted to stay crash-free.
                issues.error("Block at (" + blocks[offset] + ", " + blocks[offset + 1] + ", " + blocks[offset + 2]
                        + ") could not be stored");
                return null;
            }
        }
        return builder.build();
    }
}
