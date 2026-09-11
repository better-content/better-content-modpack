#!/usr/bin/env python3
"""Stage hash-verified typed API providers from an explicitly pinned pack checkout.

Only custom provider JARs are copied. This does not resolve a pack, build mods,
deploy artifacts, or run pack tests. Requires Python 3.11+ (standard library only).
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile


def prepare(pack: Path, repository: str, output: Path) -> dict:
    document = json.loads((pack / "gradle/active-custom-mods.json").read_text())
    if document.get("schema") != "bc.active_custom_mods.v1":
        raise ValueError("unsupported active custom-mod inventory")
    mods = {mod["repository"]: mod for mod in document["mods"]}
    if len(mods) != len(document["mods"]):
        raise ValueError("duplicate repository in inventory")
    if repository not in mods:
        raise ValueError(f"unknown active repository: {repository}")
    ordered, visiting, done = [], set(), set()

    def visit(name):
        if name in visiting:
            raise ValueError(f"provider dependency cycle: {name}")
        if name in done:
            return
        if name not in mods:
            raise ValueError(f"unknown provider: {name}")
        visiting.add(name)
        for dependency in mods[name].get("dependsOn", []):
            visit(dependency)
        visiting.remove(name)
        done.add(name)
        if name != repository:
            ordered.append(mods[name])

    visit(repository)
    index = tomllib.loads((pack / "index.toml").read_text())
    if index.get("hash-format") != "sha256":
        raise ValueError("provider index must use sha256")
    hashes = {item["file"]: item["hash"] for item in index["files"]}
    payloads = []
    for mod in ordered:
        artifact = mod["artifact"]
        if not re.fullmatch(r"[A-Za-z0-9._+-]+\.jar", artifact):
            raise ValueError(f"unsafe provider artifact: {artifact}")
        source = pack / "mods" / artifact
        if source.is_symlink():
            raise ValueError(f"symlink provider: {artifact}")
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if hashes.get(f"mods/{artifact}") != digest:
            raise ValueError(f"provider hash mismatch: {artifact}")
        with zipfile.ZipFile(source) as jar:
            identity = dict(line.split("=", 1) for line in jar.read(
                "META-INF/better-content-source.properties").decode().splitlines()
                if line and not line.startswith("#") and "=" in line)
        if identity.get("schema") != "bc.custom_mod_source.v1" or identity.get("repository") != mod["repository"] or identity.get("mod_id") != mod["modId"]:
            raise ValueError(f"provider identity mismatch: {artifact}")
        if not re.fullmatch(r"[0-9a-f]{40}", identity.get("commit", "")):
            raise ValueError(f"provider source revision missing: {artifact}")
        payloads.append({"repository": mod["repository"], "artifact": artifact,
                         "sha256": digest, "sourceCommit": identity["commit"]})
    # Validate everything before creating or modifying destination files.
    if output.is_symlink():
        raise ValueError("provider output must not be a symlink")
    if output.exists():
        expected = {p["artifact"] for p in payloads} | {"providers.json"}
        if any(p.name not in expected for p in output.iterdir()):
            raise ValueError("provider output contains unrelated files")
        for payload in payloads:
            target = output / payload["artifact"]
            if target.is_symlink() or (target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() != payload["sha256"]):
                raise ValueError(f"existing provider differs: {target.name}")
    output.mkdir(parents=True, exist_ok=True)
    for payload in payloads:
        target = output / payload["artifact"]
        if not target.exists():
            shutil.copyfile(pack / "mods" / payload["artifact"], target)
    manifest = {"repository": repository, "providers": payloads}
    (output / "providers.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack-root", required=True, type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(prepare(args.pack_root, args.repository, args.output), indent=2))
