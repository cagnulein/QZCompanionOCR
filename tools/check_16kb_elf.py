#!/usr/bin/env python3
"""Fail CI if a 64-bit native library in the App Bundle has 4 KB ELF load alignment."""
import pathlib
import struct
import sys
import zipfile

PAGE_SIZE = 16 * 1024
ABIS = ("/lib/arm64-v8a/", "/lib/x86_64/")


def load_alignments(blob: bytes):
    if blob[:4] != b"\x7fELF":
        raise ValueError("Not an ELF binary")
    if blob[4] != 2:
        raise ValueError("Expected ELF64")
    endian = {1: "<", 2: ">"}.get(blob[5])
    if endian is None:
        raise ValueError("Unknown ELF byte order")
    phoff = struct.unpack_from(endian + "Q", blob, 32)[0]
    phentsize = struct.unpack_from(endian + "H", blob, 54)[0]
    phnum = struct.unpack_from(endian + "H", blob, 56)[0]
    for index in range(phnum):
        offset = phoff + index * phentsize
        if struct.unpack_from(endian + "I", blob, offset)[0] == 1:  # PT_LOAD
            yield struct.unpack_from(endian + "Q", blob, offset + 48)[0]


def main(bundle: pathlib.Path):
    incompatible = []
    found = 0
    with zipfile.ZipFile(bundle) as archive:
        for name in archive.namelist():
            if not name.endswith(".so") or not any(abi in name for abi in ABIS):
                continue
            found += 1
            try:
                aligns = list(load_alignments(archive.read(name)))
                if not aligns or any(align < PAGE_SIZE for align in aligns):
                    incompatible.append((name, aligns))
                else:
                    print(f"OK: {name} / {[hex(a) for a in aligns]}")
            except (ValueError, struct.error) as exc:
                incompatible.append((name, str(exc)))
    print(f"Checked {found} 64-bit native libraries")
    if incompatible:
        for name, alignments in incompatible:
            print(f"NOT 16 KB compatible: {name} / {alignments}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: check_16kb_elf.py app-release.aab")
    sys.exit(main(pathlib.Path(sys.argv[1])))
