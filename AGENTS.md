# Better Content modpack entry point

Forge 1.20.1 content/integration repository. Read [docs/README.md](docs/README.md) and
[workspace policy](docs/policies/workspace.md) before changes. Shared rules have one owner:

- [Testing/releases](docs/testing.md): standing authorization replaces build/test/release approval
  gates. Aggregate changes, check relevant agent handoffs before heavy launches, avoid wasteful
  parallel Dist/Debug, and use extremely slow/heavy Debug only as a last resort. Supported facades,
  locks, correctness and retention remain binding; follow new explicit user stops/exclusions.
- [Generated-data retention](docs/policies/generated-data.md): retain worlds/saves, fixtures,
  evidence, candidates, receipts, caches and build outputs by default across handoffs and
  conversations. Finish closes activity and deletes nothing. Purge is separately gated by quiet,
  stable and messy conditions; last known good and latest candidate/support/evidence stay protected.
- [Inventory parity](docs/inventory-parity.md): both native paths and same-artifact real acceptance.
- [Player teaching](docs/better_discovery_guides.md): read before teaching changes.
- [Custom-mod inventory/bootstrap](docs/custom-mod-workspace.md) and [SDK inventory](docs/toolchains.md).

## Repository-specific boundaries

- `dist.sh` is package-once only. No output-directory override and no validation/provenance/schema/
  content verdicts may be added to it. `package.sh` is its internal packager; no alternate assemblers.
- `test.main.kts` is the Dev/Dist/Debug facade; `release.main.kts` owns explicitly ordered fresh
  distributions. `test-control.main.kts` exposes structured control. `pack-test-queue.main.kts`
  is the persistent coordinator queue; `pack-test-lock.main.kts` is the internal mutex wrapper.
- `maintenance.main.kts` delegates to standard-library Python, not Gradle. Follow the task
  activity-registration and retention/purge contract in the generated-data policy.
- The former `tools/` tree and `quarantine/` are unsupported. Do not recreate/invoke them or make
  runtime content depend on them without explicit reversal. Custom-mod source remains outside the pack.
- `bc.crafting_policy.v1` is a narrow authorized content policy in `kubejs/config/` and KubeJS scripts,
  not a general audit tool. Its startup check rejects unknown namespaces; runtime reporting can
  name exact cut-family leaks/live consumers. It does not authorize a new `tools/` tree.
- FTB Quests, its integration and the standalone quest-layout harness are retired. Do not recreate
  quest graphs, completion ledgers or compilers. Preserve current Threads domain-event contracts.
- Tracked root `options.txt` is the client-default source. Authored/tracked production inputs are
  not disposable outputs. Do not infer runtime acceptance from documentation normalization.

## Local checks

For docs-only work: `python3 -B scripts/check_documentation.py` and `git diff --check`.
For relevant pack source/maintenance changes: `./test.main.kts dev` (Minecraft-free).
Builds, qualification, deployment, refresh, packaging and Dist/Debug need no separate approval.
Use them judiciously under the shared cost/coordination warnings; earlier approval-only holds are
superseded, not correctness checks, safe resource ownership or retention protections.
