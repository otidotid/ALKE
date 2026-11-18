#!/usr/bin/env python3
"""Reconstitute the exact v583 Personal APK from the successful CI build and
a preverified PUBLIC APK Signature Scheme v2/v3 signing block.

No private certificate key or keystore password is present in this repository.
The recipe is bound to one specific unsigned APK hash and one signed APK hash.
It cannot produce a valid signature for any other APK.
"""
import base64
import hashlib
import struct
import sys
import zipfile
import zlib
from pathlib import Path

UNSIGNED_SHA256 = "9ac949fb51b695e814e19a0e9737bc88b7f9713c835ec2e91e6d6fd1b14d88eb"
SIGNED_SHA256 = "c9ebb77178905f1a7cb49a77b4c610ab90134bbccfea3fb42029fba646b85d3b"
PUBLIC_BLOCK_SHA256 = "80b308d8f5634339cfc94f35402ae410b9fc4862a69366dc6fc441ce901a6449"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def central_directory(data):
    pos = data.rfind(b"PK\x05\x06")
    if pos < 0 or pos + 22 != len(data):
        raise ValueError("Unsupported ZIP EOCD")
    start = struct.unpack_from("<I", data, pos + 16)[0]
    length = struct.unpack_from("<I", data, pos + 12)[0]
    if start + length != pos:
        raise ValueError("Unsupported central directory or malformed archive")
    return pos, start


def rebuild(source, block, destination):
    original = Path(source).read_bytes()
    if digest(original) != UNSIGNED_SHA256:
        raise ValueError("The build artifact is not the verified passing v583 Personal APK")
    public_block = zlib.decompress(base64.b64decode(Path(block).read_text().strip(), validate=True))
    if digest(public_block) != PUBLIC_BLOCK_SHA256:
        raise ValueError("Public signature block checksum mismatch")
    if public_block[-16:] != b"APK Sig Block 42":
        raise ValueError("Invalid APK v2/v3 signature footer")
    if (struct.unpack_from("<Q", public_block, 0)[0] + 8 != len(public_block)
            or struct.unpack_from("<Q", public_block, len(public_block) - 24)[0] + 8 != len(public_block)):
        raise ValueError("Invalid APK signature block length")

    eocd_offset, central_start = central_directory(original)
    prior_block_length = 0
    if original[central_start - 16:central_start] == b"APK Sig Block 42":
        prior_block_length = struct.unpack_from("<Q", original, central_start - 24)[0] + 8
        if not 0 < prior_block_length < central_start:
            raise ValueError("Invalid preexisting debug signing block")
    with zipfile.ZipFile(source) as archive:
        entries = sorted(archive.infolist(), key=lambda entry: entry.header_offset)
    if len(entries) < 100 or entries[0].header_offset != 0:
        raise ValueError("Unexpected v583 APK ZIP layout")

    assembled = bytearray()
    offsets = {}
    for index, entry in enumerate(entries):
        start = entry.header_offset
        end = (entries[index + 1].header_offset
               if index + 1 < len(entries) else central_start - prior_block_length)
        item = original[start:end]
        if item[:4] != b"PK\x03\x04":
            raise ValueError("Malformed APK local entry")
        name_len, extra_len = struct.unpack_from("<HH", item, 26)
        content_start = 30 + name_len + extra_len
        if entry.compress_type == zipfile.ZIP_STORED:
            if any(item[30 + name_len:content_start]):
                raise ValueError("Noncanonical extra field in APK ZIP local entry")
            aligned_length = extra_len + (8 if extra_len < 2 else 4)
            alignment = (b"\x35\xd9" + struct.pack("<H", aligned_length - 4)
                         + b"\x04\x00" + b"\x00" * (aligned_length - 6))
            header = bytearray(item[:30 + name_len])
            struct.pack_into("<H", header, 28, aligned_length)
            item = bytes(header) + alignment + item[content_start:]
        offsets[entry.filename] = len(assembled)
        assembled.extend(item)

    # APK v2/v3 signature starts at a 4096-byte boundary; keep alignment exact.
    assembled.extend(b"\x00" * (-len(assembled) % 4096))
    assembled.extend(public_block)
    new_central_start = len(assembled)

    cursor = central_start
    for entry in entries:
        if original[cursor:cursor + 4] != b"PK\x01\x02":
            raise ValueError("ZIP central directory does not match local entries")
        name_len, extra_len, comment_len = struct.unpack_from("<HHH", original, cursor + 28)
        end = cursor + 46 + name_len + extra_len + comment_len
        record = bytearray(original[cursor:end])
        if struct.unpack_from("<I", record, 42)[0] != entry.header_offset:
            raise ValueError("APK ZIP central directory order mismatch")
        struct.pack_into("<I", record, 42, offsets[entry.filename])
        assembled.extend(record)
        cursor = end
    if cursor != eocd_offset:
        raise ValueError("APK central directory length mismatch")
    footer = bytearray(original[eocd_offset:])
    struct.pack_into("<I", footer, 16, new_central_start)
    assembled.extend(footer)
    if digest(assembled) != SIGNED_SHA256:
        raise ValueError("Reconstruction does not match independently verified signed v583 APK")
    output = Path(destination)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(assembled)
    print("Exact signed v583 APK confirmed:", output, "bytes=", len(assembled),
          "sha256=", digest(assembled))


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: reconstruct_v583.py source.apk public.zb64 destination.apk")
    rebuild(*sys.argv[1:])
