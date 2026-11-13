"""Writes the empty 3x3x3 structure template used by GameTests.

Usage: python3 tools/make_empty_structure.py <output.nbt>
"""
import gzip
import struct
import sys

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10
DATA_VERSION_1_20_1 = 3465


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag, key, payload):
    return bytes([tag]) + name(key) + payload


def int_list(values):
    return bytes([TAG_INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(compounds):
    return bytes([TAG_COMPOUND]) + struct.pack(">i", len(compounds)) + b"".join(compounds)


def main(path):
    air = named(TAG_STRING, "Name", name("minecraft:air")) + bytes([TAG_END])
    block = named(TAG_LIST, "pos", int_list([0, 0, 0])) + named(TAG_INT, "state", struct.pack(">i", 0)) + bytes([TAG_END])
    body = (
        named(TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION_1_20_1))
        + named(TAG_LIST, "size", int_list([3, 3, 3]))
        + named(TAG_LIST, "palette", compound_list([air]))
        + named(TAG_LIST, "blocks", compound_list([block]))
        + named(TAG_LIST, "entities", bytes([TAG_END]) + struct.pack(">i", 0))
        + bytes([TAG_END])
    )
    with gzip.GzipFile(path, "wb", mtime=0) as out:
        out.write(bytes([TAG_COMPOUND]) + name("") + body)


if __name__ == "__main__":
    main(sys.argv[1])
