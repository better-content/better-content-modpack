#!/usr/bin/env python3
"""Executable Debug boundary inventory and fresh-evidence admission. Never manufactures passes."""
import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import sys
import zipfile
import zlib
import testing_session
import debug_source_registration

SCHEMA = "bc.debug_interactions.v1"
HASH = re.compile(r"[0-9a-f]{64}\Z")


def load(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(), object_pairs_hook=unique)


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def regular(path, within=None):
    path = pathlib.Path(path).absolute()
    if ".." in path.parts:
        raise ValueError(f"parent traversal in evidence: {path}")
    if any(p.is_symlink() for p in [path, *path.parents]):
        raise ValueError(f"symlink evidence: {path}")
    if not path.is_file():
        raise ValueError(f"missing evidence: {path}")
    if within and not path.is_relative_to(within.absolute()):
        raise ValueError(f"evidence outside current run: {path}")
    return path


def manifest(root):
    doc = load(root / "gradle/debug-interactions.json")
    if doc.get("schema") != SCHEMA:
        raise ValueError("unsupported interaction manifest")
    active = {m["repository"] for m in load(root / "gradle/active-custom-mods.json")["mods"]}
    inventory = {entry["repository"] for entry in doc["repository_inventory"]}
    if len(inventory) != len(doc["repository_inventory"]):
        raise ValueError("duplicate repository inventory entry")
    ids = set()
    owners = set()
    scenarios = doc["scenarios"]
    for entry in doc["boundaries"]:
        if entry["id"] in ids:
            raise ValueError("duplicate boundary")
        ids.add(entry["id"])
        for field in ("owners", "risk", "contract", "layer", "target", "fixture_intervention", "oracle", "limitations", "source"):
            if not entry.get(field):
                raise ValueError(f"boundary {entry['id']} lacks {field}")
        owners.update(entry["owners"])
        if entry["risk"] not in ("high", "medium", "low") or entry["coverage"] not in ("native", "source", "uncovered"):
            raise ValueError("invalid risk/coverage")
        if entry["coverage"] == "native" and not entry.get("scenarios"):
            raise ValueError("native boundary lacks executable scenarios")
        if entry["coverage"] == "source":
            test = entry.get("source_test", {})
            if not all(test.get(k) for k in ("repository", "task", "path", "method")) or test["repository"] not in entry["owners"]:
                raise ValueError("source boundary lacks exact owned executable method")
        if any(s not in scenarios for s in entry.get("scenarios", [])):
            raise ValueError("unknown boundary scenario")
    if inventory != active:
        raise ValueError(f"active-mod repository inventory drift: missing={active-inventory}, unknown={inventory-active}")
    if owners != active:
        raise ValueError(f"active-mod concrete boundary inventory drift: missing={active-owners}, unknown={owners-active}")
    for name, target in doc.get("targets", {}).items():
        if not all(target.get(k) for k in ("suite", "scenarios", "tiers", "setup", "acceptance_scope")) or any(key not in scenarios for key in target["scenarios"]):
            raise ValueError(f"invalid executable target descriptor: {name}")
    for key, scenario in scenarios.items():
        if not all(scenario.get(k) for k in ("suite", "class", "method")):
            raise ValueError(f"invalid executable scenario: {key}")
    return doc


def target_catalog(root, doc, tier=None, target=None):
    def describe(name, entry):
        return {"target": name, **entry, "executable_scenarios": [doc["scenarios"][key] for key in entry["scenarios"]],
                "candidate_binding_required": True, "manifest_sha256": sha(root / "gradle/debug-interactions.json")}
    if target is not None:
        entry = doc["targets"].get(target)
        if entry is None:
            for descriptor in doc.get("parameterized_targets", {}).values():
                if re.fullmatch(descriptor["pattern"], target) and target.removeprefix("dimension:") not in descriptor["excluded"]:
                    entry = descriptor
                    break
        if entry is None or tier not in entry["tiers"]:
            raise ValueError(f"unsupported target/tier: {target}/{tier}")
        return describe(target, entry)
    return {"fixed_targets": [describe(name, entry) for name, entry in doc["targets"].items() if tier is None or tier in entry["tiers"]],
            "parameterized_targets": {name: entry for name, entry in doc.get("parameterized_targets", {}).items() if tier is None or tier in entry["tiers"]},
            "full_debug": {"mandatory_scenarios": doc["scenarios"], "visual_cases": doc["visual_cases"],
                "source_boundaries": len([b for b in doc["boundaries"] if b["coverage"] == "source"]),
                "uncovered_high_risk": [b["id"] for b in doc["boundaries"] if b["risk"] == "high" and b["coverage"] == "uncovered"],
                "admission": "all mandatory native/source contracts, exact candidate/support/source identities and actual LLM native framebuffer review; no uncovered high-risk boundary"} if tier in (None, "debug") else None,
            "manifest_sha256": sha(root / "gradle/debug-interactions.json")}


def events(path):
    return [json.loads(line) for line in regular(path).read_text().splitlines() if line.strip()]


def binding(rows):
    selected = [r for r in rows if r.get("type") == "candidate_selected"]
    if not selected:
        raise ValueError("no native candidate selection")
    result = {key: selected[0][key] for key in ("client_sha256", "server_sha256")}
    for row in selected:
        if any(row[k] != result[k] for k in result):
            raise ValueError("candidate selection changed")
    for value in result.values():
        if not HASH.fullmatch(value):
            raise ValueError("invalid candidate digest")
    return result


def png_dimensions(path):
    """Validate the complete native RGB(A)8 PNG, not just a forgeable IHDR prefix."""
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise ValueError("review requires actual native PNG capture")
        width = height = channels = None
        compressed = bytearray()
        while True:
            head = stream.read(8)
            if len(head) != 8:
                raise ValueError("truncated native PNG")
            size, kind = struct.unpack(">I4s", head)
            if size > 256 * 1024 * 1024:
                raise ValueError("native PNG chunk exceeds bounded admission memory")
            data, crc = stream.read(size), stream.read(4)
            if len(data) != size or len(crc) != 4 or zlib.crc32(kind + data) != struct.unpack(">I", crc)[0]:
                raise ValueError("truncated/corrupt native PNG chunk")
            if kind == b"IHDR":
                if width is not None or size != 13:
                    raise ValueError("invalid native PNG header")
                width, height, depth, colour, compression, filtering, interlace = struct.unpack(">IIBBBBB", data)
                if not width or not height or width * height > 64 * 1024 * 1024 or depth != 8 or colour not in (2, 6) or compression or filtering or interlace:
                    raise ValueError("unsupported/beyond-memory-bound native RGB(A)8 PNG")
                channels = 3 if colour == 2 else 4
            elif kind == b"IDAT":
                if width is None:
                    raise ValueError("native PNG data precedes header")
                compressed.extend(data)
                if len(compressed) > 256 * 1024 * 1024:
                    raise ValueError("native PNG exceeds bounded admission memory")
            elif kind == b"IEND":
                if size or stream.read(1) or width is None or not compressed:
                    raise ValueError("incomplete native PNG")
                break
        decoder = zlib.decompressobj()
        expected = (width * channels + 1) * height
        pixels = decoder.decompress(compressed, expected + 1)
        if len(pixels) != expected or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
            raise ValueError("invalid/truncated native PNG pixel stream")
        stride = width * channels + 1
        if any(pixels[offset] > 4 for offset in range(0, len(pixels), stride)):
            raise ValueError("invalid native PNG row filter")
        return width, height


def capture_case(observation):
    label = observation.get("label", "")
    shader = re.fullmatch(r"shader-palette-([0-7])-(true|false)-\d+", label)
    if not shader:
        return label
    day, night = observation.get("native_day"), observation.get("native_night")
    if not isinstance(day, bool) or not isinstance(night, bool) or day == night:
        raise ValueError("shader review lacks actual native day/night classification")
    state = observation.get("third_party_shader_state", {})
    if state.get("enabled") is not True:
        raise ValueError("shader visual case is not an actual active native shader frame")
    return f"shader-{shader[1]}-{'signal' if shader[2] == 'true' else 'normal'}-{'day' if day else 'night'}-frame"


def validate_review(review, run, expected, required):
    if review.get("schema") != "bc.native_visual_review.v1" or review.get("run_id") != run.name:
        raise ValueError("visual review schema/run mismatch")
    if review.get("binding") != expected:
        raise ValueError("visual review candidate/support binding mismatch")
    if review.get("reviewer", {}).get("kind") != "llm" or not review["reviewer"].get("identity"):
        raise ValueError("actual LLM reviewer identity required")
    found = set()
    for image in review["images"]:
        key = image["case"]
        if key not in required or key in found:
            raise ValueError(f"unknown/duplicate visual case: {key}")
        found.add(key)
        path = regular(image["path"], run)
        if sha(path) != image["sha256"]:
            raise ValueError(f"reviewed image changed: {path}")
        width, height = png_dimensions(path)
        if image.get("dimensions") != [width, height] or min(width, height) < 64:
            raise ValueError("capture dimensions mismatch")
        if not all(image.get(k) for k in ("native_screen", "native_menu", "gui_scale", "capture_observation", "findings")):
            raise ValueError("review lacks native identity, observation or findings")
        observation = regular(image["capture_observation"], run)
        obs = load(observation)
        # Authoritative native client response binds framebuffer and identities. A filename
        # or a generated image alone cannot enter the visual gate.
        if obs.get("framebuffer") != path.name or observation.parent != path.parent or obs.get("screen_class") != image["native_screen"] or obs.get("menu_class") != image["native_menu"]:
            raise ValueError("review does not match native framebuffer observation")
        if obs.get("run") != run.name or capture_case(obs) != key:
            raise ValueError("native capture belongs to another run/case")
        if [obs.get("framebuffer_width"), obs.get("framebuffer_height")] != [width, height] or obs.get("gui_scale") != image["gui_scale"]:
            raise ValueError("native framebuffer dimensions/gui scale mismatch")
        for k, value in expected.items():
            if obs.get("candidate_binding", {}).get(k) != value:
                raise ValueError(f"native capture binding mismatch: {k}")
        criteria = image.get("criteria", {})
        if set(criteria) != set(required[key]) or any(v.get("verdict") != "passed" or not v.get("finding") for v in criteria.values()):
            raise ValueError(f"missing/ambiguous/failed explicit visual criteria: {key}")
        if image.get("verdict") != "passed":
            raise ValueError(f"visual defects: {key}")
    if found != set(required):
        raise ValueError(f"missing reviewed native images: {set(required)-found}")


def canonical_source_attempt(root, mods):
    """Observe an immutable coherent provider set; reuse is not incomplete verification."""
    archive = root / ".worklane/testing-sessions/completed"
    for candidate in sorted(archive.glob("*.json")):
        regular(candidate)
        if candidate.stat().st_size > 1024 * 1024:
            continue
        receipt = load(candidate)
        providers = receipt.get("mods", {})
        if receipt.get("schema") != "bc.testing_session.v2" or receipt.get("active_run") or set(providers) != set(mods) or any(
                providers.get(name, {}).get("verification_id") != mod["verification_id"] or
                providers.get(name, {}).get("sha256") != mod["sha256"] or
                providers.get(name, {}).get("support_hashes", {}) != mod.get("support_hashes", {}) for name, mod in mods.items()):
            continue
        observed = testing_session.inspect_live(root, receipt)
        if observed["affected_providers"] or any(observed["verification_origins"].get(name) != mod["verification_id"] for name, mod in mods.items()):
            continue
        return {"path": str(candidate), "sha256": sha(candidate), "verification_origins": observed["verification_origins"]}
    raise ValueError("no immutable canonical source attempt matches current input/task/toolchain/dependency/support identities and candidate verification IDs")


def source_coverage(root, run, doc, expected):
    """Exact canonical source verification, not a surrogate full-pack/rendering verdict."""
    sources = [entry for entry in doc["boundaries"] if entry["coverage"] == "source"]
    if not sources:
        return
    provenance = load(regular(run / "server/candidate-binding.json", run))
    if provenance.get("schema") != "bc.fresh_dist_provenance.v1" or provenance.get("tests_skipped") is not False:
        raise ValueError("source boundaries require canonical tested verification provenance")
    for key, value in expected.items():
        if key in ("client_sha256", "server_sha256") and provenance.get("candidates", {}).get(key) != value:
            raise ValueError("source verification belongs to another candidate")
    mods = {mod["repository"]: mod for mod in provenance["mods"]}
    active = {mod["repository"]: mod for mod in load(root / "gradle/active-custom-mods.json")["mods"]}
    if set(mods) != set(active) or any(not mod.get("verification_id") for mod in mods.values()):
        raise ValueError("candidate lacks a coherent fully verified active provider set")
    eligible = canonical_source_attempt(root, mods)
    registrations = []
    logs = {}
    server = regular(provenance["candidates"]["server"])
    with zipfile.ZipFile(server) as archive:
        for entry in sources:
            test = entry["source_test"]
            repo = root.parent / "mod_source" / test["repository"]
            mod = mods[test["repository"]]
            if not mod.get("verification_id") or test["task"] not in active[test["repository"]]["tasks"]:
                raise ValueError(f"unverified source boundary: {entry['id']}")
            def git(*args):
                return subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True, timeout=5).stdout
            if git("status", "--porcelain", "--untracked-files=all").strip() or git("rev-parse", "HEAD").decode().strip() != mod["commit"]:
                raise ValueError(f"source checkout drift: {entry['id']}")
            path = regular(repo / test["path"], repo)
            committed = git("show", f"{mod['commit']}:{test['path']}")
            if committed != path.read_bytes():
                raise ValueError(f"source test changed after verification: {entry['id']}")
            if test["repository"] not in logs:
                origin = mod["verification_id"]
                if not re.fullmatch(r"\d{8}T\d{6}Z-\d+", origin):
                    raise ValueError("invalid canonical verification origin")
                log_path = regular(root / f"generated/test-evidence/{origin}/release/mod-{test['repository']}.log", root / "generated/test-evidence")
                logs[test["repository"]] = (log_path, log_path.read_text())
            log_path, log = logs[test["repository"]]
            registration = debug_source_registration.inspect(repo, test, active[test["repository"]]["modId"], log)
            registrations.append({"boundary": entry["id"], "registration": registration,
                                  "verification_log": {"path": str(log_path), "sha256": sha(log_path)},
                                  "build_registration_sha256": sha(regular(repo / registration["build_file"], repo))})
            artifact = [name for name in archive.namelist() if name.endswith("/mods/" + mod["artifact"]) or name == "mods/" + mod["artifact"]]
            if len(artifact) != 1 or hashlib.sha256(archive.read(artifact[0])).hexdigest() != mod["sha256"]:
                raise ValueError(f"verified provider not in selected candidate: {entry['id']}")
    return {"canonical_attempt": eligible, "concrete_tests": [entry["source_test"] for entry in sources], "registrations": registrations,
            "scope": "exact named source fixture contracts only; not pack-composition/render proof"}


def verify(root, run_id):
    doc = manifest(root)
    if not re.fullmatch(r"\d{8}T\d{6}Z-\d+", run_id):
        raise ValueError("invalid run ID")
    run = root / "generated/test-evidence" / run_id
    failures = []
    review_pending = False
    source_result = None
    all_rows = []
    suite_rows = {}
    for suite in sorted({s["suite"] for s in doc["scenarios"].values()}):
        try:
            report = load(regular(run / suite / "run.json", run))
            if any(report.get(k) != v for k, v in {"run_id": run_id, "suite": suite, "scope": "full", "tier": "debug", "status": "passed"}.items()):
                raise ValueError(f"{suite}: not a passed full current Debug suite")
            rows = events(run / suite / "events.jsonl")
            if any(r.get("type") in ("test_failed", "test_aborted", "test_skipped", "checkpoint_failed", "final_audit_failed") for r in rows):
                raise ValueError(f"{suite}: failed/aborted/skipped required scenario")
            if suite != "candidate" and not any(r.get("type") == "process_cleanup" and r.get("complete") is True for r in rows):
                raise ValueError(f"{suite}: no complete process cleanup")
            required_audits = {"final_artifact_integrity", "final_fixture_audit_logging", "runtime_log_findings", "startup_performance_diagnostics", "final_audited_logs"}
            if not required_audits.issubset({r.get("type") for r in rows}):
                raise ValueError(f"{suite}: missing required final audit")
            if not any(r.get("type") == "suite_finished" and r.get("success") is True and not r.get("failures") for r in rows):
                raise ValueError(f"{suite}: missing successful suite finalization")
            for row in rows:
                if row.get("type") in ("final_artifact_integrity", "final_fixture_audit_logging", "runtime_log_findings") and row.get("count") != 0:
                    raise ValueError(f"{suite}: final correctness findings")
                if row.get("type") == "final_audited_logs":
                    if not row.get("logs") and suite != "candidate":
                        raise ValueError(f"{suite}: native audited logs absent")
                    for log in row["logs"]:
                        if sha(regular(log["path"], run)) != log["sha256"]:
                            raise ValueError(f"{suite}: audited native log changed")
            suite_rows[suite] = rows
            all_rows += rows
        except (ValueError, OSError, KeyError) as error:
            failures.append(str(error))
    for key, scenario in doc["scenarios"].items():
        if not any(r.get("type") == "test_passed" and r.get("class") == scenario["class"] and r.get("method") == scenario["method"] for r in suite_rows.get(scenario["suite"], [])):
            failures.append(f"missing required native scenario: {key}")
    for entry in doc["boundaries"]:
        if entry["risk"] == "high" and entry["coverage"] == "uncovered":
            failures.append(f"uncovered high-risk boundary: {entry['id']} ({entry['limitations']})")
    try:
        expected = binding(all_rows)
        support = {r["support_sha256"] for r in all_rows if r.get("type") == "runtime_diagnostic_support"}
        if len(support) != 1 or not all(HASH.fullmatch(value) for value in support):
            raise ValueError("missing or inconsistent support hashes")
        expected["support_sha256"] = support.pop()
        source_result = source_coverage(root, run, doc, expected)
        for row in all_rows:
            if row.get("type") == "candidate_selected":
                for side in ("client", "server"):
                    if sha(regular(row[side])) != expected[side + "_sha256"]:
                        raise ValueError("candidate bytes changed after runtime")
            if row.get("type") == "runtime_diagnostic_support":
                if sha(regular(row["source"])) != expected["support_sha256"]:
                    raise ValueError("support bytes changed after runtime")
        if (run / "visual-review.json").exists():
            review = load(regular(run / "visual-review.json", run))
            validate_review(review, run, expected, doc["visual_cases"])
        else:
            review_pending = True
    except (ValueError, OSError, KeyError) as error:
        failures.append(str(error))
    result = {"schema": "bc.debug_coverage_result.v1", "run_id": run_id, "manifest_sha256": sha(root / "gradle/debug-interactions.json"), "status": "failed" if failures else "awaiting_review" if review_pending else "passed", "failures": failures, "source_verification": source_result}
    run.mkdir(parents=True, exist_ok=True)
    (run / "debug-coverage.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inventory", "targets", "verify", "admit-review", "finalize"))
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    parser.add_argument("--run-id")
    parser.add_argument("--review", type=pathlib.Path)
    parser.add_argument("--target")
    parser.add_argument("--tier", choices=("dev", "dist", "debug"))
    args = parser.parse_args()
    root = args.root.absolute()
    doc = manifest(root)
    if args.action == "targets":
        print(json.dumps(target_catalog(root, doc, args.tier, args.target), indent=2))
        return 0
    if args.action == "inventory":
        print(json.dumps(doc, indent=2))
        return 0
    if not args.run_id or not re.fullmatch(r"\d{8}T\d{6}Z-\d+", args.run_id):
        raise ValueError("valid --run-id required")
    run = root / "generated/test-evidence" / args.run_id
    if args.action == "finalize":
        control_path = regular(run / "control.json", run)
        control = load(control_path)
        if control.get("scope") != "debug" or control.get("target") is not None or control.get("state") != "awaiting_review":
            raise ValueError("only a full Debug awaiting visual review may be finalized; runtime failures cannot be waived")
        native_id = control.get("child_runs", {}).get("debug_run", args.run_id)
        previous = load(regular(root / "generated/test-evidence" / native_id / "debug-coverage.json"))
        if previous.get("status") != "awaiting_review" or previous.get("manifest_sha256") != sha(root / "gradle/debug-interactions.json"):
            raise ValueError("runtime coverage was not awaiting this exact review contract")
        result = verify(root, native_id)
        if result["status"] == "passed":
            control.update(state="passed", exit_code=0, finalization=result,
                           initial_state="awaiting_review", review_run_id=native_id)
            pending = control_path.with_suffix(".pending")
            pending.write_text(json.dumps(control, indent=2) + "\n")
            pending.replace(control_path)
        print(json.dumps(result, indent=2))
        return 4 if result["status"] == "awaiting_review" else int(result["status"] != "passed")
    if args.action == "admit-review":
        rows = []
        for path in run.glob("*/events.jsonl"):
            rows += events(path)
        expected = binding(rows)
        support = {r["support_sha256"] for r in rows if r.get("type") == "runtime_diagnostic_support"}
        if len(support) != 1 or not all(HASH.fullmatch(value) for value in support):
            raise ValueError("missing/inconsistent support")
        expected["support_sha256"] = support.pop()
        review = load(regular(args.review))
        validate_review(review, run, expected, doc["visual_cases"])
        # Explicit admission never reruns Minecraft or upgrades a failed runtime verdict.
        destination = run / "visual-review.json"
        with destination.open("x") as stream:
            json.dump(review, stream, indent=2)
            stream.write("\n")
        print(destination)
        return 0
    result = verify(root, args.run_id)
    print(json.dumps(result, indent=2))
    return 4 if result["status"] == "awaiting_review" else int(result["status"] != "passed")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"Debug coverage rejected: {error}", file=sys.stderr)
        sys.exit(1)
