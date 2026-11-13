package com.buildingassistant.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups of blocks that may stand in for each other in flexible validation mode, for example all planks.
 *
 * <p>Groups are plain data: each lists block ids and block tags ({@code #namespace:tag}). They are stored
 * in a JSON file in the config folder so players can edit them. Before use the groups are resolved
 * against the registry; that step must be repeated when tags change (joining a world, reloading data).
 */
public final class MaterialGroups {

    /** One bit per group in the lookup mask. */
    public static final int MAX_GROUPS = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final List<Group> groups;
    private final Reference2LongOpenHashMap<Block> masks = new Reference2LongOpenHashMap<>();

    public MaterialGroups(List<Group> groups) {
        this.groups = List.copyOf(groups.size() > MAX_GROUPS ? groups.subList(0, MAX_GROUPS) : groups);
    }

    /** Sensible groups for vanilla: wood variants, wool colours, glass colours and similar. */
    public static MaterialGroups defaults() {
        List<Group> groups = new ArrayList<>();
        groups.add(tag("planks", "minecraft:planks"));
        groups.add(tag("logs", "minecraft:logs"));
        groups.add(tag("wooden_stairs", "minecraft:wooden_stairs"));
        groups.add(tag("wooden_slabs", "minecraft:wooden_slabs"));
        groups.add(tag("wooden_fences", "minecraft:wooden_fences"));
        groups.add(tag("fence_gates", "minecraft:fence_gates"));
        groups.add(tag("wooden_doors", "minecraft:wooden_doors"));
        groups.add(tag("wooden_trapdoors", "minecraft:wooden_trapdoors"));
        groups.add(tag("wooden_buttons", "minecraft:wooden_buttons"));
        groups.add(tag("wooden_pressure_plates", "minecraft:wooden_pressure_plates"));
        groups.add(tag("standing_signs", "minecraft:standing_signs"));
        groups.add(tag("wall_signs", "minecraft:wall_signs"));
        groups.add(tag("leaves", "minecraft:leaves"));
        groups.add(tag("wool", "minecraft:wool"));
        groups.add(tag("carpets", "minecraft:wool_carpets"));
        groups.add(tag("beds", "minecraft:beds"));
        groups.add(tag("candles", "minecraft:candles"));
        groups.add(tag("terracotta", "minecraft:terracotta"));
        groups.add(tag("stone_bricks", "minecraft:stone_bricks"));
        groups.add(tag("glass", "minecraft:impermeable"));
        groups.add(tag("glass_panes", "forge:glass_panes"));
        groups.add(colored("concrete", "_concrete"));
        groups.add(colored("concrete_powder", "_concrete_powder"));
        groups.add(colored("glazed_terracotta", "_glazed_terracotta"));
        return new MaterialGroups(groups);
    }

    /** Reads groups from a file; any problem falls back to the defaults and is reported to the caller. */
    public static LoadedGroups load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new LoadedGroups(defaults(), null);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = root.getAsJsonObject().getAsJsonArray("groups");
            List<Group> groups = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                List<String> entries = new ArrayList<>();
                for (JsonElement entry : object.getAsJsonArray("blocks")) {
                    entries.add(entry.getAsString());
                }
                groups.add(new Group(object.get("id").getAsString(), entries));
            }
            String problem = groups.size() > MAX_GROUPS
                    ? "more than " + MAX_GROUPS + " groups, the rest is ignored" : null;
            return new LoadedGroups(new MaterialGroups(groups), problem);
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException
                 | ClassCastException | UnsupportedOperationException e) {
            return new LoadedGroups(defaults(), "could not be read (" + e + "), using default groups");
        }
    }

    public void save(Path file) throws IOException {
        JsonArray array = new JsonArray();
        for (Group group : groups) {
            JsonObject object = new JsonObject();
            object.addProperty("id", group.id());
            JsonArray blocks = new JsonArray();
            group.entries().forEach(blocks::add);
            object.add("blocks", blocks);
            array.add(object);
        }
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Blocks in one group may replace each other in FLEXIBLE validation mode. "
                + "Entries are block ids or tags (#namespace:tag).");
        root.add("groups", array);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    /** Looks up every id and tag in the registry. Unknown entries are skipped. */
    public void resolve() {
        masks.clear();
        for (int i = 0; i < groups.size(); i++) {
            long bit = 1L << i;
            for (String entry : groups.get(i).entries()) {
                if (entry.startsWith("#")) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                    if (id == null) {
                        continue;
                    }
                    BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, id)).ifPresent(tag -> {
                        for (Holder<Block> holder : tag) {
                            masks.put(holder.value(), masks.getLong(holder.value()) | bit);
                        }
                    });
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(entry);
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                        Block block = BuiltInRegistries.BLOCK.get(id);
                        masks.put(block, masks.getLong(block) | bit);
                    }
                }
            }
        }
    }

    /** Whether two different blocks share at least one group. Requires {@link #resolve()} first. */
    public boolean interchangeable(Block first, Block second) {
        return (masks.getLong(first) & masks.getLong(second)) != 0;
    }

    public List<Group> groups() {
        return groups;
    }

    private static Group tag(String id, String tag) {
        return new Group(id, List.of("#" + tag));
    }

    private static Group colored(String id, String suffix) {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        List<String> entries = new ArrayList<>(colors.length);
        for (String color : colors) {
            entries.add("minecraft:" + color + suffix);
        }
        return new Group(id, entries);
    }

    /**
     * @param id      name of the group, only for people reading the file
     * @param entries block ids and {@code #tags}
     */
    public record Group(String id, List<String> entries) {
        public Group {
            entries = List.copyOf(entries);
        }
    }

    /**
     * @param groups  the loaded groups, or the defaults when loading failed
     * @param problem what went wrong, or null
     */
    public record LoadedGroups(MaterialGroups groups, String problem) {
    }
}
