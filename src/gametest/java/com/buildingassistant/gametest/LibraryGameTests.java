package com.buildingassistant.gametest;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.library.SchematicLibrary;
import com.buildingassistant.schematic.MaterialList;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.transform.SchematicProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.buildingassistant.gametest.TestSupport.check;
import static com.buildingassistant.gametest.TestSupport.checkEquals;

/** Quality gate for the schematics shipped with the mod. */
@GameTestHolder(BuildingAssistant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LibraryGameTests {

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void everyBuiltinSchematicIsValid(GameTestHelper helper) {
        List<String> problems = SchematicLibrary.loadNow();
        check(problems.isEmpty(), "built-in schematics were rejected: " + problems);
        check(SchematicLibrary.isLoaded(), "library must report being loaded");
        check(!SchematicLibrary.all().isEmpty(), "the library must not be empty");

        Set<String> names = new HashSet<>();
        for (Schematic schematic : SchematicLibrary.all()) {
            String id = schematic.id();
            check(schematic.sizeX() <= SchematicFormat.MAX_SIZE && schematic.sizeY() <= SchematicFormat.MAX_SIZE
                    && schematic.sizeZ() <= SchematicFormat.MAX_SIZE, id + " exceeds the size limit");
            check(schematic.stages().size() >= 2, id + " needs at least two build stages");
            check(schematic.requiredBlockCount() > 0, id + " has no blocks to place");
            check(!schematic.metadata().description().isBlank(), id + " has no description");
            check(names.add(schematic.metadata().name()), id + " repeats the name of another schematic");
            checkEquals(schematic, SchematicLibrary.get(id).orElse(null), "lookup of " + id);

            MaterialList materials = MaterialList.of(schematic);
            check(!materials.entries().isEmpty(), id + " has an empty material list");
            checkEquals(0, materials.blocksWithoutItem(), id + ": blocks that cannot be obtained as items");

            // The build must be placeable in every orientation without losing or duplicating blocks.
            for (Rotation rotation : Rotation.values()) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    SchematicProjection projection = SchematicProjection.create(
                            schematic, new Placement(BlockPos.ZERO, rotation, mirrored));
                    checkEquals(schematic.entryCount(), projection.size(), id + " entry count when placed");
                    check(projection.transformWarnings().isEmpty(),
                            id + " has blocks that cannot be rotated: " + projection.transformWarnings());
                    Set<Long> positions = new HashSet<>();
                    for (int i = 0; i < projection.size(); i++) {
                        check(positions.add(projection.positionAt(i)), id + " places two blocks at one position");
                    }
                    checkEquals(projection.size(), projection.layerEnd(projection.layerCount() - 1),
                            id + " layer index");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void projectionLayersAreContiguous(GameTestHelper helper) {
        SchematicLibrary.loadNow();
        for (Schematic schematic : SchematicLibrary.all()) {
            SchematicProjection projection = SchematicProjection.create(schematic, Placement.at(new BlockPos(5, 70, 5)));
            checkEquals(schematic.sizeY(), projection.layerCount(), schematic.id() + " layer count");
            int expectedStart = 0;
            for (int y = 0; y < projection.layerCount(); y++) {
                checkEquals(expectedStart, projection.layerStart(y), schematic.id() + " start of layer " + y);
                for (int i = projection.layerStart(y); i < projection.layerEnd(y); i++) {
                    checkEquals(y, projection.layerAt(i), schematic.id() + " layer of entry " + i);
                    checkEquals(70 + y, BlockPos.getY(projection.positionAt(i)), schematic.id() + " height of entry " + i);
                }
                expectedStart = projection.layerEnd(y);
            }
            checkEquals(projection.size(), expectedStart, schematic.id() + " total entries");
        }
        helper.succeed();
    }
}
