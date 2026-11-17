# Complete playtest qualification: authoring status and contract

## Current status — implementation in progress, NOT qualified

The user approved **authoring** the complete suite on 2026-11-09, then explicitly authorized continuing the full implementation plan. This is not permission to execute tests, discovery worlds or campaigns, package/deploy mods, publish a release, or add gameplay features. Compile-only checks are permitted. Every requested suite must eventually qualify before starting the friends' world. The friends' playtest has not started.

**Candidate strategy:** immutable build-71 anchors implementation scope. Qualification must explicitly select a fresh source-attested candidate and review its differences; neither the latest checkout nor a Smoke pass silently changes the suite reference. The old build-69 catalogue and receipts are preserved as historical authoring material.

**Latest single-agent continuation (2026-11-09 18:59 UTC):** former workers remain paused. Current pack main/test sources and validator preparation compiled successfully (`/home/dev/.tmp/playtest-checkpoints/compile-9.log`); native main/test sources compiled successfully (`native-compile-6.log`, 18:52 UTC). This recovers the previous compiler failures, not runtime behavior. No tests, worlds, screenshots, distribution packaging or deployment were executed.

The continuation adds native protocol/session/driver code, historical v2 validation, and v3 offline-checkpoint execution/evidence contracts, but does **not** finish all gameplay families. Unsupported coverage still blocks a full campaign before world creation. See the implementation boundary below; do not summarize this as a completed suite.

Implemented in this increment:

- Kotlin manifest/receipt contracts and fail-closed authoring/qualification checks.
- Acquisition dependency/cycle/stage checks, assertion ownership, explicit note/roster coverage.
- Pure evaluators for deterministic stratified food selection, wild-tree classification/ratios, observed fake-player footprints and tick windows, and bounded encounter-death attribution.
- Supplemental multiline log-signature grouping that preserves the old per-line diagnostic output.
- Independent GUI checklist evidence contract layered on the existing real-client visual review.
- Additive CLI, evidence-only validator preparation, explicit CI wiring, and regression-test sources.
- A **draft** reference catalogue: `src/test/resources/playtest/suite.json`, generated from build-69 package provenance, with 203 coverage entries and 53 shipped custom repositories plus the pack.
- Repository-specific assessment documents and selected additive regression tests, tracked in [the repository assessment index](playtest-repository-assessments.md).

**Still not implemented/finished:** the full gameplay driver set; complete effective acquisition graph and recipe/mechanism/GUI/biome/depth rosters; tree sampling collectors and all required natural progression routes; complete real-client EMI/GUI case plans; within-branch save/reopen persistence assertions; complete source-promise/test/oracle mappings and missing assertions for every repository; source-to-packaged-artifact qualification fingerprints; final whole-suite authoring review/freeze; or any runtime qualification. Authored scale collectors/oracles and client observation primitives do not close these gaps.

A coverage entry is **not an executable test**. The historical draft intentionally leaves `implementation.state = missing`, unbound inputs and open roster gaps. `campaign` and `qualify` reject incomplete authoring before world creation or a qualification marker. Merely changing `authoring.state` cannot resolve the other checks. Exactly one `NativeCampaignAdapter` is now registered; it still rejects unsupported full-case drivers. Narrow controls cannot impersonate entire gameplay families. Do not replace missing drivers with mocked passing receipts.

Compile-only checks of the initial authoring scaffold succeeded at 2026-11-09 08:32 UTC; the continuation's final combined compile succeeded at **2026-11-09 09:53 UTC** (`compileKotlin compileTestKotlin preparePlaytestValidator`). **No JUnit, GameTest, discovery, campaign, visual review or CI tests were executed for this task.** Compilation is not behavior verification. Source-repository tests added during assessment remain unrun and are not assumed to match packaged binaries.

Build-69 is the **historical draft inventory reference**, not a chosen execution candidate or accepted build. Its independent Smoke attempt failed. Build-71 passed exact-pair Smoke (`dist/build-71/ready.json`) and is now the explicitly selected scope reference; that does not qualify this suite or rewrite the old catalogue. `src/test/resources/playtest/build-71-coverage.json` records the separate build-71 reference and source assessment mappings. Its bundled-input provenance has no verified production source fingerprints: all 53 source/artifact equivalences remain unverified until a source-attested candidate is selected. In-progress pack/source changes must not be silently folded into a qualification candidate. In particular, added GameTests under source repositories' `src/main` can affect their artifact fingerprints; they are not authorization to stage those JARs.

## Continuation implementation boundary

Authored code now includes:

- Strict v2 draft/reviewed acquisition validation with alternative routes and conjunctive ingredient groups; immutable offline ZIP/nested-JAR inventory and explicit v1-to-v2 **draft-only** conversion.
- A separately compiled Forge project in `test-support/forge/`, excluded from Packwiz exports and rejected if its descriptor-bearing JAR appears in production `mods/` during packaging. No support JAR was staged or distributed.
- Run/token/sequence-bound file-mailbox actions and raw observations, bounded parsing/deadlines, explicit native capability descriptors and rejection of replay/unsupported actions. See [protocol v2](playtest-protocol-v2.md).
- Observation-only discovery entry point and an opt-in instrumented session profile (seed 424242, server distances 10, server heap 4–16 GiB). Existing uninstrumented Smoke/Tele Soak settings are unchanged. Effective discovery remains **partial**, with explicit gaps; it cannot freeze a complete catalogue.
- A native campaign adapter with preflight driver checks, dependency ordering, retained failure evidence and conservative cleanup checks. Restarts are not yet implemented; no disk-reopen coverage is claimed.
- Real-player snapshots, normal block-use/item-use paths, true item-consumption completion, owned vanilla-state fixture provisioning/restoration, and narrow independent control oracles. Direct `destroyBlock` controls do not establish natural digging duration/progression.
- A parameterized `survival.food` driver for the exact deterministic ten-plus-mandatory **ordinary item-food** selection, with independent hunger/saturation/effect/inventory/remainder expectations. It rejects unsupported food mechanisms and requires independent acquisition cases. This is not spoilage coverage or proof that the actual pack's entire food roster is supported.
- Repository raw JUnit/native report import with exact membership, nonzero results, raw hashes, timestamps, source/provider/toolchain/configuration/artifact bindings, and skip/failure rejection. Static test-method inventories are not executed assertions.
- A v2 qualifier that requires compatible v2 evidence, re-evaluates native driver oracles and retains independent lifecycle, GUI and log-review gates. Historical v1 receipts cannot be upgraded into v2 qualification.

**Compilation evidence:** the standalone native project's `compileJava compileTestJava` succeeded; log: `/home/dev/.tmp/playtest-implementation/native-compile-final.log` (one deprecated ResourceLocation constructor warning). The combined pack `compileKotlin compileTestKotlin preparePlaytestValidator` succeeded in `/home/dev/.tmp/playtest-implementation/compile-6.log`. These commands did not execute tests, Minecraft, native discovery, or distribution packaging. Generated compilation artifacts are not staged distribution JARs. No runtime acceptance is implied.

An independent read-only review found five execution/evidence boundary defects: offline failed-terminal replay, unordered/undeclared actions, incomplete mandatory log coverage, runner/qualifier food-roster drift, and nonfixture actions falsely claiming a restored baseline. Follow-up code closes these paths, with additional authored regressions, and the final sources compile. This was a bounded review of the implemented increment, **not** approval of the unfinished full-suite authoring checkpoint; the regressions remain unrun.

Build-71 ZIPs were reread after authoring and still match its Smoke-ready hashes. No new candidate was packaged, and no source-owned gameplay JAR was staged.

The remaining work is implementation, not merely permission to run tests. In particular, completing one food driver does not close the 35 broad campaign entries or the 53-repository integration scope. All existing acceptance thresholds below remain unchanged.

## Offline-checkpoint implementation status — 2026-11-09

The approved plan remains **incomplete**. These are authored implementation capabilities, not executed or qualified results:

- V3 manifest/campaign/epoch/trial/evidence contracts, explicit branch DAGs, player-free origin, fresh runtime restoration, external sampling ledger, retained failures and blocked descendants. Native action transport stays v2; historical documents are not promoted.
- Offline server **and client** snapshots include empty directories, world/mod/player state and owned launcher state. Hash-bound restore audits are verified before launch; offline qualification checks that retained pre-run audit rather than incorrectly requiring a changed world to equal its input snapshot. Live-process, raw PID/birth-time, partial-copy, path/link and storage-budget gates are authored.
- Runtime filesystem restrictions use an explicitly hash-bound bubblewrap executable and PortableMC wheel. Only the relevant server/client state and native output directory are writable; controller request journals and dispatch/completion evidence are protected. Bubblewrap was not found in this lane, so runtime setup still has a dependency blocker. No sandbox was executed.
- Independent trial restores, `finally` cleanup, per-epoch native identities, separate authentic client streams, source-aware capability checks, and a controller dispatch/completion journal bind cross-client/server action order. Caps-only joined clients are distinguished from actual GUI observations.
- Fresh-scale terrain checks read the complete sealed-input MCA region headers. Site claims survive restores; each scale action binds the exact retained postclaim/pre-action ledger prefix. The authored raw 8/16/32 oracles charge setup/world generation to recovery and independently evaluate recovery and sustained performance. This is **not** measured capacity or certification of authenticated clients.
- Client framebuffer/input/EMI adapters and independent replay checks are authored. EMI binds the exact selected pair's remote declaration and pinned server JAR to the actual loaded client JAR. The narrow registered `client.emi.public-index` compares independently reviewed ID/recipe multisets (retaining duplicate IDs); `client.inventory.sync` checks all 41 client inventory slots around an explicitly owned behavior-only grant. Neither implements the broad GUI/EMI promise or natural acquisition. Catalogue/manifest expectations still need candidate-bound review. A separate `client_frame_review` requires human approval of the exact epoch frames; old visual receipts do not approve them. No screenshot or approval was generated.
- Sealed main-inventory decoding now checks the saved player's UUID and accepts the native empty-list representation. Typed stack hashes, same-capture live identities and bounded NBT decoding are compiled but do not establish acquisition ancestry. The caller-side harvest observer compiles; the pinned Forge bytecode was inspected statically, but runtime Mixin application remains unverified.
- Explicit repository runners, observed native membership reporting, 54 narrow owner mappings (53 custom repositories plus pack), a SHA-bound missing-assertion inventory and a nonshipping Airtight Machines fixture are authored. These are not complete promise coverage or source/artifact equivalence.

**Compile-only evidence:** historical successes `compile-4.log` / `native-compile-3.log` predate subsequent additions. Retained `compile-5.log` and `compile-6.log` record Kotlin source/test diagnostics, now corrected; `native-compile-4.log` records the unsupported interface injection, replaced with caller-side local capture. Current sources passed pack `compileKotlin compileTestKotlin preparePlaytestValidator` in **`compile-9.log`, 18:59:51 UTC**, and native `compileJava compileTestJava` in **`native-compile-6.log`, 18:52:50 UTC**. Logs are under `/home/dev/.tmp/playtest-checkpoints/`. New sealed-player, client-oracle and harvest bytecode regressions were compiled, **not run**. The Kotlin task graph generated the harness `:jar` dependency; no distribution was packaged or staged.

A bounded independent static review found seven concrete checkpoint/evidence defects; fixes were authored for all seven and a focused recheck was requested. This is not whole-suite review approval. All added regression tests, source fixtures, native actions, checkpoint operations and campaigns remain **unrun**. The implemented inventory exporter still reports incomplete semantic domains and blocks full execution; generic historical catalogue entries have not been relabeled complete. Exact natural filled-stack ancestry and genuine within-group reopen behavior remain missing, alongside primitive/crafting/magic/travel/moving-mechanism and other survival/combat coverage. The remaining work is authoring, not merely permission to execute.

## Fixed acceptance scope

- All requested gameplay, acquisition, GUI, log-review and repository promises, Smoke, Overworld Tele Soak and Tele Soak must qualify. Existing tier semantics are unchanged.
- One disposable **sampling world per candidate campaign**, seed `424242`; separate existing lifecycle, source-repository and independent visual fixtures remain distinct. No favorable-seed search or player-world reuse.
- Every named item/block has an acquisition classification. Actual crafting covers each materially different mechanism/progression route; interchangeable variants are parameterized. Roots include worldgen, loot, trades, fluids, harvesting, tools, stations, power, heat and dimension access. Recipe existence alone is insufficient.
- Primitive and Hand Workshop acquisition; starter Ars book/writing/crafting surfaces must be reachable before the first Font. Higher glyph gates and quarantines are retained.
- Every supported VS/Create assembly/controller family and materially distinct interaction, not every possible player construction. Include inventories/fluids, disassembly and persistence without duplication/loss.
- Server-side fake-player cohorts `8`, `16`, `32`, sequentially, at server view/simulation distance `10`; verify actual disjoint loaded/ticking regions and player lifecycle participation. Not 32 real-client login certification. Production max-players remains unchanged.
- Each cohort: fresh terrain on arrival; recover >=19 TPS continuously for30 seconds within120 seconds; then measure600 seconds, requiring >=19 TPS in every subsequent30-second window; retain memory/GC/tick percentiles and verify owned player/ticket cleanup within120 seconds.
- Each applicable biome stratum: >=100 classified wild trees over >=5 separated sites, <=20 attempted sites, >=95% dynamic. Structural/decorative logs and player builds are not regular trees; exceptions require evidence. Ambiguity or insufficient sampling is inconclusive.
- Ten seeded stratified food selections plus mandatory starter foods. Long-duration behavior uses native observation windows plus boundary fixtures and independent logic tests, never false full-duration claims or accelerated production rates.
- Death attribution matters: unrelated deaths cannot satisfy Pillager/Depth Director lethality; at most three attempts, bounded deadlines and retained interruptions.
- GUI profiles: `1280x720@2` and `1920x1080@3`, authentic interaction and fresh real-client screenshots from a distinct reviewer.
- Log review is mandatory; zero warnings/errors is not the gate. Review actual behavioral impact, corruption, crashes and harmful flooding. Diagnostic output cannot self-approve a warning.

These are empirical, candidate/profile-bound checks, not a guarantee of zero regressions. Broader real-client concurrency suggestions in repository assessments do not silently change the selected fake-player acceptance scope.

## Files and interfaces

| Surface | Purpose |
| --- | --- |
| `src/test/resources/playtest/suite.json` | Draft catalogue and exact inventory reference; not a frozen manifest. |
| `src/test/resources/playtest/schemas/` | Versioned JSON contracts with shared local definitions; no online schema fetch required. |
| `src/main/kotlin/com/bettercontent/tests/suite/` | Contract validation, measurement evaluators, GUI/qualification validation and native-adapter seam. |
| `src/test/kotlin/com/bettercontent/tests/suite/` | Authored, unrun harness regression cases. |
| `scripts/author_playtest_catalogue.py` | Exclusive-create draft authoring from an explicit existing package marker; never launches tests or discovers a latest candidate. |
| `docs/playtest-repository-assessments.md` | Source-repository assessment links and integration status. |

Manifest fields define stable IDs, owners, layers/contexts, canonical assertions, user-note mappings, targets, dependencies/stages, procedures, expected outcomes, deadlines, evidence kinds, cleanup, existing-test links and implementation ownership. Rosters enumerate actual membership; runtime drift blocks rather than silently adding passing cases.

`CaseResult` states are `passed`, `failed`, `blocked`, `inconclusive`, `not_run`. Only a complete, non-diagnostic `passed` result can satisfy a required case. Evidence binds to candidate hashes, catalogue, profile, case specification, raw files and timestamps. Local-source result reuse additionally requires matching source/tests/providers/toolchain/configuration and packaged artifact fingerprints. Required dependency failures do not become skips.

All relative paths in the **manifest** resolve from the pack root. Bound paths in a receipt/index resolve from that document's directory. A repository assessment may live in its sibling source checkout. Qualification rejects stale/missing/modified evidence and writes a fresh `playtest-qualified.json` in the index directory; existing package-ready/Smoke-ready/visual markers remain untouched.

### Commands — documented, not authorized to run

Author a new draft without overwriting an existing one:

```sh
python3 scripts/author_playtest_catalogue.py \
  --package-marker /absolute/path/to/package-ready.json \
  --output /absolute/path/to/new-draft.json
```

Compile the validator separately (no tests, Minecraft or distribution packaging):

```sh
./gradlew --no-daemon --max-workers=2 preparePlaytestValidator
```

Only after full authoring review **and explicit run approval**:

```sh
./test.main.kts campaign --manifest /path/reviewed.json \
  --client /path/client.zip --server /path/server.zip

# Explicit subsets are diagnostic even if every case happens to be listed.
./test.main.kts campaign --manifest /path/reviewed.json \
  --client /path/client.zip --server /path/server.zip --cases CASE_ID,CASE_ID

./test.main.kts qualify --manifest /path/reviewed.json --receipts /path/evidence-index.json
```

`qualify` uses a separately compiled validator and refuses a missing/stale prepared classpath; it does not invoke Gradle, build/package mods or start Minecraft. Both commands use the existing fail-fast runner lock. Runtime commands do not run owning-repository tests. The campaign remains blocked until all requested full-case drivers, complete discovered inventories and source-attested bindings exist; registering the adapter alone does not unblock it.

For offline authoring (no Minecraft), the separately compiled validator also supports exclusive-create outputs:

```sh
./test.main.kts convert-suite --manifest /path/historical-v1.json --output /path/new-v2-draft.json
./test.main.kts inspect-suite --client /path/client.zip --server /path/server.zip --output /path/archive-inventory.json
```

Conversion preserves the original v1 document inside the new draft and leaves active v2 acquisition authoring pending. It never marks review complete or upgrades old test results. Inspection records packaged bytes and declared remote locks separately; it does not claim those remote JARs were materialized or that effective recipes were observed.

After separate explicit execution approval, structurally bound v2 drafts can request non-qualifying observation-only discovery:

```sh
./test.main.kts discover --manifest /path/v2-draft.json \
  --client /path/client.zip --server /path/server.zip --output /path/new-discovery-directory
```

Discovery requires an explicitly built, SHA-bound support artifact, refuses an existing output directory, records raw inventory gaps, and never reviews authoring or emits qualification. The fixed maximum-heap budget is checked against available/cgroup memory instead of silently reducing the profile.

The source-owned test supplement must stay nonshipping, with its hash and fixture-only changes disclosed. Observer code cannot cancel/repair outcomes. Drivers may provision declared functional fixtures but `/give`, placed resources and injected loot cannot prove natural acquisition.

## Authoring and execution separation

Before testing, close all of the following:

1. Expand every draft entry into concrete cases and primary source assertions; map each item/block and every route/root.
2. Freeze effective recipe, biome, depth, font, glyph, mechanism, food, GUI and repository rosters against the selected artifact pair.
3. Implement native actions/observers, fake-player conformance, lifecycle ownership, cleanup and raw receipt emission. Preserve the original lifecycle tiers rather than pretending this adapter replaces them.
4. Bind all sources/tests/providers/toolchains/configuration/support and reference hardware/heap. Fixture changes are not production configuration changes.
5. Integrate existing tests/receipts; eliminate duplicate claims at the same layer/context without deleting or weakening original tests. Different layers can intentionally overlap.
6. Review source-test population/registration and static issues in the assessment index. Source annotation counts are not native discovery evidence.
7. Review all authored tests, schemas, failure paths, cleanup and cross-repository isolation; record a real independent authoring review and freeze the catalogue revision.
8. Present exact commands, resource profile and estimated run duration for human approval. Existing standing permission applies only to its documented old runtime tiers, not new/local suite tests.

The new explicit workflow requires a separately provisioned self-hosted qualification runner and an explicit approved invocation. No runtime workflow was triggered, no runner was provisioned, and no automatic campaign-on-push job was introduced.

After approval, run the source checks and exact-artifact campaigns; preserve all failed evidence. Changed candidates require fresh pack-level qualification. New test cases discovered during execution return to authoring first. Missing required gameplay is a blocker, not permission to implement a new feature under the freeze.
