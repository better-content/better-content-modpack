# Behavior-preserving hook refactor plan

**Status: R1–R3 focused JVM tests pass; R4 source/test blocked before compile by qualified-provider prerequisites. No native/baseline parity or pack qualification. Not integration-ready.**

Requested by the user on **2026-11-17** following the Mixin/reflection alternatives audit.
This is the implementation reference for that work, not a new feature backlog, a release
approval, or permission to resume paused qualification-suite work.

## 1. Objective, constraints, and decisions

Replace fragile reflective discovery and bytecode hooks **only where the replacement has a
smaller, supported integration surface and preserves behavior**. Fewer Mixins is a useful
measurement, not the acceptance criterion. A retained narrow Mixin can be the better practice.

The initial work is bounded compatibility maintenance. It must not introduce gameplay,
registry IDs, persistence formats, network protocol changes, replacement machines/entities,
a general plugin framework, or a replacement qualification system. Broader redesign candidates
remain deferred under the [feature-freeze instructions](../../AGENTS.md).

### Chosen order

1. **R1 — Compat Fixes:** replace two optional tooltip reflection adapters with typed calls.
2. **R2 — Runtime Diagnostics:** replace pressure-chamber recipe reflection with its public API.
3. **R3 — Spawns:** remove reflective Death's Door availability probes using the existing typed
   bridge and isolated optional loaders; change Door only if strictly necessary.
4. **R4 — Manufacturing:** replace synthetic `this$0` owner discovery with explicit context,
   falling back to a typed constructor-capture Mixin if context cannot be passed.
5. **M1–M4:** inspected now at the user's request, then **skipped/retained** under the user's
   follow-up rule to skip changes that cannot be fixed easily without breaking features. Their
   specific proposed APIs do not cover parity; remaining Mixins and reflection are outside this pass.

For each R1–R4 batch, first author tests expressing **existing feature behavior**, then make
one replacement, then seek run authorization and compare with the pinned original. A batch
cannot be declared feature-parity-verified by compilation, source inspection, or mocks alone.
If any required parity check fails, is unavailable, or lacks approval to run, retain the old
implementation and mark the batch blocked/unvalidated. Never deploy an unverified replacement.
Each batch is independently reviewable and reversible; finish its applicable gates before
integrating the next. A blocked gate does not authorize relaxing checks or starting an unrelated
rewrite.

### Execution permission and release boundaries

- This document was written during a **documentation-only** turn: no code/test edits, builds,
  test execution, packaging, deployment, or publication.
- Writing tests is allowed. **Executing any local test, class-loading probe, GameTest, visual
  scenario, or custom runtime validation needs explicit human approval for that scope.** The
  request to plan behavior-validation tests is not execution permission.
- The standing pack-tier approval in [testing.md](testing.md) remains valid within its stated
  scope; it is not permission for repository tests, extra clients, restart scenarios, or feature
  tours. This plan invokes none of those tiers.
- Existing release requirements remain unchanged. No failed/unrun required gate may be called
  passing. Smoke proves its documented lifecycle, not optional-absence behavior, rendering,
  two-player correctness, or save/restart parity.
- The friends' playtest has **not started**. These will be development checks, not player reports.
- Do not weaken dependency verification, bypass the supported runner/Gradle locks, use stale
  sibling JARs silently, or use a broad `build`/`check`/`verifyFull` command as a substitute for
  explicitly scoped authorization.

### Concurrent ownership and baseline

ChemLib/Hazards/Airtight removal and Guides chemistry cleanup are concurrently owned by other
agents. Their membership/references in the original audit are **time-bound, not current truth**.
Spaceflight and several pack test-support areas have existing ownership/paused work. This plan
does not claim those sources or revive excluded mods.

Before each implementation batch:

- Read current workspace/repository instructions and all agent claims; correlate with live Herdr
  agent state. Coordinate overlap before claiming any files.
- Inspect every affected repository's `git status --short`; preserve user/peer changes.
- Record starting commits plus dirty-diff identity, exact provider versions/hashes, relevant
  mappings, descriptor/runtime-side constraints, and the baseline behavior under review.
- Re-read current source. The November 17 source counts are not implementation specifications.
- Bind before/after comparisons to the same provider set. A concurrent dependency/removal change
  invalidates attribution to this refactor until the baseline is reconciled.

## 2. Integration practices to use

Prefer, in order:

1. Existing data/configuration, **when the behavior is actually data-defined**.
2. Public typed provider API, a callback we own, or an existing Forge event with equivalent
   arguments, timing, cancellation, and side behavior.
3. Owned subclass/behavior/capability **only for objects we actually own or can extend through
   a supported registration surface**.
4. A small, version-pinned Mixin/accessor where the operation is otherwise inaccessible.
5. A bounded reflective fallback for genuinely unknown optional diagnostic families, with
   explicit incomplete/unsupported results rather than silent fabricated success.

Rules for all replacements:

- Optional provider types stay out of eagerly loaded common signatures, static fields, and
  automatic subscriber discovery. `compileOnly` alone is not loading isolation.
- Separate common and client adapters. Exercise absence and old/incompatible providers; do not
  replace capability checking with only a mod-ID check.
- Preserve the existing failure policy. In particular, direct calls can surface linkage failures
  differently from reflective invocation; decide and test the adapter's supported-failure
  boundary instead of broadly swallowing `Throwable`.
- Keep data and packet schemas, registry identities, event order, exact-once semantics, and
  native fallback unchanged unless a separate behavior change is justified and approved.
- Do not substitute reflection with raw ASM/coremods or access transformers merely to reduce a
  count. Access transformers change visibility, not behavior.
- Do not use tick polling or post-hoc inventory/time repair to replace an atomic native hook.
- Do not permanently run old and new production paths together. A temporary test oracle may
  compare them in isolated cases; never double-charge, double-consume, or mutate twice.
- Retain existing useful tests. If a test asserts the old mechanism (for example reflection
  cache counts), update it to enforce the same safety/performance requirement under the new
  design and add behavioral coverage; do not drop or weaken the protected contract.

## 3. Wave 1 — concrete reflection replacements

All source paths below are relative to `/home/dev/mod_source/<repository>/`.
New class/test names are proposed, not claims that those files already exist.

### R1. Typed optional tooltips — `better-compat-fixes`

**Current boundary:**
`src/main/java/com/bettercontent/bettercompatfixes/client/tooltip/ExternalToolTooltipSummary.java`.
Existing related test:
`src/test/java/com/bettercontent/bettercompatfixes/ToolTooltipTest.java`.

**Verified available surface:** pinned Blood Magic artifact `7956981` exposes
`AnointmentRegistrar.ANOINTMENT_MAP` and the queried `Anointment` methods; pinned Ars Nouveau
artifact `6688854` exposes the `ReactiveCaster(ItemStack)` constructor and typed Spell methods.
Verify exact dependency pins again at implementation time.

**Implementation:**

1. Keep `ExternalToolTooltipSummary` as the provider-neutral dispatcher.
2. Add small independently loadable `BloodMagicTooltipAdapter` and `ArsReactiveTooltipAdapter`
   classes in the existing client tooltip integration area; no general adapter registry.
3. Replace map/method/constructor reflection with direct typed calls. Use the existing detached
   stack and existing effect/state renderer; do not call native input-sensitive tooltip methods.
4. Gate each adapter on provider presence and keep provider linkage confined to that branch.
   Preserve the visible unavailable state and bounded warning behavior for supported adapter
   failures. Do not silently convert failures into an empty successful summary.
5. Add only required pinned compile dependencies and test-only runtime dependencies. Do not
   bundle optional mods, change their required/optional descriptor status, or update versions.
6. Review Compat Fixes' custom source-set/classpath wiring before editing its Gradle file; avoid
   accidentally putting client adapter classes or nonshipping fixtures into extra archives.

**Characterization and regression cases:**

- Known anointment: same effect key, category, ordering, label translation/level, and uses left.
- Harvest-only anointment is hidden for a non-mining tool, visible for a mining tool; mixed
  attack/harvest/use-finish anointments are not incorrectly hidden.
- Missing tag, empty list, unknown ID, malformed identifier and negative remaining-use inputs
  preserve the current skip/failure/clamping behavior established by characterization.
- Reactive enchantment absent/present; valid/invalid/empty spell; same displayed description
  and conditional-effect classification; no extra spell execution or mana consumption.
- Original stack count, damage, enchantments and NBT unchanged after repeated summary calls.
- Neither provider, each provider individually, and both providers installed: independent
  contributions and no accidental optional-class loading on absence.
- Dedicated server does not resolve the client adapter; supported provider failures produce
  the intended unavailable row without breaking the rest of the tooltip.

**Tests to add/extend:** extend `ToolTooltipTest`; add package-local adapter behavior tests such
as `ExternalToolTooltipAdapterTest`. Use pinned provider-backed cases in an appropriate native
fixture where Minecraft bootstrap is needed, not fake provider classes as the sole proof.

**Acceptance:** equivalent semantic tooltip rows for supported inputs, equivalent fallback
policy, no source-stack mutation, optional/class-side loading matrix passes, and no reflection
in these two production adapters. Real-client hover validation is a separate permission-gated
check; pure text tests cannot certify layout or provider class-loading in a real client.

**Rollback:** revert the batch/source change and restage the prior accepted artifact only
through the authorized release process. No persistence migration should be necessary.

### R2. Public pressure-chamber API — `better-runtime-diagnostics`

**Current boundary:**
`src/main/java/com/bettercontent/betterruntimediagnostics/ReflectivePneumaticRecipeAdapter.java`.
Existing related test:
`src/test/java/com/bettercontent/betterruntimediagnostics/RecipeGraphExporterTest.java`.

**Verified available surface:** pinned PneumaticCraft artifact `7307654` exposes public
`PressureChamberRecipe.getInputsForDisplay()`, `getResultsForDisplay()`, and
`getCraftingPressureForDisplay()`.

**Implementation:**

1. Introduce a provider-neutral dispatch boundary and a lazily loaded typed Pneumatic adapter;
   preserve the pressure-chamber recipe-type check and reject unsupported concrete recipes.
2. Use the public recipe base class rather than implementation-name matching or duck-typing
   arbitrary objects with similarly named methods. The existing fake method-name-only test
   object is not a production provider recipe: replace that mechanism test with native
   provider-backed behavior tests. If a real supported pressure-chamber recipe relies on
   duck-typing without extending the public provider class, stop R2 and retain the old adapter
   until an equivalent typed provider surface exists.
3. Preserve exporter schema, alternative outputs, counts/NBT, pressure representation, ordering
   guarantees, and incomplete/error handling. Never call `craftRecipe` or randomized production
   methods merely to display/export recipes.
4. Add the minimum pinned compile/test dependencies. Keep the optional `testSupport` source set
   disabled by default; do not import its large provider inventory into ordinary builds.
5. Replace the adapter's cache-count assertion with appropriate no-repeated-discovery/bounded
   direct-call behavior checks, while retaining exporter completeness/error tests.

**Regression cases:** normal pressure recipe, multiple input alternatives, multiple output
alternatives, counts/NBT preserved, boundary pressure values supported by the provider, subclassed
recipes, wrong recipe type, unsupported object, throwing provider method, absent PneumaticCraft,
and repeated exports without mutation. Assert no native crafting/consumption invocation.

Compare normalized before/after graph rows on the same real pinned recipe corpus. Every
previously supported row must remain present and semantically equal; unsupported/error cases
must remain visible as incomplete rather than disappear into a successful export.

**Tests:** extend `RecipeGraphExporterTest`; add `PneumaticRecipeAdapterTest` in the same test
package plus a provider-backed fixture if JVM-only bootstrapping is insufficient. Do not use a
synthetic object exposing only the three method names as the native API proof.

**Acceptance:** same supported export semantics and failure reporting, working optional-absence
and dedicated-server loading, no private/string-based reflection in this adapter. Leave the
large generic recipe adapter, Fabric fallback, and EMI probe unchanged in this batch.

**Rollback:** restore previous adapter and dependency declarations; existing diagnostic JSON
consumers must not require a schema migration.

### R3. Existing typed Scout bridge — `better-spawns` (Door only if necessary)

**Current source:**

- `src/main/kotlin/com/bettercontent/betterspawns/integration/DoorBridgeAvailability.kt`
- `.../ClassSelectorMod.kt`, `.../respawn/DeathScoutIntegration.kt`
- `.../client/ClientEvents.kt`, `.../client/ScoutDeathClient.kt`
- Provider: `better-deaths-door/src/main/java/com/bettercontent/betterdeathsdoor/api/ScoutBridgeContract.java`
  and existing `client/ScoutDeathAction` registration surface.

**Important refinement from the audit:** Spawns already has a compile-only Door dependency,
a typed client implementation, and existing registration. Prefer tightening those boundaries;
**do not create a new shared API mod, service registry, or network handshake framework**.

**Implementation:**

1. Keep a common dispatcher containing no eager Door/client class references.
2. Call the existing `ScoutBridgeContract.version()` from an isolated typed optional adapter
   after the presence gate; preserve unsupported/missing/older bridge => unavailable behavior.
3. In the client-only path, validate/register the existing typed client integration through an
   isolated guarded entry point. Retain idempotence and avoid initializing gameplay/client
   state merely to answer a presence question.
4. Ensure incompatible client interface/method linkage does not leave a half-registered listener
   or hide a genuine common initialization error. This batch changes **Spawns only**: if the
   existing Door contract is insufficient, stop R3; a provider API change would be a separate
   reviewed batch, not an implicit fallback.
5. Keep death/scout decisions, request packets, server authority, disconnect cleanup, and the
   spectator daylight Mixin unchanged.

**Regression cases:** Door absent; current contract; old Door missing marker; unsupported contract
version; compatible common API but missing/incompatible client API; dedicated server with no
client classes; repeated registration; disconnect/rejoin cleanup. Use isolated classpaths or
processes for missing-class cases rather than a boolean pretending the class is absent.

**Gameplay checks:** native death remains native when bridge unavailable; valid scout action
appears only under existing eligibility rules; one action produces one request; unavailable,
hardcore, stale-episode and rejected-server paths remain rejected. Run a real server/client
interaction only with applicable approval; unit tests do not establish packet delivery.

**Tests:** proposed `DoorBridgeAvailabilityTest.kt` and `ScoutBridgeRegistrationTest.kt`, plus
an existing/native fixture extended only as needed. Preserve existing refmap tests.

**Acceptance:** no class/method string probes in availability, same supported provider-version
matrix, no client classloading on dedicated server, no protocol/save changes, exact-once existing
registration. Verify the pinned Door input; do not silently rely on stale `build/libs` artifacts.

**Rollback:** revert the Spawns-only change. No saved death/scout state migration or Door API
change is permitted in this batch.

### R4. Explicit portable-handler owner — `better-manufacturing`

**Current boundary:**
`src/main/java/com/bettercontent/manufacturing/integration/createfluids/FluidNativeRates.java`;
related hooks in `.../mixin/createfluids/FluidTransferMixins.java` and
`FluidVariantBehaviour`. Existing unit tests include
`src/test/java/com/bettercontent/manufacturing/nativeparts/fluidslogistics/NativeFluidsLogisticsRatesTest.java`;
existing native fluid fixtures live under `src/wave2FluidsHeatFixture/` and
`src/wave2CreateFixture/`.

**Chosen implementation:** all identified owner-dependent calls originate inside the existing
`Portable` handler Mixin, where the outer owner is unavailable; implement a tiny typed owner
interface on that existing Mixin. Capture its pinned Create 6.0.8 constructor's explicit
`PortableFluidInterfaceBlockEntity` argument into a unique instance field at constructor return.
Verify the exact constructor bytecode/signature and transformed runtime mapping **before**
authoring the injection; if the signature or safe capture point differs, stop R4 and retain the
old path rather than choosing another private-field technique. Do not shadow/access `this$0`
under a different spelling, add a global owner map, or serialize the owner reference.

Use the interface in `FluidNativeRates.owner`. Preserve direct-owner handling and unknown/no
variant => native behavior (`Integer.MAX_VALUE` limit). Preserve the existing pinned target
failure policy; do not silently broaden version support or turn an optional fallback into a new
startup crash without review. This batch may **remove reflection without reducing Mixin count**.

**Regression cases:**

- Native/unconfigured/invalid variant uses exactly native transfer behavior.
- Two simultaneously active interfaces with different variants never share budgets/owners.
- Fill and both drain overloads preserve fluid identity, amount, NBT and partial acceptance.
- SIMULATE calls do not charge transfer budget or mutate inventory/fluid state.
- EXECUTE charges actual accepted/drained amount exactly once, not requested amount.
- Budget exhausts/resets at the existing tick boundary; clamping and copied FluidStack behavior
  are unchanged; no negative transfer or duplication.
- Attach/disconnect/reconnect, moving contraption, chunk unload/reload, and save/restart recreate
  the correct owner association with no retained global references.
- Unknown handler/fallback does not suppress normal native capability operation.

**Tests:** extend existing rate tests and add a focused provider-backed portable-interface
fixture. Validate the actual transformed constructor and production mappings, not just the
helper interface in isolation. Preserve existing native fluid fixture inclusion/exclusion rules.

**Acceptance:** no synthetic-name reflection, correct owner identity on the real transformed
handler, unchanged native fallback and fluid conservation, no new persistence fields or protocol.
Existing saves remain readable. If constructor capture cannot be verified, retain old behavior
and mark the batch blocked; do not replace it with polling.

**Rollback:** batch revert with unchanged world format. If data loss/duplication is observed in
a disposable fixture, retain the failed fixture for diagnosis; never use the friends' world to
prove a recovery path.

## 4. M1–M4 — assessed now, skipped for parity/cost

The user requested all four candidates now, then clarified to skip ones that cannot easily be
fixed. Static inspection of the pinned implementation shows that none of the **specified
substitutions** can currently replace its entire hook while preserving existing behavior. These
were assessed now, not silently deferred; status is skipped/retain rather than claiming a
refactor that breaks features. A broad renderer
rewrite, replacement worldgen system, or gameplay change remains outside the compatibility
scope. Provider extension or independently approved changed behavior would be a different task.
Do not delete these hooks or downgrade parity checks to force a Mixin-count reduction.

| ID / target | Candidate | Promote only if | Required behavior checks |
| --- | --- | --- | --- |
| M1 — Wildfire `SpriteSourceListMixin` | Atlas JSON/resource pack; generated pack only if existing dynamic resource support suffices | Same sprites appear under existing pack priority without new renderer/resource framework or stale reload state | Initial load and repeated resource reload; texture/resource-pack override precedence; no missing sprites; actual fire visuals unchanged in a real client |
| M2 — selected Guides/Journal presentation hooks | Existing `ScreenEvent`/`ContainerScreenEvent` hooks and resource assets | Exact lifecycle/order/private-state needs fit the events without replacing a foreign screen; select individual files after review | Loading progress/status remain accurate; screen resize/GUI scales; widget/input hitboxes; tooltip order; Curios/Polymorph behavior unchanged; real-client capture |
| M3 — Industrial Heat heater policy | Existing `BoilerHeater.REGISTRY` overrides | Public registry priority/fallback/tag behavior covers current passive/blaze policy; existing own-heater registration is not duplicated; chemistry removal owner has finished | Passive, blaze, own heater and third-party fallback; state changes/unload/reload; heat strength; no duplicate registration or changed energy accounting |
| M4 — Regolith Sourceberry support | `canSustainPlant` on owned farmland, supported provider integration for others | Every currently supported `c:farmland` and all relevant `mayPlaceOn` prechecks remain supported, not merely the owned block | Hand and automation placement, survival/neighbor updates, farmland moisture/trample, tags/reload; foreign tagged farmland and unrelated plants |

**Do not currently promote:**

- Survival HUD full overlay replacement: technically available but takes ownership of vanilla
  hotbar/offhand/spectator rendering. Revisit only if a real conflict/maintenance benefit
  outweighs that duplication; test all modes/scales/alpha restoration and other overlays.
- Dimension Fonts pool JSON replacement: removes private mutation but can discard other mods'
  additive pool entries. Require preservation of complete pool entries, weights, chance-element
  codec, reload behavior and third-party composition. Existing generated chunks must remain
  unchanged. Do not introduce a new pool-merging system just for this refactor.
- Seasonal clock rewrite: public tick events are not equivalent to exact native time ordering.
- New VS/Eureka migration API/fork: belongs to separately owned Spaceflight work, not this plan.

## 5. Complete per-mod disposition / better-option reference

This table preserves the audit's options in the repository so implementation does not depend
on a scratch file or conversation history. `Partial` never means the listed API replaces every
Mixin. Names omit `better-` except `scalable-tnt`. Counts from the earlier inventory are omitted
because removal and source edits are concurrent.

| Owner | Preferred option and retained boundary | Validation emphasis if later selected |
| --- | --- | --- |
| compat-fixes | R1 typed tooltip adapters; config/data/events for truly data-level fixes; retain private I/O/worldgen/concurrency/ABI patches until verified upstream fixes | Provider absence, handler ordering, patched failure reproduction and original behavior |
| manufacturing | R4 explicit owner; public Create behaviors/capabilities/recipes already cover part; retain native per-instance rate/force/pressure hooks | Simulation vs execution, exact-once budgets, conservation, moving machinery and saves |
| drinking-water | Owned item methods/use-item events for ordinary drinks; retain native cup/teapot/stockpot provenance/payment/persistence | Manual and automated transfer, nested consumption, purity/NBT, no double hydration, restart |
| spirit-commerce | Trade events/static recipes for ordinary additions; retain merchant transaction and Malum currency hooks | Shift-click/payment/stock limits, non-villager merchants, attribution, save/load |
| wild-fruit-trees | DT data/species/gen-feature APIs where supported; retain finite budgets, root lifecycle and existing species overrides | Repeated harvest, growth, reload/unload, original root data and worldgen |
| deaths-door | Public events for ordinary reactions, screen events for eligible presentation; retain exact damage/health/death commits | Damage/absorption ordering, lethal edge cases, synchronized health, revive/disconnect |
| discovery-guides | M2 for eligible presentation; real provider completion callbacks only for outcomes | Successful vs failed/cancelled/partial machine jobs, exact attribution, no possession shortcuts |
| industrial-heat | M3 selected heater policy; retain heat-aware generic merge and mutation hooks | Heat conservation across all merge paths; native/third-party heater behavior |
| spicy-farmers | Recipes/owned food methods where equivalent; retain native bites, Diet ordering, seasoning propagation | Full-hunger behavior, placed foods, slicing, effects and exact-once nutrient payment |
| bumblezone-crops | Growth/bonemeal events, recipes and loot only for paths that dispatch them | Direct mod growth, automation, harvest provenance, duplicate/drop-free cases |
| journal-inventory | M2 resource/screen-event candidates; retain recipe-book/private routing gaps | UI/input geometry, recipe-book state, Curios/Polymorph native transactions |
| survival-physiology | Diet data/public APIs and ordinary effect events; retain dynamic classification/decay/exhaustion gaps | Paid-food semantics, nested lunch bags, effect cadence/hidden effects, reload |
| spaceflight | Retain scoped default-off private boundary; supported upstream transaction/snapshot API is the real alternative | Owner-approved identity/joint/thread/recovery invariants; never weaken capture evidence |
| rpg-progression | Attributes/events where equivalent; retain launch/exhaustion/TACZ-specific gaps | Air/exhaustion at source, projectile/recoil/dispersion math and client/server agreement |
| seasonal-trees | DT SeasonManager for supported factors, not a replacement for leaf birth/dormancy bookkeeping | Protochunk promotion, seasonal boundaries, root removal, unloaded chunks |
| airtight-machines | Removal in progress; no refactor | Do not revive; coordinate any archival references |
| block-placement-preview | Prefer upstream Rotavision/RBP fixes; avoid reimplementing the preview | Placement-state parity, rotation memory, preview-only worlds, no rendering side effects |
| weathering-offline-progress | Chunk events for scheduling only; provider sampling API needed for replay internals | Probability/catch-up limits, no duplicate replay drops, offline timestamp behavior |
| chemlib-hazards | Removal in progress; no refactor | Do not revive; findings are time-bound |
| create-kinetic-loss | Retain network hooks; block stress registry is not topology-dependent loss | Split/merge/rebuild networks, synchronized stress, no stale capacity |
| ore-geology | Loot data for table-backed outputs; retain programmatic rewards/generated property hooks | Output identity, direct rewards, generated variants/models, save property compatibility |
| rehooked-grappling | Provider config where equivalent; retain native projectile extension gaps | Target synchronization, pulling/boats, detach and reload, dedicated server |
| wildfire | M1 atlas resources; retain native fire consumption/render gaps | Atlas reload and native spread/consumption/animation unchanged |
| seasonal-daylight | Retain exact-time hooks unless a separately justified clock design replaces them | Sleep/time commands, spectator pause, server/client interpolation, competing time writers |
| celestial-events | Static resource art only for static behavior; retain dynamic moon/time-lapse hooks | Dynamic moon state, sky render compatibility, Sleeping Overhaul timing |
| exploration-load-control | Retain pre-scan hooks; rejecting spawns later does not prevent scan cost | Actual scan avoidance, legitimate spawn/habitat behavior and exploration responsiveness |
| malum-dynamic-trees | DT selectors/structure data for static policy; retain contextual constraints | Species distribution, well constraints, generated-vs-existing terrain |
| survival-hud | Full public overlay replacement deferred; current narrow hooks may cost less | Hotbar/offhand/spectator modes, alpha restoration, other overlays, GUI scales |
| cave-encounters | Retain exact parent/child spawn boundary or use a real provider callback | Original spawn causality, failed spawn, unrelated entity joins |
| create-train-fuel | Retain native burn/NBT hook; recipes cannot replace accounting | Fuel depletion, idle/moving trains, unload/restart, attribution |
| dimension-fonts | Fixed pool JSON possible but composition loss blocks default migration | Pool weights/chance codec, other additions, new-village behavior and existing saves |
| rail-beetle | Verified upstream config transaction lock preferred; retain current scoped race fix | Concurrent initial-load/watcher transactions, valid JSON, no stale overwrite |
| regolith-farming | M4 plant-support API only if all foreign farmland/prechecks are covered | Placement/survival/automation and third-party tagged substrates |
| spawns | R3 typed bridge; retain spectator-time hook | Optional bridge loading and actions; daylight behavior stays unchanged |
| scalable-tnt | Retain radius argument modification; cancel/recreate explosion is not equivalent | Radius, damage/block effects, source attribution, no duplicate explosion events |

### Other reflection and excluded sources

- **Diagnostics generic recipes:** incrementally add typed adapters only for families with an
  actual correctness/maintenance benefit. Preserve complete output vectors, fluid inputs/outputs,
  chance and dynamic state semantics; do not substitute `getResultItem()` for every provider.
- **Diagnostics Fabric modifier:** retain the bounded empty-modifier fallback until a supported
  codec/API exists. An internal typed import is not a public-API migration.
- **Diagnostics EMI readiness:** a typed internal call removes reflection syntax but not ABI risk.
  Keep unless documented readiness semantics are established; registration is not completion.
- **Spaceflight Eureka getters:** public getters could be typed; private force state, original
  serializer identity, capability writer/lazy state, and visible/updating/pending-unload chunk
  coverage still need equivalent supported APIs. Coordinate with its owner; no work here.
- **Excluded Cooking:** ordinary data/consumption APIs cover only part; native placed-food and
  stockpot transaction provenance still require provider integration. Do not revive.
- **Excluded World Management:** lifecycle/screen events cover some UI, not pre-login admission,
  atomic saved-data/region storage or native schematic provenance. Do not revive.
- **Excluded Finite Water:** source-creation events/gamerules can disable infinite sources, not
  preserve finite-volume simulation. That is a feature change, not a refactor.
- **Excluded Electrochemistry:** Create behavior attachment may cover plumbing, not basin
  arbitration. Do not revive.
- **Excluded Dynamic Trees Dimension Compat:** upstream primitive-leaf mapping support preferred;
  fixed data only if generation/priority is equivalent. Do not revive.
- **Harnesses/fixtures:** keep ordinary annotation-based test discovery and intentional forbidden-
  reflection negative controls. Prefer public helpers or explicit context for private harness
  access, but never replace original-callback observations with mocks/recomputed values and call
  that equivalent evidence. Fixture-only Mixins are not gameplay debt.

## 6. Test strategy and acceptance gates

Use the existing owning repositories' tests and fixtures. Do not resume or extend the deferred
full pack qualification suite to carry this task. Keep fixtures nonshipping. A small shared
pattern is fine; a new testing platform is not part of the plan.

### G0 — baseline and expected behavior (before replacement)

- Characterize existing behavior with explicit inputs/expected outputs and known failure cases.
- **Author assertions against the existing implementation first**, including the negative cases
  and native fallbacks below. Request permission to run them on the unchanged baseline. If
  baseline execution is unavailable, the replacement is not parity-verified and cannot advance
  to integration. Preserve receipts from the same pinned provider/build environment.
- Derive expected values independently of the replacement implementation. Differential equality
  alone does not prove correctness if both paths share a bug; preserve documented behavior unless
  a separate bug fix is justified.

| Batch | Before/after parity oracle | Native/absence check required before integration |
| --- | --- | --- |
| R1 | Same semantic tooltip rows, harvest filtering, invalid tags and detached-stack immutability | Blood Magic/Ars separately present and absent; real client hover; dedicated server |
| R2 | Same exported graph rows, alternative stacks/NBT, pressure, error/incomplete markers | Real pinned recipe corpus, no crafting side effects; absent provider |
| R3 | Same bridge-version availability, registration count and scout/death decisions | Missing/old/current Door, incompatible client API, dedicated server, actual authorized action |
| R4 | Same SIMULATE/EXECUTE fluid balances, tick budgets and native fallback | Two simultaneous owners, transformed Create constructor, save/reopen, real native handler |

A unit test passing only against a fake provider does not satisfy the corresponding native
check. Do not call a removed test or a shortened assertion list feature parity.

### G1 — focused JVM behavior

Run only after approval. Examples of intended selectors, **not commands executed or automatically
approved by this document**:

```sh
# From the named repository; verify final source-set/classpath/task prerequisites first.
# New *AdapterTest/*AvailabilityTest names are proposed and may be finalized during authoring.
./gradlew test --tests '*ToolTooltipTest' --tests '*ExternalToolTooltipAdapterTest' --max-workers=2
./gradlew test --tests '*RecipeGraphExporterTest' --tests '*PneumaticRecipeAdapterTest' --max-workers=2
./gradlew test --tests '*DoorBridgeAvailabilityTest' --tests '*ScoutBridgeRegistrationTest' --max-workers=2
./gradlew test --tests '*NativeFluidsLogisticsRatesTest' --max-workers=2
```

Inspect build scripts first: even a filtered `test` may compile native fixtures or execute task
prerequisites. Get approval for the actual execution scope, not just the selector string. Use
existing serialization/locking conventions; do not run these examples concurrently by default.

### G2 — absence, side, and ABI compatibility

- Isolated provider-absent/present/old/incompatible classpaths; common and client boundaries.
- Dedicated-server initialization without client classes, not merely a mocked side flag.
- Verify real optional classes are absent in the absence branch; a classpath that still contains
  test dependencies cannot prove this.
- Inspect compiled references/packaged contents to catch accidental provider bundling, eager
  references, fixture leakage and stale reflection. Structural checks supplement, not replace,
  behavior tests. Do not add broad source-string count tests as the main acceptance gate.

### G3 — native transformed/provider behavior

Required where JVM tests cannot establish the contract, especially R4 and any removed Mixin.
Use real pinned providers and the actual reobfuscated candidate under an approved fixture.
Validate original callback ordering and actual outcomes. A dev-only untransformed constructor
or successful compile is not evidence that a Mixin works in production mappings.

Use disposable worlds/fixtures, preserve failed evidence, and never change live/player saves.
Do not download new provider versions to make the tests easier.

### G4 — scoped client/server and persistence behavior

Select only checks relevant to the batch, with explicit approval if not already covered:

- R1: real client hover with actual provider items; no state mutation/visual regression.
- R2: real server diagnostic export against the pinned recipe corpus; no crafting side effects.
- R3: actual server-authorized scout action and reconnect/reset behavior; no changed native death.
- R4: native portable fluid operations, simultaneous owners, moving contraption and save/reopen.
- Any later separately approved M1/M2 work would require actual Minecraft screenshots/input
  checks; web/placeholder screenshots cannot validate Minecraft rendering. Add multiplayer
  checks when synchronized gameplay is touched, not by claiming Smoke covers them.

Save-format changes are prohibited in Wave 1. A necessary migration or registry/protocol change
is a scope stop, not an incidental test update. Retain exact prior artifacts and untouched save
copies; reverting code alone cannot undo mutated world data.

### G5 — integration/release handoff

- Review the diff, updated dependency/side boundary, fixture exclusion and complete batch results.
- Record source/provider/artifact identity, commands, authorization scope, outcomes and remaining
  gaps in the ledger; link existing test receipts/logs rather than inventing a receipt framework.
- Only then hand off to the current release owner. Packaging, staging/deployment and publication
  remain separately controlled by [testing.md](testing.md) and repository release requirements.
- Exact-artifact Smoke may be applicable under standing approval after an authorized candidate
  exists; it is not a replacement for G1–G4. Do not mutate accepted distributions or a running JVM.

## 7. Stop conditions and rollback

Stop the affected batch when:

- a replacement requires weaker semantics, polling, a new registry/persistence/network design,
  an upstream fork, or substantial unrelated refactoring;
- the optional-mod/side matrix cannot be preserved, provider ABI is unknown, or production
  remapping/native interception is unverified;
- tests cannot run under current approval: source may remain authored/unrun, but the batch is not
  validated/integration-ready;
- there is ownership conflict, baseline/provider drift, failed dependency verification, or any
  failing required check.

Use a separate reviewable change per R/M item. Remove only that item's obsolete production
adapter/hook/config entry after its replacement passes the applicable gates. Preserve regression
coverage and original behavior records; do not leave two active implementations as a rollback
switch. Revert only owned changes, retaining peer/user edits. Restore accepted artifacts through
the normal release process, never by overwriting evidence or silently hot-patching installations.

## 8. Implementation ledger — update this file while working

`planned` → `authoring` → `authored/unrun` → `validated` → `integrated`.
Use `blocked`, `retained`, or `deferred` where appropriate. Do not equate compiled with validated.

| ID | Status | Owner/files | Evidence / next gate |
| --- | --- | --- | --- |
| R1 | focused JVM tests passed; native held | w1H:p3; Compat Fixes optional tooltip boundary | 9/9 across three selectors passed; authorized local JAR staging only; baseline, real client and absent-provider checks still required |
| R2 | focused JVM tests passed; native held | w1H:p3; Diagnostics pressure-chamber adapter | 25/25 across two selectors passed after fixture repair; real recipe corpus, absent-provider and baseline checks still required |
| R3 | focused JVM tests passed; native held | w1H:p3; Spawns availability/client registration | 2/2 bridge source/version tests passed; authorized local `jar`/`reobfJar` only; old/missing Door/server/client matrix still required |
| R4 | blocked at prerequisite | w1H:p3; Manufacturing portable fluid owner | `test` task stopped before source compile/test: Heat requires typed Water/Spice classes; rebuilding Water requires qualified Compat runtime JAR; never bypass or use stale inputs |
| M1 | skipped/retain | w1H:p3 inspected Wildfire source; no product edit | Programmatic dynamic sprite source lacks serialized codec; static atlas JSON cannot enumerate reload-selected provider/model textures |
| M2 | skipped/retain | w1H:p3 inspected Guides/Journal presentation; no product edit | Loading status/private render order and container foreground-before-tooltip order are not provided by the proposed screen events |
| M3 | skipped/retain | w1H:p3 inspected Heat and pinned Create 6.0.8; no product edit | Create registry rejects duplicate blaze registration; direct entries outrank providers, so registry override cannot replace blaze hook |
| M4 | skipped/retain | w1H:p3 inspected Regolith and Forge plant API; no product edit | Owned farmland method cannot cover foreign `c:farmland` nor Sourceberry's `mayPlaceOn` precheck |
| Other owners | retained/deferred | No implementation claim | Use per-mod matrix; promote only with concrete equivalent API and bounded value |

For each batch append a short dated note here: baseline identity, exact files, replacement,
expected behavior, tests authored, tests authorized/run, result/log location, unresolved gaps,
and rollback reference. No separate tracking service is needed.

### 2026-11-17 — plan creation

- Product/source audit read-only; only this plan authored in the pack repository.
- No builds, tests, packaging, staging, deployment, publication, or gameplay changes.
- Existing dirty Occultism chemistry script and untracked Electrochemistry/Arena preservation
  files belong to concurrent work and were left untouched.
- Implementation has not started. First bounded batch: **R1**, tests authored before replacement.
- After the user raised feature-breakage concerns, this plan was finalized with strict parity
  gates and R1–R4 in scope; the later user request expanded active assessment to M1–M4.

### 2026-11-17 — R1–R4 source authoring (unvalidated)

- Baselines: Compat Fixes `2b20f57`, Diagnostics `8d1cc8e`, Spawns `5e4be5b`, Manufacturing
  `3d49031` (working trees clean at authoring start). Pins: Blood Magic `7956981`, Ars `6688854`,
  PneumaticCraft `7307654`, Create `6.0.8-291`; Spawns uses the existing Door `1.0.0` compile-only
  contract. Re-check exact artifact hashes before running or staging anything.
- R1: only `ExternalToolTooltipSummary`, two new client-only adapters, pinned compile/test
  dependencies, `ToolTooltipTest`, `BloodMagicTooltipAdapterTest` and
  `ArsReactiveTooltipAdapterTest` changed. Characterized
  harvest visibility/remaining uses and detached adapter boundaries in source tests. Real tooltip
  rows, mutation and optional-classpath checks are **unverified**.
- R2: replaced the reflective Pneumatic adapter with a public-type adapter and explicit
  incomplete marker for unsupported pressure-chamber recipe classes; edited exporter/test and
  pinned compile/test dependency. `PneumaticRecipeAdapterTest` uses a typed provider subclass;
  genuine recipes and missing-provider behavior are **unverified**.
- R3: consumer-only typed Door common/client adapter and packet dispatch isolation; added
  `DoorBridgeAvailabilityTest`. Version rule has a pure assertion; old/missing provider and real
  scout packet behavior are **unverified**. No Door source or protocol changes.
- R4: `javap` statically confirmed the pinned Create handler constructor takes an explicit
  `PortableFluidInterfaceBlockEntity` followed by `IFluidHandler`. Existing handler Mixin now
  captures this argument behind `PortableFluidOwner`, with a new structural/native-fallback
  test. Transformed Mixin delivery, owner identity, fluid conservation and save parity are
  **unverified**; no Mixin was removed.
- **No baseline test was executed before replacement** because test approval was requested but
  not granted. Preserve the old clean commit as the baseline and request approval to run scoped
  unchanged-baseline and candidate checks (isolated checkout or otherwise protected); until
  then this is authored source only, not a demonstrated parity migration. Do not package or
  integrate any batch. No build, test, GameTest, world, staging, deployment or publication run.
- Rollback references are the four clean starting commits above; revert only these owned edits,
  not concurrent pack/removal changes. No persistence or network migration was introduced.

### 2026-11-17 — M1–M4 requested now: parity inspection, not substitutions

- **M1 Wildfire:** `CharredSpriteSource.run` enumerates currently registered blocks, follows
  selected model/texture resources and metadata on *each* reload, then creates sprites for all
  matching originals. Its `type()` deliberately throws because there is no serialized atlas
  codec. Atlas JSON or fixed resources cannot replace the dynamically selected original textures
  across optional mods, resource-pack priority, animation metadata and reload. No equivalent
  pinned resource-generation path was established. Keep `SpriteSourceListMixin` for now.
- **M2 Guides/Journal:** `ConnectScreenLoadingMixin` reads private status and intercepts *after*
  native narration but before native status drawing. `ReceivingLevelScreenLoadingMixin` similarly
  replaces native render while keeping `super.render`. In Journal, `JournalPanelRenderMixin`
  draws before carried stacks/tooltips, whereas its existing `ScreenEvent.Render.Post` is
  explicitly only a fallback for modal paths. Event-only rewrites would change status, render
  order, or input geometry. Keep these hooks; no overlap with Guides chemistry owner.
- **M3 Industrial Heat:** pinned Create 6.0.8 `BoilerHeaters.registerDefaults()` installs blaze
  as a direct `SimpleRegistry` entry and passive heaters by tag provider. `SingleImpl.get()` checks
  direct entries **before** any provider; `register` throws on a duplicate direct block. Adding
  `BoilerHeater.REGISTRY` providers or duplicate registration cannot override native blaze
  behavior. Keep passive/blaze policy hooks; own heater registration already uses public API.
  Heat build/test files are concurrently owned by the chemistry-removal worker and untouched.
- **M4 Regolith:** `RegolithFarmlandBlock.canSustainPlant` already covers own farmland where
  supported, but the existing `BushBlock.mayPlaceOn` return hook accepts Sourceberry on **every**
  `c:farmland` block, including foreign substrates and pre-survival placement checks. Extending
  only owned blocks or relying on later growth/placement events loses supported placements.
  Keep the narrow hook until a provider-controlled precheck extension exists.
- These are **source/static API findings**, not test results or proof that no future upstream
  extension can exist. The additional Journal presentation hooks reviewed were likewise not
  event-equivalent: tab rendering cancels native widget drawing, the list hook changes native
  tint calls, and the Curios recipe-book hooks intervene before native placement. No M1–M4
  product/test files, dependency graphs, builds or worlds changed; no friend playtest observations
  were used. Per the latest user instruction, leave these four Mixins in place rather than build
  provider forks, rewrite rendering, or accept reduced feature coverage.

### 2026-11-17 — user-authorized focused testing (incomplete)

- User authorized testing at ~22:30 UTC. All Gradle calls used `--offline --no-daemon
  --max-workers=2` and the shared `$HOME/.tmp/better-content-gradle.lock`. No pack test tier,
  GameTest, release packaging, deployment, visual or multiplayer runtime was run.
- **R2:** `test --tests '*RecipeGraphExporterTest' --tests '*PneumaticRecipeAdapterTest'` first
  failed compilation because the typed fixture incorrectly returned `List` rather than
  `NonNullList`; after repair it compiled but `ItemStack.EMPTY` triggered a JVM-only Minecraft
  `Not bootstrapped` initializer. Replaced that fixture's outputs with empty alternative lists
  (native item-count/NBT coverage remains necessary). Final retry **25 tests passed, zero failed**;
  log `/home/dev/.tmp/hook-refactor-r2-test-20261117-retry2.log`. Earlier failed runs are retained.
- **R1:** `compileJava` passed (39 deprecation warnings); log
  `/home/dev/.tmp/hook-refactor-r1-compile-20261117.log`. Focused `test` dry-run includes
  `jarJar`, `reobfJarJar`, `stageRuntimeJar`, native fixture preparation and `jacocoTestReport`;
  no R1 tests run because test permission does not authorize the incidental local runtime-JAR
  staging. Need explicit permission for those prerequisites and review exact input identities.
- **R3:** `compileKotlin` passed; log `/home/dev/.tmp/hook-refactor-r3-compile-20261117.log`.
  Focused `test` dry-run includes `jar` and `reobfJar`; no R3 tests run pending explicit
  local-artifact prerequisite permission. No Door provider source changed.
- **R4:** focused `test` dry-run included `compileIndustrialHeatOwner` and
  `compileRailBeetleOwner`, no Hazards/Airtight/jar/stage tasks. With supervisor coordination,
  first attempt stopped at Heat configuration requiring typed Water/Spice compile-class paths;
  log `/home/dev/.tmp/hook-refactor-r4-test-20261117.log`. Heat's complete `src` plus Gradle
  input manifest is byte-identical before/after, SHA256
  `d769d170b870b1ee3350dae87fb44d8a318f30b1a29cb9e582e4ec4c14ce5b16`.
  Spice compileJava is UP-TO-DATE using pinned Kaleidoscope 1.6.0 SHA
  `0eee5579b36746825d6586c65051d9e68fc84178a19dec4372fc9d64b8629d62`;
  Water source refresh cannot configure without a *qualified* Compat Fixes runtime JAR. Do
  not bind stale compiled Water/Compat or bypass those requirements. No Manufacturing source
  compile or test executed and no Heat source changed.
- None of these compile/JVM outcomes proves feature parity, real optional-mod absence,
  transformed Mixin delivery, client presentation, multiplayer or save/restart behavior.

### 2026-11-17 — explicit local artifact-prerequisite authorization; focused tests

- User subsequently authorized local JAR/reobfuscation/staging tasks **solely as prerequisites**
  to the focused Compat Fixes and Spawns tests. Under the shared Gradle lock with offline
  resolution and two workers, R3 `test --tests '*DoorBridgeAvailabilityTest'` executed
  `jar`/`reobfJar` and **2/2 passed**; log `/home/dev/.tmp/hook-refactor-r3-test-20261117.log`.
- R1 `test --tests '*ToolTooltipTest' --tests '*BloodMagicTooltipAdapterTest'
  --tests '*ArsReactiveTooltipAdapterTest'` executed its declared local test prerequisites,
  including `jarJar`/`reobfJarJar`/`stageRuntimeJar`, native fixture **compilation/preparation
  only**, and `jacocoTestReport`; **9/9 passed**, log
  `/home/dev/.tmp/hook-refactor-r1-test-20261117.log`. A local JAR in `build/libs` is not an
  accepted or installed pack artifact; no GameTest, world, client, release package or deployment.
- R4 remains held: a local Compat Fixes test pass/staged JAR does **not** satisfy Water's
  qualified-Compat runtime input requirement. No silent substitution or Manufacturing test run.
- The tests above are scoped/pure and structural; they do not establish real provider-absent
  loading, runtime Mixin delivery, recipe corpus equivalence, UI hover, packet behavior,
  save/restart or multiplayer parity. Baseline execution before replacement has not occurred.

## 9. Audit evidence / source references

The plan above is self-contained; these local scratch files provide deeper inspection evidence,
not required runtime inputs or a guarantee of current membership:

- `/home/dev/.tmp/mod-cost-audit-20261117/hook-alternatives.md` — full earlier assessment.
- Same directory: `hooks-inventory.json`, `hooks-audit.md`, `hook-targets.md` — source target inventory.
- `public-api-signatures.txt` — inspected public signatures for pinned Blood Magic, Ars Nouveau,
  PneumaticCraft, Create 6.0.8, Dynamic Trees 1.4.10.
- `pinned-forge/` — selected Forge 1.20.1–47.4.22 event definitions and BushBlock patch.

Those are static inspection results, not execution evidence. Verify current pins and source
before implementing. Prefer the closest existing repository tests; obey the current instructions
rather than treating this document as authorization to bypass them.
