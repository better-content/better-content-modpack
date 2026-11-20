#!/usr/bin/env python3
"""Nonqualifying Build-bound first recipe observation, never a harvest/craft/acquisition proof."""
import argparse
import hashlib
import json
from pathlib import Path

DOMAIN_LIMITS = {"recipes_semantics": 50_331_648, "item_tags": 16_777_216, "items": 16_777_216}
ROOT_ITEM = "minecraft:oak_log"
OUTPUT_ITEM = "minecraft:oak_planks"
TAG = "minecraft:oak_logs"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    if path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= 4 * 1024**3:
        raise ValueError("unsafe or unbounded candidate archive")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_native_run(discovery):
    """Correlate the diagnostic's native session with its receipt, not a standalone JSON claim."""
    evidence = Path(discovery["evidence_directory"])
    if (any(ancestor.is_symlink() for ancestor in (evidence, *evidence.parents)) or
            evidence.name != "discovery-diagnostic" or
            evidence.parent.name != discovery.get("run_id") or
            Path(discovery["native_directory"]).resolve(strict=True) !=
            (evidence / "native").resolve(strict=True)):
        raise ValueError("native discovery evidence ownership differs")
    files = {}
    for name in ("run.json", "events.jsonl"):
        path = evidence / name
        if path.is_symlink() or not path.is_file() or not 1 <= path.stat().st_size <= 1_048_576:
            raise ValueError(f"native discovery {name} unsafe or unbounded")
        files[name] = path.read_bytes()
    run = json.loads(files["run.json"])
    if not (run.get("schema") == "bc.session_run.v2" and run.get("run_id") == discovery["run_id"] and
            run.get("tier") == "discovery" and run.get("status") == "diagnostic" and
            run.get("diagnostic") is True and run.get("acceptance_eligible") is False and
            run.get("failure") is None and run.get("candidates") == discovery["candidate"]):
        raise ValueError("native discovery run receipt differs")
    events = [json.loads(line) for line in files["events.jsonl"].splitlines()]
    def unique(kind):
        matched = [event for event in events if event.get("type") == kind]
        if len(matched) != 1:
            raise ValueError(f"native discovery {kind} event missing/duplicate")
        return matched[0]
    if not (unique("session_started").get("candidates") == discovery["candidate"] and
            unique("test_support_installed").get("sha256") == discovery.get("test_support_sha256") and
            unique("test_support_installed").get("instrumented") is True and
            unique("prepared").get("fresh_world") is True and
            unique("validated").get("candidate_hashes_unchanged") is True and
            unique("process_cleanup").get("complete") is True and
            unique("session_finished").get("diagnostic_complete") is True and
            unique("session_finished").get("success") is False and
            unique("session_finished").get("failure") is None):
        raise ValueError("native discovery lifecycle events differ")
    unique("server_saved_and_stopped")
    return {name: sha(data) for name, data in files.items()}


def bound_domain(discovery, domain):
    files = discovery["inventory"]["files"]
    matches = [f for f in files if f.get("domain") == domain]
    if len(matches) != 1:
        raise ValueError(f"missing/duplicate native {domain} domain")
    ref = matches[0]
    relative = Path(ref["path"])
    native_path = Path(discovery["native_directory"])
    if native_path.is_symlink():
        raise ValueError(f"native {domain} root is symlink")
    native = native_path.resolve(strict=True)
    if relative.is_absolute() or ".." in relative.parts or relative.parts[:1] != ("inventory",):
        raise ValueError(f"unsafe native {domain} path")
    path = native / relative
    for depth in range(len(relative.parts) + 1):
        if (native / Path(*relative.parts[:depth])).is_symlink():
            raise ValueError(f"native {domain} has symlinked ancestor")
    if not path.resolve(strict=True).is_relative_to(native):
        raise ValueError(f"native {domain} escaped owned mailbox")
    size = path.stat().st_size
    if not 1 <= size <= DOMAIN_LIMITS[domain] or size != ref.get("bytes"):
        raise ValueError(f"native {domain} exceeded byte bound or changed size")
    data = path.read_bytes()
    if sha(data) != ref.get("sha256"):
        raise ValueError(f"native {domain} SHA-256 changed")
    if len(data.splitlines()) != ref.get("records"):
        raise ValueError(f"native {domain} record count changed")
    return data, ref


def matching_row(data, wanted):
    found = []
    for line in data.splitlines():
        if len(line) > 1_400_000:
            raise ValueError("unbounded native inventory row")
        row = json.loads(line)
        if row.get("id") == wanted:
            found.append((row, sha(line)))
    if len(found) != 1:
        raise ValueError(f"missing/duplicate effective row: {wanted}")
    return found[0]


def bind(discovery, client, server):
    if not (discovery.get("schema") == "bc.playtest_discovery.v2" and
            discovery.get("status") == "observed" and discovery.get("qualification_eligible") is False and
            discovery.get("cleanup_complete") is True and discovery.get("saved_and_validated") is True and
            discovery.get("failure") is None):
        raise ValueError("not a completed nonqualifying native discovery")
    receipt_hashes = verify_native_run(discovery)
    pair = discovery["candidate"]
    for side, path in (("client", client), ("server", server)):
        if file_sha(path) != pair[side + "_sha256"]:
            raise ValueError(f"candidate {side} bytes differ from native discovery")
    observed = {}
    refs = {}
    for domain, target in (("recipes_semantics", "minecraft:oak_planks"),
                           ("item_tags", "minecraft:oak_logs"), ("items", "minecraft:oak_log")):
        data, refs[domain] = bound_domain(discovery, domain)
        observed[domain] = matching_row(data, target)
    recipe, recipe_row_sha = observed["recipes_semantics"]
    tag, tag_row_sha = observed["item_tags"]
    item, item_row_sha = observed["items"]
    if not (recipe.get("semantics_complete") is True and recipe.get("family") == "shapeless" and
            recipe.get("serializer") == "minecraft:crafting_shapeless" and
            recipe.get("station") == "minecraft:player_inventory_or_crafting_table" and
            recipe.get("output", {}).get("kind") == "item" and
            recipe["output"].get("item") == OUTPUT_ITEM and recipe["output"].get("count") == 4 and
            len(recipe.get("inputs", [])) == 1 and recipe["inputs"][0].get("slot") == 0 and
            recipe["inputs"][0].get("raw_ingredient") == {"tag": TAG} and
            any(a.get("kind") == "item" and a.get("item") == ROOT_ITEM and a.get("count") == 1 for a in
                recipe["inputs"][0].get("alternatives", []))):
        raise ValueError("effective first-edge recipe differs from reviewed narrow shape")
    if not (tag.get("metadata_complete") is True and ROOT_ITEM in tag.get("values", []) and
            item.get("metadata_complete") is True and item.get("max_stack", 0) >= 1):
        raise ValueError("effective root item/tag membership unavailable")
    return {
        "schema": "bc.first_edge_recipe_observation.v1", "qualification_eligible": False,
        "candidate": pair, "discovery_run_id": discovery["run_id"],
        "native_receipt_sha256": receipt_hashes,
        "test_support_sha256": discovery["test_support_sha256"],
        "source_capabilities": {"effective_recipe": True, "effective_root_tag": True,
                                "root_acquisition": False, "physical_client_craft": False,
                                "craft_event_ancestry": False, "saved_reopened_stack": False},
        "recipe_id": "minecraft:oak_planks", "root_item": ROOT_ITEM, "root_tag": TAG,
        "output_item": OUTPUT_ITEM, "output_count": 4,
        "domain_files": {domain: {"path": refs[domain]["path"], "sha256": refs[domain]["sha256"]}
                         for domain in DOMAIN_LIMITS},
        "rows_sha256": {"recipe": recipe_row_sha, "tag": tag_row_sha, "item": item_row_sha},
        "limitations": ["No naturally generated log site or source-attributed pickup established.",
                        "No physical client crafting click, craft-event ancestry, or saved/reopened stack established.",
                        "Discovery's whole-pack provider and semantic gaps remain; this is not a campaign case."],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("discovery", "client", "server", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    discovery_bytes = args.discovery.read_bytes()
    discovery = json.loads(discovery_bytes)
    observation = bind(discovery, args.client, args.server)
    observation["discovery_sha256"] = sha(discovery_bytes)
    with args.output.open("x") as stream:
        json.dump(observation, stream, indent=2)
        stream.write("\n")
    print(args.output)


if __name__ == "__main__":
    main()
