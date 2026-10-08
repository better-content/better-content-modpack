# Native startup policy: pinned updates and casting-cache ordering

## Scope and retained evidence

The focused build403 run `20261008T063512Z-3343772` passed all 202 journal rows,
initial merge, real client reconnect, and fresh-server-restart full-NBT persistence
checks. Its final strict log audit failed. The retained `checkpoint_failed` event
names Collective's update-check warning and TConstruct's inconsistent material cost,
as well as two separately owned EMI/Sophisticated issues. Process cleanup completed
with no surviving PIDs. Those observations are not a strict-suite pass.

This change disables an optional network feature through its native setting and
changes native recipe construction order. It does not upgrade dependencies, filter
logs, whitelist failures, edit upstream code, or change recipe JSON or material costs.

## Collective

`config/collective.json5` sets only `enableUpdateChecker` to `false`.
The generated build403 config had this native flag enabled. In pinned Collective
8.40, `com.natamus.collective_common_forge.check.RegisterMod.register(...)` reads
`CollectiveConfigHandler.enableUpdateChecker` first and returns immediately when
false, before starting the asynchronous update checker. Other native defaults remain
unchanged. Forge's `config/fml.toml` `versionCheck` is a separate feature and is not
changed by this remedy.

## TConstruct / KubeJS: construction-order hypothesis

Pinned TConstruct `3.11.2.166` has these native recipes:

- `tconstruct:tools/parts/fake_storage_block_casting`: `item_cost: 9`;
- `tconstruct:tools/parts/fake_storage_block_composite`: `item_cost: 9`.

Inspection of the retained candidate's mod recipe resources found no competing
fake-storage casting JSON with a different cost. The pack scripts do not override
those costs. This does not substitute for a live effective-recipe observation.

`MaterialCastingRecipe` construction calls the public native
`MaterialCastingLookup.registerItemCost(IMaterialItem, int)`. Composite casting
inherits that constructor. The lookup uses an unsynchronized static
`Object2IntOpenHashMap`; conflicting registrations log an error and retain the lower
value. The observed error occurred on `KubeJS Recipe Event Worker 0`.

Pinned KubeJS `2001.6.5-build.16` performs final original/added recipe conversion using
unconditional parallel streams. `RecipeJS.getOriginalRecipe()` lazily deserializes
and caches a native recipe. The combination suggests a concurrent registration/cache
initialization race. This remains a **hypothesis**, and the remedy is **not runtime-proven**
until the coordinated canonical candidate passes the strict native log gate.

`kubejs/server_scripts/policy/native_material_cost_prewarm.js` uses supported
`// priority: 1000` metadata to register its recipe callback before ordinary priority-0
scripts. It uses public, sequential `event.forEachRecipe(...)` and
`recipe.getOriginalRecipe()` for exactly four serializers:

- `tconstruct:table_casting_material`;
- `tconstruct:basin_casting_material`;
- `tconstruct:table_casting_composite`;
- `tconstruct:basin_casting_composite`.

No JSON fields, costs, output stacks, or lookup entries are assigned by the script.
A missing decoded native recipe throws rather than pretending that prewarming worked.
Unchanged recipes subsequently reuse KubeJS's cached original native instances.
Later scripts that genuinely modify these recipes remain responsible for native
reconstruction and validation; this is not a general serialization lock.

### Verified pinned API / ordering

Read-only bytecode inspection established:

1. `ScriptFileInfo.preload` parses `//` metadata properties and integer `priority`.
2. `ScriptFile.compareTo` uses `Integer.compare(other.priority, this.priority)`;
   `ScriptManager` sorts scripts before execution, so larger priorities load first.
3. `RecipesEventJS.forEachRecipe` uses `recipeStream(...).forEach(...)`, not its async
   stream path.
4. `RecipeJS.getOriginalRecipe` calls the native serializer once and stores the result;
   unchanged `createRecipe()` returns that cached original.
5. `allowAsyncStreams=false` only controls `recipeStreamAsync`; it does **not** disable
   final `lambda$post$6`/`lambda$post$7` parallel conversion. It is not changed here.

The focused Dev source guards verify the native feature key, supported priority,
exact four filters, public cache call, and absence of recipe/cache mutation. These
checks are not native runtime proof. No packaging, refresh, commit, or pack retry is
performed by this authoring change; combined gates and the next immutable-candidate
release remain coordinator-owned.
