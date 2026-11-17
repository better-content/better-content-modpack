#!/usr/bin/env python3
"""Create an exclusive, source-cited 54-owner repository obligation matrix.

Read-only source inventory; never discovers/tests/builds/packages a candidate. Every
short excerpt links back to the full historical assessment, which is not rewritten.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "src/test/resources/playtest"

def load(name):
    return json.loads((BASE / name).read_text(encoding="utf-8"))

def cell(value, limit=270):
    text = " ".join(str(value).split()).replace("|", "\\|")
    return text if len(text) <= limit else text[:limit].rstrip() + "… (excerpt)"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    oracle = load("build-71-oracles.json")
    missing = load("build-71-missing-assertions.json")
    reference = load("build-71-coverage.json")
    contracts = {entry["repository"]: entry for entry in missing["entries"]}
    sources = {entry["repository"]: entry for entry in reference["sources"]}
    records = {record["repository"]: record for record in oracle["records"]}
    assert len(contracts) == len(sources) == 53 and len(records) == 54
    rows = [
        "# Repository source assertion and qualification-gap matrix — static authoring only",
        "",
        "Source inventory: `build-71-oracles.json` (54 source-level mappings), `build-71-missing-assertions.json` (53 complete open assessment sections) and `build-71-coverage.json` (53 immutable artifact hashes, **all 53 production fingerprints null**). The linked assessments retain full obligation text. The snippets here are explicitly truncated; they are pointers, **not substitutes** for the whole promise or review. Pack owner is the 54th row. This matrix has **zero candidate-attested executed IDs** and **zero complete shipped promises**. Source-method names below are annotated candidates, NOT exact observed native registrations or nonzero JUnit membership. Do not use them as a report whitelist until reviewed registration and a fresh candidate bind them. Historical build-71 Smoke is not imported.",
        "",
        "The owner-local `.promise` obligation is only the narrow source assertion shown below; its named open qualification gaps remain required. `.scale` needs independently replayed exact-candidate 8/16/32 workload attribution (not a generic source test); `.ci` needs reviewed exact task→observed nonzero test-ID membership plus six-category source/tests/providers/toolchain/configuration/artifact fingerprints. These remain unresolved separately for **every** row. A complete source-to-packaged-artifact receipt is missing for all 53. Generic `repo.<id>.*` historical assertions remain `implementation.state=missing`, never executable simply because a local test is named.",
        "",
        "| Owner; historical assertion IDs | Cited narrow source oracle, test candidate (unrun) | One concrete open required gap (assessment excerpt) | Candidate binding and required next action |",
        "| --- | --- | --- | --- |",
    ]
    for owner, item in sorted(records.items()):
        test = item["test"]
        method = f"{test.get('class', '')}.{test['method']}".lstrip(".")
        source_path = test["source"]
        source_line = test["line"]
        check = item["oracle"]
        code = f"`{source_path}:{source_line}` `{method}` [{test['kind']}] → `{check['path']}:{check['line']}`: {cell(check['expression'], 155)}; reviewed narrow oracle: {cell(item['reviewed_oracle_summary'], 150)}"
        if owner in contracts:
            assessment = contracts[owner]
            sections = assessment["open_required_assertions"]
            section = next((x for x in sections if "gap" in x["heading"].lower() or "campaign" in x["heading"].lower()), sections[-1])
            fragments = [line.strip().lstrip("- ") for line in section["exact_assessment_text"].splitlines()
                if line.strip().startswith(("- ", "1. ", "2. ", "3. ", "4. "))]
            excerpt = " ".join(fragments[:2]) if fragments else section["exact_assessment_text"]
            assessment_link = f"../../mod_source/{owner}/docs/playtest-qualification.md"
            gap = f"[{cell(section['heading'], 70)}]({assessment_link}) lines {section['start_line']}–{section['end_line']}: {cell(excerpt, 290)}"
            artifact = sources[owner]
            candidate = f"build-71 `{artifact['artifact']}` SHA-256 `{artifact['artifact_sha256']}`; bundled-input production_fingerprint=null. Review whole local source method/native registration, exact installed artifact and open gap; author per-owner nonzero expected IDs before any test run."
        else:
            gap = "[Pack-wide Hand Workshop, effective recipes, acquisition and lifecycle obligations](playtest-suite.md); local KubeJS grout mock cannot prove installed recipe or natural ingredients."
            candidate = "pack owner: no independently attested exact production source/config→candidate proof or observed test membership; bind effective pack recipe/provider and full lifecycle separately."
        rows.append(f"| `{owner}`; `repo.{owner}.promise/.scale/.ci` | {code} | {gap} | {cell(candidate, 245)} |")
    rows += ["", "## Explicit gap ownership", "",
        "A/source-attestation: fresh candidate with independent source→artifact build receipt and all six fingerprint categories. B/native: exact candidate registered GameTest IDs and callback outcome/log membership; source annotation is not discovery. C/scenarios: effective gameplay assertion, real actor/target/acquisition and live-disk-reopen evidence per family. D/repositories: exact leaf test task plan, Gradle executed-task proof, zero-skip JUnit/native importer and 54-owner reviewed source→test→provider binding. An aggregate root can attribute a report only to its separately reviewed source-bound leaf task with observed uncached execution; this does **not** certify every CI/release obligation. Unreviewed aggregate aliases remain blocked. Human-approved test execution and runtime/distribution gates remain separate; none is granted here.", ""]
    with args.output.open("x", encoding="utf-8") as output:
        output.write("\n".join(rows))

if __name__ == "__main__":
    main()
