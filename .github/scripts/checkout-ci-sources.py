#!/usr/bin/env python3
"""Materialize pinned source-only checkouts for the fast reflection boundary test.

No mod is built, deployed, or used as a CI provider JAR by this operation.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import subprocess


def checkout(manifest: Path, workspace: Path):
    document = json.loads(manifest.read_text())
    if document.get("schema") != "bc.ci_source_revisions.v1":
        raise ValueError("unsupported CI source inventory")
    records = document["repositories"]
    names = [r["repository"] for r in records]
    if len(names) != len(set(names)):
        raise ValueError("duplicate CI source repository")
    for record in records:
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", record["repository"]):
            raise ValueError("unsafe repository name")
        if not re.fullmatch(r"[0-9a-f]{40}", record["commit"]):
            raise ValueError("source revision must be an immutable commit")

    def materialize(record):
        target = workspace / "mod_source" / record["repository"]
        if target.exists():
            raise ValueError(f"refuse to overwrite existing source checkout: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "init", str(target)], check=True, capture_output=True)
        def git(*args):
            return subprocess.run(["git", "-C", str(target), *args], check=True,
                                  text=True, capture_output=True)
        git("remote", "add", "origin", f"https://github.com/better-content/{record['repository']}.git")
        git("config", "core.sparseCheckout", "true")
        (target / ".git/info/sparse-checkout").write_text("/src/\n")
        git("fetch", "--depth=1", "--filter=blob:none", "origin", record["commit"])
        git("checkout", "--detach", "FETCH_HEAD")
        if git("rev-parse", "HEAD").stdout.strip() != record["commit"]:
            raise ValueError(f"source revision mismatch: {target}")
        print(f"{record['repository']} {record['commit']}", flush=True)

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(materialize, records))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--workspace", required=True, type=Path)
    args = parser.parse_args()
    checkout(args.manifest, args.workspace)
