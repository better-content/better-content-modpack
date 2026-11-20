#!/usr/bin/env python3
"""Narrow negative contracts; no native world is created by these tests."""
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("first_edge", Path(__file__).with_name("first_edge_recipe_binding.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def digest(data):
    return hashlib.sha256(data).hexdigest()


class FirstEdgeBindingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir="/home/dev/.tmp")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.client = self.root / "client.zip"
        self.server = self.root / "server.zip"
        self.client.write_bytes(b"client")
        self.server.write_bytes(b"server")
        self.evidence = self.root / "discovery-test" / "discovery-diagnostic"
        self.native = self.evidence / "native"
        (self.native / "inventory" / "000000000001").mkdir(parents=True)
        self.discovery = {"schema": "bc.playtest_discovery.v2", "status": "observed",
                          "qualification_eligible": False, "cleanup_complete": True,
                          "saved_and_validated": True, "failure": None, "run_id": "discovery-test",
                          "native_directory": str(self.native),
                          "candidate": {"client_sha256": digest(b"client"), "server_sha256": digest(b"server")},
                          "inventory": {"files": [], "complete": False, "gaps": ["provider:natural_acquisition"]}}
        self.recipe = {"id": "minecraft:oak_planks", "semantics_complete": True, "family": "shapeless",
                       "serializer": "minecraft:crafting_shapeless",
                       "station": "minecraft:player_inventory_or_crafting_table",
                       "output": {"kind": "item", "item": "minecraft:oak_planks", "count": 4},
                       "inputs": [{"slot": 0, "raw_ingredient": {"tag": "minecraft:oak_logs"},
                                   "alternatives": [{"kind": "item", "item": "minecraft:oak_log", "count": 1}]}]}
        self.write_domain("recipes_semantics", self.recipe)
        self.write_domain("item_tags", {"id": "minecraft:oak_logs", "values": ["minecraft:oak_log"],
                                        "metadata_complete": True})
        self.write_domain("items", {"id": "minecraft:oak_log", "max_stack": 64,
                                    "metadata_complete": True})
        self.write_session_receipts()

    def write_domain(self, domain, row):
        data = (json.dumps(row, separators=(",", ":")) + "\n").encode()
        relative = f"inventory/000000000001/{domain}.jsonl"
        (self.native / relative).write_bytes(data)
        ref = {"domain": domain, "path": relative, "sha256": digest(data), "bytes": len(data), "records": 1}
        files = self.discovery["inventory"]["files"]
        files[:] = [f for f in files if f["domain"] != domain] + [ref]

    def write_session_receipts(self):
        self.discovery["evidence_directory"] = str(self.evidence)
        self.discovery["test_support_sha256"] = digest(b"instrumented support")
        run = {"schema": "bc.session_run.v2", "run_id": "discovery-test", "tier": "discovery",
               "status": "diagnostic", "diagnostic": True, "acceptance_eligible": False,
               "failure": None, "candidates": self.discovery["candidate"]}
        events = [{"type": "session_started", "candidates": self.discovery["candidate"]},
                  {"type": "test_support_installed", "sha256": self.discovery["test_support_sha256"],
                   "instrumented": True}, {"type": "prepared", "fresh_world": True},
                  {"type": "server_saved_and_stopped"},
                  {"type": "validated", "candidate_hashes_unchanged": True},
                  {"type": "process_cleanup", "complete": True},
                  {"type": "session_finished", "success": False, "failure": None,
                   "diagnostic_complete": True}]
        (self.evidence / "run.json").write_text(json.dumps(run))
        (self.evidence / "events.jsonl").write_text("\n".join(map(json.dumps, events)) + "\n")

    def test_native_receipts_correlate_and_reject_mutation(self):
        self.write_session_receipts()
        hashes = module.verify_native_run(self.discovery)
        self.assertEqual(digest((self.evidence / "run.json").read_bytes()), hashes["run.json"])
        run = json.loads((self.evidence / "run.json").read_text())
        run["candidates"]["server_sha256"] = digest(b"other")
        (self.evidence / "run.json").write_text(json.dumps(run))
        with self.assertRaisesRegex(ValueError, "run receipt differs"):
            module.verify_native_run(self.discovery)
        self.write_session_receipts()
        events = (self.evidence / "events.jsonl").read_text().replace('"instrumented": true',
                                                                  '"instrumented": false')
        (self.evidence / "events.jsonl").write_text(events)
        with self.assertRaisesRegex(ValueError, "lifecycle events differ"):
            module.verify_native_run(self.discovery)

    def test_direct_bind_requires_native_receipts(self):
        (self.evidence / "events.jsonl").unlink()
        with self.assertRaisesRegex(ValueError, "events.jsonl unsafe"):
            module.bind(self.discovery, self.client, self.server)

    def test_native_ancestor_symlink_rejected_even_with_matching_hash(self):
        (self.native / "inventory").rename(self.native / "actual_inventory")
        (self.native / "inventory").symlink_to("actual_inventory", target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlinked ancestor"):
            module.bind(self.discovery, self.client, self.server)

    def test_matching_effective_recipe_remains_nonqualifying(self):
        observation = module.bind(self.discovery, self.client, self.server)
        self.assertFalse(observation["qualification_eligible"])
        self.assertEqual("minecraft:oak_planks", observation["recipe_id"])
        self.assertFalse(observation["source_capabilities"]["root_acquisition"])
        self.assertFalse(observation["source_capabilities"]["physical_client_craft"])
        self.assertEqual(digest((self.evidence / "events.jsonl").read_bytes()),
                         observation["native_receipt_sha256"]["events.jsonl"])

    def test_mutated_raw_file_or_candidate_rejected(self):
        (self.native / self.discovery["inventory"]["files"][0]["path"]).write_bytes(b"different\n")
        with self.assertRaisesRegex(ValueError, "size|SHA-256"):
            module.bind(self.discovery, self.client, self.server)
        self.server.write_bytes(b"different")
        with self.assertRaisesRegex(ValueError, "candidate server bytes"):
            module.bind(self.discovery, self.client, self.server)

    def test_wrong_output_tag_or_incomplete_discovery_rejected(self):
        changed = dict(self.recipe)
        changed["output"] = {"kind": "item", "item": "minecraft:oak_planks", "count": 8}
        self.write_domain("recipes_semantics", changed)
        with self.assertRaisesRegex(ValueError, "recipe differs"):
            module.bind(self.discovery, self.client, self.server)
        self.write_domain("recipes_semantics", self.recipe)
        self.write_domain("item_tags", {"id": "minecraft:oak_logs", "values": [], "metadata_complete": True})
        with self.assertRaisesRegex(ValueError, "root item/tag"):
            module.bind(self.discovery, self.client, self.server)
        self.write_domain("item_tags", {"id": "minecraft:oak_logs", "values": ["minecraft:oak_log"],
                                        "metadata_complete": True})
        changed = dict(self.recipe)
        changed["inputs"] = [{"slot": 0, "raw_ingredient": {"tag": "minecraft:oak_logs"},
                              "alternatives": [{"kind": "fluid", "item": "minecraft:oak_log", "count": 1}]}]
        self.write_domain("recipes_semantics", changed)
        with self.assertRaisesRegex(ValueError, "recipe differs"):
            module.bind(self.discovery, self.client, self.server)
        self.discovery["status"] = "failed"
        with self.assertRaisesRegex(ValueError, "completed nonqualifying"):
            module.bind(self.discovery, self.client, self.server)


if __name__ == "__main__":
    unittest.main()
