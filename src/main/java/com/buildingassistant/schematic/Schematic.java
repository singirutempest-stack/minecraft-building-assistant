package com.buildingassistant.schematic;

import com.buildingassistant.schematic.format.PreviewInfo;
import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.format.SchematicMetadata;
import com.buildingassistant.schematic.format.StageDefinition;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A loaded, immutable schematic with resolved block states.
 *
 * <p>Blocks are kept in two dense arrays indexed by cell (one palette index and one stage index per cell),
 * which is compact for the 15x15x15 limit and gives constant-time lookups.
 *
 * <p>A cell is one of three kinds:
 * <ul>
 *   <li>empty: not part of the schematic, never checked;</li>
 *   <li>required: a real block the player has to place;</li>
 *   <li>clearance: explicitly stored air, meaning the position should stay free. It never counts
 *       towards progress.</li>
 * </ul>
 */
public final class Schematic {

    private static final short EMPTY = -1;

    private final SchematicMetadata metadata;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] palette;
    private final List<StageDefinition> stages;
    private final short[] paletteIndices;
    private final byte[] stageIndices;
    private final PreviewInfo preview;
    private final int[] floors;

    private final int entryCount;
    private final int requiredBlockCount;
    /** Required blocks per stage; the last element counts blocks of the common stage. */
    private final int[] requiredPerStage;
    private final int[] requiredPerLayer;
    private final int[] firstLayerPerStage;

    private Schematic(Builder builder) {
        this.metadata = builder.metadata;
        this.sizeX = builder.sizeX;
        this.sizeY = builder.sizeY;
        this.sizeZ = builder.sizeZ;
        this.palette = builder.palette.toArray(BlockState[]::new);
        this.stages = List.copyOf(builder.stages);
        this.paletteIndices = builder.paletteIndices;
        this.stageIndices = builder.stageIndices;
        this.preview = builder.preview;
        this.floors = builder.floors.clone();

        int entries = 0;
        int required = 0;
        int[] perStage = new int[stages.size() + 1];
        int[] perLayer = new int[sizeY];
        int[] firstLayers = new int[stages.size() + 1];
        Arrays.fill(firstLayers, -1);
        for (int cell = 0; cell < paletteIndices.length; cell++) {
            short paletteIndex = paletteIndices[cell];
            if (paletteIndex == EMPTY) {
                continue;
            }
            entries++;
            if (palette[paletteIndex].isAir()) {
                continue;
            }
            required++;
            int stage = stageIndices[cell];
            int stageSlot = stage == SchematicFormat.COMMON_STAGE ? stages.size() : stage;
            int layer = cell / (sizeX * sizeZ);
            perStage[stageSlot]++;
            perLayer[layer]++;
            // Cells are visited from the bottom layer up, so the first hit is the lowest layer.
            if (firstLayers[stageSlot] < 0) {
                firstLayers[stageSlot] = layer;
            }
        }
        this.firstLayerPerStage = firstLayers;
        this.entryCount = entries;
        this.requiredBlockCount = required;
        this.requiredPerStage = perStage;
        this.requiredPerLayer = perLayer;
    }

    public SchematicMetadata metadata() {
        return metadata;
    }

    public String id() {
        return metadata.id();
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<StageDefinition> stages() {
        return stages;
    }

    public PreviewInfo preview() {
        return preview;
    }

    public List<BlockState> palette() {
        return List.of(palette);
    }

    /** Number of stored cells, including clearance cells. */
    public int entryCount() {
        return entryCount;
    }

    /** Number of blocks the player has to place. */
    public int requiredBlockCount() {
        return requiredBlockCount;
    }

    /** Required blocks in a stage; pass {@link SchematicFormat#COMMON_STAGE} for shared blocks. */
    public int requiredBlocksInStage(int stage) {
        return requiredPerStage[stage == SchematicFormat.COMMON_STAGE ? stages.size() : stage];
    }

    public int requiredBlocksInLayer(int y) {
        return requiredPerLayer[y];
    }

    /** Lowest layer containing a block of the stage, or -1 when the stage has none. */
    public int firstLayerOfStage(int stage) {
        return firstLayerPerStage[stage == SchematicFormat.COMMON_STAGE ? stages.size() : stage];
    }

    /** Index of the stage with the given id, or -1. */
    public int stageIndex(String stageId) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).id().equals(stageId)) {
                return i;
            }
        }
        return -1;
    }

    /** Layers on which a storey begins, from bottom to top. Never empty. */
    public int[] floors() {
        return floors.clone();
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    /** Block state at a local position, or {@code null} when the cell is empty or out of bounds. */
    @Nullable
    public BlockState stateAt(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return null;
        }
        short paletteIndex = paletteIndices[cellIndex(x, y, z)];
        return paletteIndex == EMPTY ? null : palette[paletteIndex];
    }

    /** Stage index at a local position; only meaningful when {@link #stateAt} is not null. */
    public int stageAt(int x, int y, int z) {
        return stageIndices[cellIndex(x, y, z)];
    }

    /** Visits every stored cell in layer order (bottom to top). */
    public void forEachBlock(BlockVisitor visitor) {
        int cell = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, cell++) {
                    short paletteIndex = paletteIndices[cell];
                    if (paletteIndex != EMPTY) {
                        visitor.visit(x, y, z, palette[paletteIndex], stageIndices[cell]);
                    }
                }
            }
        }
    }

    /** Converts back to the on-disk form. */
    public RawSchematic toRaw() {
        List<String> paletteStrings = new ArrayList<>(palette.length);
        for (BlockState state : palette) {
            paletteStrings.add(BlockStateParser.serialize(state));
        }
        int[] blocks = new int[entryCount * SchematicFormat.BLOCK_STRIDE];
        int offset = 0;
        int cell = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, cell++) {
                    short paletteIndex = paletteIndices[cell];
                    if (paletteIndex == EMPTY) {
                        continue;
                    }
                    blocks[offset++] = x;
                    blocks[offset++] = y;
                    blocks[offset++] = z;
                    blocks[offset++] = paletteIndex;
                    blocks[offset++] = stageIndices[cell];
                }
            }
        }
        return new RawSchematic(SchematicFormat.FORMAT_VERSION, metadata, sizeX, sizeY, sizeZ,
                paletteStrings, stages, blocks, preview, floors.clone());
    }

    private int cellIndex(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    @FunctionalInterface
    public interface BlockVisitor {
        void visit(int x, int y, int z, BlockState state, int stage);
    }

    /** Assembles a schematic cell by cell. Sizes and stage indices are expected to be validated by the caller. */
    public static final class Builder {

        private final SchematicMetadata metadata;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final List<StageDefinition> stages;
        private final List<BlockState> palette = new ArrayList<>();
        private final Reference2IntOpenHashMap<BlockState> paletteLookup = new Reference2IntOpenHashMap<>();
        private final short[] paletteIndices;
        private final byte[] stageIndices;
        private PreviewInfo preview = PreviewInfo.DEFAULT;
        private int[] floors = RawSchematic.SINGLE_FLOOR;

        public Builder(SchematicMetadata metadata, int sizeX, int sizeY, int sizeZ, List<StageDefinition> stages) {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1
                    || sizeX > SchematicFormat.MAX_SIZE || sizeY > SchematicFormat.MAX_SIZE || sizeZ > SchematicFormat.MAX_SIZE) {
                throw new IllegalArgumentException("Schematic size out of range: " + sizeX + "x" + sizeY + "x" + sizeZ);
            }
            if (stages.isEmpty() || stages.size() > SchematicFormat.MAX_STAGES) {
                throw new IllegalArgumentException("Schematic needs 1-" + SchematicFormat.MAX_STAGES + " stages");
            }
            this.metadata = metadata;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.stages = stages;
            this.paletteIndices = new short[sizeX * sizeY * sizeZ];
            this.stageIndices = new byte[sizeX * sizeY * sizeZ];
            Arrays.fill(paletteIndices, EMPTY);
            paletteLookup.defaultReturnValue(-1);
        }

        public Builder preview(PreviewInfo preview) {
            this.preview = preview;
            return this;
        }

        /** Layers on which a storey begins; expected to be validated by the caller. */
        public Builder floors(int[] floors) {
            this.floors = floors.length == 0 ? RawSchematic.SINGLE_FLOOR : floors;
            return this;
        }

        /**
         * Stores a block. Air is stored as a clearance cell.
         *
         * @return false when the position is out of bounds, already taken, or the stage does not exist
         */
        public boolean set(int x, int y, int z, BlockState state, int stage) {
            if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                return false;
            }
            if (stage != SchematicFormat.COMMON_STAGE && (stage < 0 || stage >= stages.size())) {
                return false;
            }
            int cell = (y * sizeZ + z) * sizeX + x;
            if (paletteIndices[cell] != EMPTY) {
                return false;
            }
            int paletteIndex = paletteLookup.getInt(state);
            if (paletteIndex < 0) {
                paletteIndex = palette.size();
                palette.add(state);
                paletteLookup.put(state, paletteIndex);
            }
            paletteIndices[cell] = (short) paletteIndex;
            stageIndices[cell] = (byte) stage;
            return true;
        }

        public Schematic build() {
            return new Schematic(this);
        }
    }
}
