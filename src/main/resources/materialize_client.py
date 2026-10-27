#!/usr/bin/env python3
"""Materialize an exported client without consulting live pack content or running a packager."""
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import tempfile
import time
import urllib.request
import zipfile


def relative(name):
    path = PurePosixPath(name)
    if path.is_absolute() or not path.parts or any(p in ("..", ".") for p in path.parts) or "\\" in name:
        raise ValueError(f"unsafe archive/artifact path: {name!r}")
    return path


def digest(path, algorithm):
    value = hashlib.new(algorithm)
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def materialize(archive, destination):
    if destination.exists():
        raise ValueError(f"client fixture must be new: {destination}")
    destination.mkdir(parents=True)
    with zipfile.ZipFile(archive) as bundle:
        manifest = json.loads(bundle.read("manifest.json"))
        lock = json.loads(bundle.read("better-content-artifacts.json"))
        if lock.get("schema") != "bc.client_artifacts.v1":
            raise ValueError("client artifact lock schema is unsupported")
        prefix = str(relative(manifest.get("overrides", "overrides"))) + "/"
        for entry in bundle.infolist():
            relative(entry.filename.rstrip("/"))
            if not entry.filename.startswith(prefix) or entry.is_dir():
                continue
            if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError(f"symlink in client archive: {entry.filename}")
            target = destination.joinpath(*relative(entry.filename[len(prefix):]).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(entry) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output)
    # The launcher consumes only the manifest from this exact ZIP.
    (destination.parent / "client-manifest.json").write_text(json.dumps(manifest))
    expected = {(item["projectID"], item["fileID"]) for item in manifest.get("files", []) if item.get("required", True)}
    locked = {(item["project_id"], item["file_id"]) for item in lock["artifacts"] if "project_id" in item}
    if not expected <= locked:
        raise ValueError(f"client artifact lock is missing exported file IDs: {expected - locked}")
    cache = Path(os.environ.get("BC_SESSION_ARTIFACT_CACHE", Path.home() / ".cache/bc/session-artifacts"))
    for item in lock["artifacts"]:
        target = destination.joinpath(*relative(item["path"]).parts)
        algorithm = item["hash_format"].replace("-", "").lower()
        expected_hash = item["hash"].lower()
        if not expected_hash or any(c not in "0123456789abcdef" for c in expected_hash):
            raise ValueError("invalid artifact digest")
        hashlib.new(algorithm)
        if target.is_file() and digest(target, algorithm) == expected_hash:
            continue
        if not item["url"].startswith("https://"):
            raise ValueError("artifacts must use pinned HTTPS URLs")
        cached = cache / algorithm / expected_hash
        cached.parent.mkdir(parents=True, exist_ok=True)
        with cached.with_suffix(".lock").open("a+b") as lock_file:
            fcntl.flock(lock_file, fcntl.LOCK_EX)
            if not cached.is_file() or digest(cached, algorithm) != expected_hash:
                for attempt in range(3):
                    descriptor, temporary = tempfile.mkstemp(dir=cached.parent, prefix="download-")
                    os.close(descriptor)
                    temporary = Path(temporary)
                    try:
                        request = urllib.request.Request(item["url"], headers={"User-Agent": "better-content-session/1.0"})
                        with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                            shutil.copyfileobj(response, output)
                        if digest(temporary, algorithm) != expected_hash:
                            raise ValueError(f"artifact hash mismatch: {item['path']}")
                        temporary.replace(cached)
                        break
                    except OSError:
                        if attempt == 2:
                            raise
                        time.sleep(2 ** attempt)
                    finally:
                        temporary.unlink(missing_ok=True)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(cached, target)
        print(f"resolved {item['path']}", flush=True)
    print(f"materialized {len(lock['artifacts'])} locked client artifacts", flush=True)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: materialize_client.py CLIENT_ZIP NEW_DIRECTORY")
    materialize(Path(sys.argv[1]), Path(sys.argv[2]))
