package com.buildingassistant.schematic;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Items a player needs to build a schematic in Survival.
 *
 * <p>Counting follows how blocks are actually placed: a door or bed is one item for two blocks, a double
 * slab is two slabs, a water source is one bucket, and blocks that have no item of their own are mapped
 * to what the player places instead (farmland is dirt, a filled cauldron is a cauldron).
 */
public final class MaterialList {

    /** Blocks whose item differs from the block the player ends up with. */
    private static final Map<Block, Item> PLACED_AS = Map.ofEntries(
            Map.entry(Blocks.FARMLAND, Items.DIRT),
            Map.entry(Blocks.DIRT_PATH, Items.DIRT),
            Map.entry(Blocks.WATER_CAULDRON, Items.CAULDRON),
            Map.entry(Blocks.LAVA_CAULDRON, Items.CAULDRON),
            Map.entry(Blocks.POWDER_SNOW_CAULDRON, Items.CAULDRON),
            Map.entry(Blocks.TALL_SEAGRASS, Items.SEAGRASS),
            Map.entry(Blocks.KELP_PLANT, Items.KELP),
            Map.entry(Blocks.BAMBOO_SAPLING, Items.BAMBOO),
            Map.entry(Blocks.CAVE_VINES_PLANT, Items.GLOW_BERRIES),
            Map.entry(Blocks.WEEPING_VINES_PLANT, Items.WEEPING_VINES),
            Map.entry(Blocks.TWISTING_VINES_PLANT, Items.TWISTING_VINES),
            Map.entry(Blocks.ATTACHED_MELON_STEM, Items.MELON_SEEDS),
            Map.entry(Blocks.ATTACHED_PUMPKIN_STEM, Items.PUMPKIN_SEEDS));

    private final List<Entry> entries;
    private final int totalItems;
    private final int blocksWithoutItem;

    private MaterialList(List<Entry> entries, int totalItems, int blocksWithoutItem) {
        this.entries = entries;
        this.totalItems = totalItems;
        this.blocksWithoutItem = blocksWithoutItem;
    }

    public static MaterialList of(Schematic schematic) {
        Reference2IntOpenHashMap<Item> counts = new Reference2IntOpenHashMap<>();
        int[] withoutItem = new int[1];
        schematic.forEachBlock((x, y, z, state, stage) -> {
            if (!count(state, counts)) {
                withoutItem[0]++;
            }
        });

        List<Entry> entries = new ArrayList<>(counts.size());
        int total = 0;
        for (Reference2IntMap.Entry<Item> entry : counts.reference2IntEntrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getIntValue()));
            total += entry.getIntValue();
        }
        entries.sort(Comparator.comparingInt(Entry::count).reversed()
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.item()).toString()));
        return new MaterialList(List.copyOf(entries), total, withoutItem[0]);
    }

    /** Entries sorted by count, largest first. */
    public List<Entry> entries() {
        return entries;
    }

    public int totalItems() {
        return totalItems;
    }

    /** Blocks that cannot be obtained as an item, such as fire. They are not listed. */
    public int blocksWithoutItem() {
        return blocksWithoutItem;
    }

    public int countOf(Item item) {
        for (Entry entry : entries) {
            if (entry.item() == item) {
                return entry.count();
            }
        }
        return 0;
    }

    /** @return false when the block needs an item that does not exist */
    private static boolean count(BlockState state, Reference2IntOpenHashMap<Item> counts) {
        if (state.isAir()) {
            return true;
        }
        Block block = state.getBlock();

        // The second block of a two-block object is placed together with the first one.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return true;
        }

        if (block instanceof LiquidBlock) {
            FluidState fluid = state.getFluidState();
            if (fluid.isSource()) {
                counts.addTo(fluid.getType().getBucket(), 1);
            }
            // Flowing liquid spreads on its own.
            return true;
        }
        if (block instanceof FlowerPotBlock pot) {
            counts.addTo(Items.FLOWER_POT, 1);
            Item plant = pot.getContent().asItem();
            if (plant != Items.AIR) {
                counts.addTo(plant, 1);
            }
            return true;
        }

        int amount = 1;
        if (block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            amount = 2;
        } else if (block instanceof CandleBlock) {
            amount = state.getValue(CandleBlock.CANDLES);
        } else if (block instanceof SeaPickleBlock) {
            amount = state.getValue(SeaPickleBlock.PICKLES);
        } else if (block instanceof SnowLayerBlock) {
            amount = state.getValue(SnowLayerBlock.LAYERS);
        }

        Item item = PLACED_AS.getOrDefault(block, block.asItem());
        if (item == Items.AIR) {
            return false;
        }
        counts.addTo(item, amount);
        return true;
    }

    public record Entry(Item item, int count) {
    }
}
