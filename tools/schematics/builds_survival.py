"""Survival-friendly builds."""
from schemgen import Build, bed, block, door, log, pane, stairs


def oak_cabin():
    """A 7x7x8 cabin: cobblestone footing, oak walls, spruce gable roof with eaves, furnished."""
    b = Build(
        id="oak_cabin",
        name="Oak Cabin",
        description="A snug starter cabin with a bed, furnace, chest and crafting table under a spruce gable roof.",
        name_ru="Дубовая хижина",
        description_ru="Уютная стартовая хижина с кроватью, печью, сундуком и верстаком под двускатной еловой крышей.",
        category="survival",
        difficulty="easy",
        size=(7, 7, 8),
        stages=[
            ("foundation", "Foundation"),
            ("frame", "Frame"),
            ("walls", "Walls"),
            ("roof", "Roof"),
            ("openings", "Windows and door"),
            ("interior", "Interior"),
            ("decoration", "Decoration"),
        ],
    )
    # The walls stand on z = 1..6; z = 0 and z = 7 hold only the roof overhang.
    front, back = 1, 6
    west, east = 0, 6

    # Foundation: cobblestone ring with a plank floor inside.
    b.fill(west, 0, front, east, 0, back, block("cobblestone"), "foundation")
    b.fill(west + 1, 0, front + 1, east - 1, 0, back - 1, block("oak_planks"), "foundation")

    # Frame: a log post in each corner.
    for x in (west, east):
        for z in (front, back):
            b.fill(x, 1, z, x, 3, z, log("oak"), "frame")

    # Walls between the posts, three blocks high.
    b.fill(west + 1, 1, front, east - 1, 3, front, block("oak_planks"), "walls")
    b.fill(west + 1, 1, back, east - 1, 3, back, block("oak_planks"), "walls")
    for x in (west, east):
        b.fill(x, 1, front + 1, x, 3, back - 1, block("oak_planks"), "walls")
        # Gable ends closing the roof.
        b.fill(x, 4, front + 1, x, 4, back - 1, block("oak_planks"), "walls")
        b.fill(x, 5, front + 2, x, 5, back - 2, block("oak_planks"), "walls")

    # Roof: four rows of stairs per side, rising towards the ridge. The lowest row overhangs the wall.
    for step in range(4):
        y = 3 + step
        b.fill(west, y, step, east, y, step, stairs("spruce", "south"), "roof")
        b.fill(west, y, 7 - step, east, y, 7 - step, stairs("spruce", "north"), "roof")

    # Door in the front wall, placed from outside (the player looks south).
    b.set(3, 1, front, door("oak", "south", "lower"), "openings")
    b.set(3, 2, front, door("oak", "south", "upper"), "openings")
    # Windows.
    for x in (1, 5):
        b.set(x, 2, front, pane(east=True, west=True), "openings")
    b.set(3, 2, back, pane(east=True, west=True), "openings")
    for x in (west, east):
        for z in (3, 4):
            b.set(x, 2, z, pane(north=True, south=True), "openings")

    # Interior.
    b.set(5, 1, 4, bed("red", "south", "foot"), "interior")
    b.set(5, 1, 5, bed("red", "south", "head"), "interior")
    b.set(1, 1, 5, block("crafting_table"), "interior")
    b.set(2, 1, 5, block("furnace", facing="north", lit=False), "interior")
    b.set(1, 1, 2, block("chest", facing="east", type="single", waterlogged=False), "interior")
    b.set(3, 3, 5, block("wall_torch", facing="north"), "interior")

    # Lanterns hanging from the eave on both sides of the door.
    for x in (2, 4):
        b.set(x, 2, 0, block("lantern", hanging=True, waterlogged=False), "decoration")

    return b


BUILDS = [oak_cabin]
