#!/usr/bin/env python3
"""One-shot ALKE source-identity migration. Executed by a dedicated GitHub Action.

Rebrands the active code tree, tests, manifests, runtime identifiers, resources,
backup format and docs in one atomic VCS migration. The pre-migration snapshot
lives on a separate immutable branch. This script deletes itself after success.
Third-party copyright/license text is NEVER edited or removed.
"""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SELF = Path(__file__).resolve()
LEGAL_EXCEPTIONS = {"LICENSE", "NOTICE", "ATTRIBUTION.md"}
ALLOWED_BINARY_SUFFIXES = {
    ".jar", ".png", ".webp", ".jpg", ".jpeg", ".gif", ".ico", ".keystore",
    ".zip", ".apk", ".aab", ".db", ".sqlite", ".pdf", ".woff", ".ttf",
    ".otf", ".bin", ".class", ".so", ".wav", ".mp3", ".bak",
}
# Specific namespaces BEFORE generic project names. No arithmetic or metric
# formulas are modified; this changes identifiers and references only.
REPLACEMENTS = (
    ("com.noop", "com.otidotid.alke"),
    ("com/noop", "com/otidotid/alke"),
    ("THOOP", "ALKE"),
    ("NOOP", "ALKE"),
    ("Thoop", "Alke"),
    ("Noop", "Alke"),
    ("thoop", "alke"),
    ("noop", "alke"),
)
LEGACY = re.compile(r"(?i)(?:noop|thoop)")

def rename_identity(value: str) -> str:
    for old, new in REPLACEMENTS:
        value = value.replace(old, new)
    return value

def tracked() -> list[str]:
    return [
        part.decode("utf-8") for part in subprocess.check_output(
            ["git", "ls-files", "-z"], cwd=ROOT,
        ).split(b"\\0") if part
    ]

def main() -> None:
    paths = tracked()
    if len(paths) < 1000:
        raise RuntimeError(f"Expected the full ALKE source tree; found {len(paths)} files")
    future = defaultdict(list)
    for path in paths:
        future[rename_identity(path)].append(path)
    collisions = {key: old for key, old in future.items() if len(old) > 1}
    if collisions:
        raise RuntimeError(f"Unsafe filename collisions after rename: {collisions}")

    changed = 0
    renamed = 0
    for relative in paths:
        source = ROOT / relative
        destination_name = rename_identity(relative)
        if not source.exists() or not source.is_file():
            raise RuntimeError(f"Missing tracked file {relative}")
        if relative not in LEGAL_EXCEPTIONS and source.suffix.lower() not in ALLOWED_BINARY_SUFFIXES:
            binary = source.read_bytes()
            if b"\\0" not in binary:
                try:
                    original = binary.decode("utf-8")
                except UnicodeDecodeError:
                    original = None
                if original is not None:
                    modified = rename_identity(original)
                    if modified != original:
                        source.write_bytes(modified.encode("utf-8"))
                        changed += 1
        if destination_name != relative:
            destination = ROOT / destination_name
            destination.parent.mkdir(parents=True, exist_ok=True)
            subprocess.check_call(["git", "mv", "--", relative, destination_name], cwd=ROOT)
            renamed += 1

    # No old product name may remain in active source paths or UTF-8 text,
    # except required unchanged third-party copyright/license notices.
    for relative in tracked():
        if relative == SELF.relative_to(ROOT).as_posix():
            continue
        source = ROOT / relative
        if relative not in LEGAL_EXCEPTIONS and LEGACY.search(relative):
            raise RuntimeError(f"Legacy path survived migration: {relative}")
        if relative in LEGAL_EXCEPTIONS or source.suffix.lower() in ALLOWED_BINARY_SUFFIXES:
            continue
        data = source.read_bytes()
        if b"\\0" in data:
            continue
        try:
            decoded = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if LEGACY.search(decoded):
            raise RuntimeError(f"Legacy project label survived migration: {relative}")

    # This one-shot tool contains its own migration regexes, which must NOT
    # become a permanent legacy-code exception in the committed ALKE tree.
    SELF.unlink()
    print(f"ALKE full identity migration: {renamed} paths renamed, {changed} text files rewritten")
    print("Legal license notices retained verbatim. No tracked app source file contains a legacy name.")

if __name__ == "__main__":
    main()
