#!/usr/bin/env python3
"""Author a DRAFT catalogue from an explicit existing package marker. Never runs tests or Minecraft.

This is not runtime discovery and cannot freeze authoring or qualify a candidate. Output is exclusive-create.
"""
import argparse
import hashlib
import json
from pathlib import Path

# Explicit user-note coverage; missing drivers are honest authoring gaps, not passing placeholders.
CASES = [
    ("world.temperate", ["temperate-biome"], "Locate a genuinely temperate generated biome through the installed discovery path; record predicates, bounded search and coordinates.", "worldgen-sample"),
    ("world.initial-spawn", ["spawn-selection"], "Enter through the actual first-join spawn flow; assert safe valid terrain and correct chosen position, not a test teleport.", "join-events"),
    ("primitive.knife-straw", ["knife-straw"], "Acquire and craft the butcher knife through every distinct primitive route; harvest grass with it and observe straw output and tool use.", "native-interaction"),
    ("primitive.tools", ["tools"], "Exercise each authored tool family against appropriate and wrong-tier blocks; assert harvest result, drops and durability.", "native-interaction"),
    ("water.manual", ["manual-drinking"], "Use genuine drinking actions and verify thirst recovery, paid water volume and remaining container state.", "player-state"),
    ("water.curios", ["curio-drinking"], "Equip supported drinking Curios, drink through the native path and verify slot ownership and consumption.", "slot-events"),
    ("water.automatic", ["auto-drinking"], "Cross the automatic-drinking threshold with filled and empty equipped sources; verify paid consumption and no fabricated water.", "player-state"),
    ("water.acquisition", ["water-acquisition"], "Acquire water through each supported natural collection and purification route, preserving volume and container remainders.", "acquisition-events"),
    ("water.purity", ["water-purity"], "Verify dirty purity 0 and purified purity 3; intermediate incoming values normalize to dirty, including transfer and drinking effects.", "native-interaction"),
    ("survival.thirst", ["thirst"], "Observe native thirst depletion and restoration, with explicit rates, boundary states and independent expected values.", "rate-samples"),
    ("survival.hunger", ["hunger"], "Observe hunger depletion and restoration under defined activity and food conditions without changing production rates.", "rate-samples"),
    ("survival.freezing", ["freezing"], "Observe body temperature and freezing consequences under controlled cold exposure and recovery.", "rate-samples"),
    ("survival.overheating", ["overheating"], "Observe body temperature and overheating consequences under controlled heat exposure and recovery.", "rate-samples"),
    ("survival.insulation", ["insulation"], "Compare equivalent exposure with and without acquired insulation; assert quantitative effect and correct equipment handling.", "rate-samples"),
    ("survival.food", ["food-sample"], "Eat ten seeded stratified food selections plus mandatory starter foods; verify consumption, nutrition, effects and container returns.", "food-selection"),
    ("survival.spoilage", ["spoilage"], "Observe native rates and near-boundary transitions for supported fresh, frozen, dried, hot and cold storage; preserve metadata through save/reload.", "rate-samples"),
    ("combat.outgoing", ["outgoing-damage"], "Attack enemies through normal player action; assert attributed damage and equipment consumption.", "damage-events"),
    ("combat.incoming", ["incoming-damage"], "Receive enemy attacks and assert attributed health/status changes without command-inflicted substitute damage.", "damage-events"),
    ("lifecycle.death-respawn", ["death-respawn"], "Die and respawn through the native lifecycle; verify intended inventory, health, status and mod state.", "lifecycle-events"),
    ("threat.pillagers", ["pillager-lethality"], "A naturally eligible campaign kills an idle unprotected player within the authored limit; unrelated deaths allow at most three total attempts and never satisfy lethality.", "attributed-death-events"),
    ("threat.depth-director", ["depth-lethality"], "Repeat attributable idle-player lethality at each configured behaviorally distinct depth band and inspect neighboring band boundaries.", "attributed-death-events"),
    ("crafting.tcon", ["tcon-crafting"], "Perform genuine TCon part/tool/metallurgy crafting with correct inputs, outputs, material metadata and remainders.", "craft-events"),
    ("crafting.backbone", ["crafting-backbone"], "Bootstrap the early Create/TCon crafting backbone from legitimate roots; operate stations and prove prerequisite reachability without circular unlocks.", "craft-events"),
    ("crafting.workshop", ["workshop"], "Acquire and craft the explicit Primitive and Hand Workshop roster, executing every distinct route and parameterizing interchangeable variants.", "craft-events"),
    ("travel.fonts", ["fonts"], "Use each enabled Font: legitimate access, entry, native arrival, charge/session behavior, return and ticket cleanup; keep retired destinations closed.", "font-session-events"),
    ("magic.ars-early", ["ars-early"], "Acquire starter Ars book and writing/crafting surfaces during Hand Workshop without any Font/dimensional prerequisite.", "craft-events"),
    ("magic.ars-glyphs", ["ars-glyphs"], "Acquire intended glyph origin/proof/catalyst roots and craft each distinct glyph route; respect higher-tier and quarantined glyph exclusions.", "craft-events"),
    ("moving.vs", ["vs-assemblies"], "For each supported VS assembly/controller family and distinct interaction, construct, assemble, operate, disassemble and save/reload without loss or stranded state.", "assembly-events"),
    ("moving.create", ["create-contraptions"], "For each supported Create contraption/controller family and distinct interaction, assemble, operate, transfer inventories/fluids, disassemble and save/reload without duplication.", "assembly-events"),
    ("combat.tacz", ["tacz"], "Acquire/craft intended guns, ammunition and workstations; load, reload, fire, deal damage and preserve relevant state.", "weapon-events"),
    ("client.emi", ["emi"], "Use effective recipe and usage lookups, tags, inputs, outputs and category navigation; viewer display is not acquisition or craft proof.", "client-input-events"),
    ("world.dynamic-trees", ["dynamic-trees"], "Classify at least 100 wild trees from at least five sites per applicable biome stratum, at most20 sites; require95 percent dynamic excluding evidenced structures and intentional exceptions.", "wild-tree-sample"),
]
ROSTERS = ["roots", "tools", "foods", "biomes", "depth-bands", "workshop", "glyphs", "fonts", "vs-mechanisms", "create-mechanisms", "tacz", "gui", "repositories"]

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def bound(path):
    return {"path": str(path.resolve()), "sha256": digest(path)}

def spec(identifier, notes, promise, evidence, owner="better-content-modpack", layer="campaign"):
    return {"id": identifier, "note_ids": notes, "owner": owner, "layer": layer, "context": identifier,
            "assertions": [identifier + ".promise"], "targets": [], "dependencies": [], "stage": 0,
            "timeout_seconds": 1800, "preconditions": ["Exact candidate and complete authored roster; genuine native setup required."],
            "actions": [promise], "expected": [promise], "cleanup": ["Restore declared fixture state; retain raw evidence; release owned entities and tickets."],
            "evidence_kinds": [evidence], "existing_tests": [],
            "implementation": {"state": "missing", "path": "", "symbol": ""}}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-marker", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    marker = json.loads(args.package_marker.read_text())
    if marker.get("schema") != "bc.package_ready.v1":
        parser.error("an explicit package-ready marker is required")
    proofs = marker["proofs"]
    for name in ("provenance.json", "archive-members.json"):
        if digest(Path(proofs[name]["path"])) != proofs[name]["sha256"]:
            parser.error("package proof changed: " + name)
    provenance = json.loads(Path(proofs["provenance.json"]["path"]).read_text())
    inventory = json.loads(Path(proofs["archive-members.json"]["path"]).read_text())
    archive_jars = {Path(member["path"]).name for member in inventory["members"] if member["path"].endswith(".jar")}
    updates = {source["repository"]: source for source in provenance["source_updates"]}
    mods = [mod for mod in provenance["mods"] if mod["artifact"] in archive_jars]
    if len(mods) != len(provenance["mods"]):
        parser.error("source provenance differs from archive membership")
    cases = [spec(*case) for case in CASES]
    for cohort in (8,16,32):
        case = spec(f"scale.{cohort}", [f"scale-{cohort}"], f"Load {cohort} registered ticking fake players at distances10 with disjoint actual footprints; recover19 TPS for30s within120s, then measure600s and verify cleanup.", "fake-player-load")
        case["timeout_seconds"] = 960
        cases.append(case)
    for name, note in (("menus", "gui-menus"), ("in-game", "gui-in-game")):
        cases.append(spec("visual."+name, [note], "Independent real-client reviewer checks every authored screen at1280x720 GUI2 and1920x1080 GUI3 with authentic input and fresh captures.", "gui-review", layer="visual"))
    cases.append(spec("logs.review", ["logs"], "Review every normalized log signature and raw multiline context; disposition by actual impact, not zero WARN/ERROR compliance.", "log-review", layer="logs"))
    for tier in ("smoke", "overworld-tele-soak", "tele-soak"):
        case = spec("existing."+tier, [tier], "Consume the existing exact-artifact clean lifecycle receipt under its unchanged acceptance contract.", "existing-lifecycle", layer="lifecycle")
        case["timeout_seconds"] = 86400
        case["existing_tests"] = ["src/main/kotlin/com/bettercontent/tests/SessionLifecycle.kt", "docs/testing.md"]
        # Existing native tier exists; its suite receipt adapter still must be authored.
        cases.append(case)
    repositories = []
    for identifier, mod in [("better-content-modpack", None)] + [(mod["repository"],mod) for mod in sorted(mods,key=lambda m:m["repository"])]:
        ids = []
        for suffix, note in (("promise","repo-promises"),("scale","repo-scale"),("ci","repo-ci")):
            identifier_case = f"repo.{identifier}.{suffix}"; ids.append(identifier_case)
            case = spec(identifier_case, [note], f"Establish the reviewed shipped {suffix} contract for {identifier}; the repository assessment must supply concrete independent assertions and native/client boundaries.", "repository-evidence", owner=identifier, layer="unit")
            case["existing_tests"] = [f"../mod_source/{identifier}/docs/playtest-qualification.md"] if mod else ["docs/testing.md"]
            cases.append(case)
        repositories.append({"id":identifier,"artifact":mod["artifact"] if mod else "","artifact_sha256":mod["sha256"] if mod else "",
            "source_sha256":updates[identifier]["production_fingerprint"] if mod else "", "tests_sha256":"", "providers_sha256":"", "toolchain_sha256":"", "configuration_sha256":"",
            "assessment":f"../mod_source/{identifier}/docs/playtest-qualification.md" if mod else "docs/playtest-suite.md", "state":"assessment-pending", "case_ids":ids})
    rosters = [{"kind":kind,"complete":False,"entries":{},"gaps":["Concrete effective-candidate roster and case mapping not yet authored."]} for kind in ROSTERS]
    rosters[-1]["entries"] = {repo["id"]:repo["case_ids"] for repo in repositories}
    candidate = marker["candidates"]
    data = {"schema":"bc.playtest_manifest.v1", "revision":"authoring-20261109-1",
        "authoring":{"state":"draft","reviewer":"","reviewed_at":"","blockers":[
            "Native campaign adapter and scenario action drivers are not implemented.",
            "Effective item/block acquisition graph, recipe routes and all explicit runtime rosters remain to be authored.",
            "Repository assessments are not complete native test implementations or passing evidence.",
            "Repository/toolchain/test/configuration fingerprints and reference hardware profile remain unbound.",
            "Cross-repository redundancy review and independent authoring-complete review remain outstanding.",
            "No test execution or packaging is authorized by this draft."]},
        "candidate":{"client":{"path":candidate["client"],"sha256":candidate["client_sha256"]},"server":{"path":candidate["server"],"sha256":candidate["server_sha256"]},
                     "provenance":proofs["provenance.json"],"inventory":proofs["archive-members.json"]},
        "profile":{"seed":424242,"view_distance":10,"simulation_distance":10,"cohorts":[8,16,32],"cohort_seconds":600,"recovery_seconds":120,"recovery_stable_seconds":30,"tps_window_seconds":30,"minimum_tps":19.0,
                   "tree_minimum":100,"tree_minimum_sites":5,"tree_maximum_sites":20,"tree_dynamic_fraction":0.95,"food_sample_size":10,"threat_attempts":3,"visual_profiles":["1280x720@2","1920x1080@3"],
                   "timing_mode":"native-windows-and-boundaries","participant_kind":"server-fake-player","server_heap_mib":0,"hardware_profile":""},
        "test_support":{"path":"","sha256":""},"repositories":repositories,"rosters":rosters,"acquisitions":[],"cases":cases}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open("x") as output:
        json.dump(data,output,indent=2);output.write("\n")
    print(f"Authored DRAFT only: {args.output}; {len(repositories)-1} shipped sources, {len(cases)} coverage entries, no native acceptance or test execution.")

if __name__ == "__main__":
    main()
