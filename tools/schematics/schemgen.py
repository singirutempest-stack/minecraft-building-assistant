"""Building blocks for describing the mod's built-in schematics in code.

Every built-in build is written as a small Python function that places blocks into a `Build`.
`generate.py` turns those into the JSON files shipped with the mod. Describing builds in code keeps
them reviewable and reproducible, and every block's position and state is exact by construction.

Coordinates follow Minecraft: +X is east, +Y is up, +Z is south.
"""
import json
import re

MAX_SIZE = 15
FORMAT_VERSION = 1
COMMON_STAGE = -1
ID_PATTERN = re.compile(r"[a-z0-9_]{1,64}")

CATEGORIES = {
    "medieval", "fantasy", "modern", "houses", "farms", "decorations",
    "industrial", "interior", "storage", "survival",
}
DIFFICULTIES = {"easy", "medium", "hard"}


class BuildError(Exception):
    pass


class Build:
    """A schematic under construction. Later calls overwrite earlier ones at the same position."""

    def __init__(self, id, name, description, category, difficulty, size, stages,
                 name_ru=None, description_ru=None, yaw=215.0, pitch=25.0):
        self.id = id
        self.name = name
        self.description = description
        self.name_ru = name_ru or name
        self.description_ru = description_ru or description
        self.category = category
        self.difficulty = difficulty
        self.size = tuple(size)
        self.stages = list(stages)  # list of (id, English name)
        self.yaw = yaw
        self.pitch = pitch
        self.cells = {}
        self._stage_index = {stage_id: i for i, (stage_id, _) in enumerate(self.stages)}

    def set(self, x, y, z, state, stage):
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise BuildError(f"{self.id}: block at {(x, y, z)} is outside size {self.size}")
        if stage is not None and stage not in self._stage_index:
            raise BuildError(f"{self.id}: unknown stage '{stage}'")
        self.cells[(x, y, z)] = (state, stage)

    def fill(self, x1, y1, z1, x2, y2, z2, state, stage):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                for x in range(min(x1, x2), max(x1, x2) + 1):
                    self.set(x, y, z, state, stage)

    def remove(self, x, y, z):
        self.cells.pop((x, y, z), None)

    def validate(self):
        if not ID_PATTERN.fullmatch(self.id):
            raise BuildError(f"invalid id '{self.id}'")
        if self.category not in CATEGORIES:
            raise BuildError(f"{self.id}: unknown category '{self.category}'")
        if self.difficulty not in DIFFICULTIES:
            raise BuildError(f"{self.id}: unknown difficulty '{self.difficulty}'")
        if any(not 1 <= extent <= MAX_SIZE for extent in self.size):
            raise BuildError(f"{self.id}: size {self.size} exceeds {MAX_SIZE}")
        if not self.cells:
            raise BuildError(f"{self.id}: no blocks")
        # The declared size must be exactly the space the build occupies.
        for axis, name in enumerate("xyz"):
            used = {position[axis] for position in self.cells}
            if min(used) != 0 or max(used) != self.size[axis] - 1:
                raise BuildError(f"{self.id}: size on {name} is {self.size[axis]} but blocks span "
                                 f"{min(used)}..{max(used)}")
        used_stages = {stage for _, stage in self.cells.values() if stage is not None}
        for stage_id, _ in self.stages:
            if stage_id not in used_stages:
                raise BuildError(f"{self.id}: stage '{stage_id}' has no blocks")

    def to_json(self):
        self.validate()
        ordered = sorted(self.cells.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))
        palette = []
        palette_index = {}
        rows = []
        for (x, y, z), (state, stage) in ordered:
            if state not in palette_index:
                palette_index[state] = len(palette)
                palette.append(state)
            stage_index = COMMON_STAGE if stage is None else self._stage_index[stage]
            rows.append(f"    [{x}, {y}, {z}, {palette_index[state]}, {stage_index}]")

        def dump(value):
            return json.dumps(value, ensure_ascii=False)

        metadata = {
            "id": self.id,
            "name": self.name,
            "description": self.description,
            "category": self.category,
            "difficulty": self.difficulty,
            "author": "Building Assistant",
            "source": "original",
            "license": "",
        }
        lines = [
            "{",
            f'  "format_version": {FORMAT_VERSION},',
            f'  "metadata": {dump(metadata)},',
            f'  "dimensions": {{"x": {self.size[0]}, "y": {self.size[1]}, "z": {self.size[2]}}},',
            '  "palette": [',
            ",\n".join(f"    {dump(state)}" for state in palette),
            "  ],",
            '  "stages": [',
            ",\n".join(f"    {dump({'id': stage_id, 'name': name})}" for stage_id, name in self.stages),
            "  ],",
            f'  "preview": {dump({"yaw": self.yaw, "pitch": self.pitch})},',
            '  "blocks": [',
            ",\n".join(rows),
            "  ]",
            "}",
        ]
        return "\n".join(lines) + "\n"


# --- Block state helpers -------------------------------------------------------------------------

def block(name, **properties):
    """Formats a block state string, e.g. block("oak_stairs", facing="north")."""
    if not properties:
        return f"minecraft:{name}"
    inner = ",".join(f"{key}={str(value).lower()}" for key, value in sorted(properties.items()))
    return f"minecraft:{name}[{inner}]"


def stairs(material, facing, half="bottom"):
    """Stairs whose tall side points towards `facing`."""
    return block(f"{material}_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(material, type="bottom"):
    return block(f"{material}_slab", type=type, waterlogged=False)


def log(material, axis="y"):
    return block(f"{material}_log", axis=axis)


def pane(name="glass_pane", north=False, east=False, south=False, west=False):
    return block(name, north=north, east=east, south=south, west=west, waterlogged=False)


def door(material, facing, half, hinge="left"):
    """A closed door. `facing` is the direction the player looked when placing it."""
    return block(f"{material}_door", facing=facing, half=half, hinge=hinge, open=False, powered=False)


def bed(color, facing, part):
    """`facing` points from the foot of the bed to its head."""
    return block(f"{color}_bed", facing=facing, part=part, occupied=False)
