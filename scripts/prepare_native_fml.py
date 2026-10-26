#!/usr/bin/env python3
"""First-launch source fixture config; do not fetch per-mod update news for pinned tests.

This is not a test verdict or dependency resolver. Existing settings/evidence are
never overwritten. Forge's supported versionCheck option leaves loading, mixins,
registries, gameplay and native diagnostics unchanged.
"""
import argparse
import hashlib
import json
from pathlib import Path
import stat

DEFAULTS = b"# Pinned source fixture: dependency versions are verified by owning release gates.\nversionCheck = false\n"


def prepare(directory):
    directory = Path(directory).absolute()
    if ".." in directory.parts:
        raise ValueError("native fixture path traversal")
    target = directory / "config/fml.toml"
    for parent in (target, *target.parents):
        if parent.is_symlink():
            raise ValueError(f"native fixture symlink: {parent}")
    if target.exists():
        if not stat.S_ISREG(target.stat().st_mode):
            raise ValueError("native FML config is not a regular file")
        status = "preserved"
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as output:
            output.write(DEFAULTS)
        status = "seeded"
    return {"schema": "bc.source_native_fml.v1", "status": status, "path": str(target),
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    print(json.dumps(prepare(parser.parse_args().directory)))
