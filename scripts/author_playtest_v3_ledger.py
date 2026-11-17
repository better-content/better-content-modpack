#!/usr/bin/env python3
"""Author a NEW non-executable v3 obligation ledger from immutable historical inputs.

Never discovers a candidate, runs tests or changes historical catalogues. Output is
exclusive-create; a reviewer must separately author every missing target and contract.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INPUTS = {
    "historical_catalogue": ROOT / "src/test/resources/playtest/suite.json",
    "source_oracles": ROOT / "src/test/resources/playtest/build-71-oracles.json",
    "open_assessments": ROOT / "src/test/resources/playtest/build-71-missing-assertions.json",
    "candidate_reference": ROOT / "src/test/resources/playtest/build-71-coverage.json",
}
# These are literal source assertion IDs from DriverRegistry's nine non-control drivers;
# updating a driver requires a new independently reviewed ledger revision.
DRIVERS = {
    "world.initial-spawn": ("drivers/FirstJoinDriver.kt", ["first-login", "safe-terrain", "chosen-position"], ["world.entry"]),
    "world.temperate": ("drivers/TemperateFirstJoinDriver.kt", ["native-biome", "climate", "first-join-site"], ["world.entry", "world.sample"]),
    "combat.outgoing": ("drivers/OutgoingCombatDriver.kt", ["attributed-hit", "equipment-consumption"], ["entity.snapshot", "player.snapshot", "player.attack"]),
    "survival.food": ("drivers/FullFoodDriver.kt", ["sampled-intake", "hunger-saturation", "effects", "container-remainders"], ["fixture.begin", "player.snapshot", "player.use_item", "fixture.restore"]),
    **{f"scale.{n}": ("drivers/ScaleCapacityDriver.kt", ["cohort-ticks", "tps-recovery", "sustained-600s", "resource-trace", "owned-cleanup"], ["scale.begin", "scale.cleanup"]) for n in (8, 16, 32)},
    "client.emi.public-index": ("client/EmiIndexDriver.kt", ["index-roster"], ["client.snapshot", "client.emi_index"]),
    "client.inventory.sync": ("client/InventorySyncDriver.kt", ["slot-sync"], ["player.snapshot", "client.snapshot", "fixture.begin", "wait.ticks"]),
}

TESTS = {
    "world.initial-spawn": "drivers/FirstJoinDriverTest.kt",
    "world.temperate": "drivers/TemperateFirstJoinDriverTest.kt",
    "combat.outgoing": "drivers/OutgoingCombatDriverTest.kt",
    "survival.food": "drivers/FullFoodDriverTest.kt",
    **{f"scale.{n}": "drivers/ScaleCapacityDriverTest.kt" for n in (8, 16, 32)},
    "client.emi.public-index": "client/NarrowClientDriversTest.kt",
    "client.inventory.sync": "client/NarrowClientDriversTest.kt",
}

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    history, oracles, gaps, reference = (load(path) for path in INPUTS.values())
    source = {record["repository"]: record for record in oracles["records"]}
    assessments = {entry["repository"]: entry for entry in gaps["entries"]}
    assert len(history["cases"]) == 203 and len(history["repositories"]) == 54
    assert len(source) == 54 and len(assessments) == 53 and len(reference["sources"]) == 53
    rows = []
    for old in history["cases"]:
        case_id = old["id"]
        repository = case_id.split(".")[1] if case_id.startswith("repo.") else None
        driver = DRIVERS.get(case_id)
        rec = source.get(repository) if repository else None
        row = {
            "id": case_id,
            "domain": case_id.removeprefix("repo.").rsplit(".", 1)[0] if repository else case_id,
            "historical_assertions": old["assertions"],
            "required_assertions": [f"{case_id}.{suffix}" for suffix in driver[1]] if driver else [],
            "required_capabilities": driver[2] if driver else [],
            "required_inventory_domains": [], "inventory_domain_review": "missing",
            "implementation_path": f"src/main/kotlin/com/bettercontent/tests/suite/{driver[0]}" if driver else None,
            "source_path": f"src/main/kotlin/com/bettercontent/tests/suite/{driver[0]}" if driver else (rec["oracle"]["path"] if rec else None),
            "test_path": f"src/test/kotlin/com/bettercontent/tests/suite/{TESTS[case_id]}" if driver else (rec["test"]["source"] if rec else None),
            "provider_paths": [], "target_bindings": [],
            "producer_contract": {"status": "requires-review", "provider_paths": []},
            "acquisition": {"status": "missing", "proof_case_ids": [], "fixture_is_not_proof": True},
            "persistence": {"status": "missing", "live_disk_reopen_assertion_ids": []},
            "reviews": {"status": "pending", "source": "", "target": "", "artifact": "", "human": ""},
            "state": "authored-previously-compiled-unrun-not-executable" if driver else "missing-unrun",
            "remaining_defects": ["no independently reviewed v3 targets, provider contracts or qualifying acquisition/persistence binding"],
            "historical_requirement": old["actions"],
        }
        if repository:
            row["remaining_defects"].append("generic historical repository promise/scale/CI is not a concrete source-test assertion")
            row["source_oracle"] = {
                "test": rec["test"], "assertion": rec["oracle"], "layer": rec["layer"],
                "boundary": rec["boundary"], "state": rec["state"],
                "reviewed_summary": rec["reviewed_oracle_summary"],
                "assessment": assessments[repository]["assessment"] if repository in assessments else "docs/playtest-suite.md",
                "open_required_sections": [a["heading"] for a in assessments[repository]["open_required_assertions"]] if repository in assessments else [],
                "expected_runtime_test_ids": [], "observed_runtime_test_ids": [],
            }
        rows.append(row)
    # Two narrowly registered client cases have no historical v1 row at all.
    for case_id in ("client.emi.public-index", "client.inventory.sync"):
        path, suffixes, actions = DRIVERS[case_id]
        rows.append({"id": case_id, "domain": case_id, "historical_assertions": [],
            "required_assertions": [f"{case_id}.{suffix}" for suffix in suffixes], "required_capabilities": actions,
            "required_inventory_domains": [], "inventory_domain_review": "missing",
            "implementation_path": f"src/main/kotlin/com/bettercontent/tests/suite/{path}",
            "source_path": f"src/main/kotlin/com/bettercontent/tests/suite/{path}",
            "test_path": f"src/test/kotlin/com/bettercontent/tests/suite/{TESTS[case_id]}",
            "provider_paths": [], "target_bindings": [],
            "producer_contract": {"status": "requires-review", "provider_paths": []},
            "acquisition": {"status": "missing", "proof_case_ids": [], "fixture_is_not_proof": True},
            "persistence": {"status": "missing", "live_disk_reopen_assertion_ids": []},
            "reviews": {"status": "pending", "source": "", "target": "", "artifact": "", "human": ""},
            "state": "authored-previously-compiled-unrun-not-executable",
            "remaining_defects": ["absent from historical catalogue; no reviewed target, acquisition or persistence contract"],
            "historical_requirement": []})
    assert len(rows) == 205 and len({r["id"] for r in rows}) == 205
    owners = []
    for owner in sorted(source):
        oracle = source[owner]
        owners.append({"id": owner, "case_ids": [f"repo.{owner}.{kind}" for kind in ("promise", "scale", "ci")],
            "source_path": oracle["oracle"]["path"], "test_path": oracle["test"]["source"],
            "source_role": "source-assertion-path-not-attested-production-source",
            "production_source_paths": [], "provider_paths": [], "toolchain_paths": [], "configuration_paths": [],
            "artifact_sha256": next((r["artifact_sha256"] for r in reference["sources"] if r["repository"] == owner), None),
            "source_to_artifact": "unverified", "expected_runtime_test_ids": [],
            "observed_runtime_test_ids": [], "status": "source-review-only-unrun"})
    result = {"schema": "bc.playtest_authoring_catalogue.v3", "purpose": "diagnostic", "status": "draft-unbound",
        "reviewed_by": None, "reviewed_at": None,
        "execution_eligible": False, "qualification_eligible": False,
        "source_documents": {name: {"path": str(path.relative_to(ROOT)), "sha256": sha(path)} for name, path in INPUTS.items()},
        "required_owner_count": 54, "required_source_repository_count": 53,
        "notes": ["This is a non-executable obligation ledger, NOT a v3 campaign manifest.",
            "Historical build-69 catalogue/build-71 reference preserved; no results imported.",
            "The prior compile-9/native-compile-6 logs do not establish runtime behavior or this ledger's compilation."],
        "owners": owners, "cases": rows}
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(result, output, indent=2, ensure_ascii=False)
        output.write("\n")

if __name__ == "__main__":
    main()
