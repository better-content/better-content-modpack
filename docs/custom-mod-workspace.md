# Custom-mod workspace and build inputs

## Repository inventory

Custom-mod checkouts are under `/home/dev/mod_source/` beside the modpack.
The active identities and provider graph are in
[`gradle/active-custom-mods.json`](../gradle/active-custom-mods.json).
Current runtime identities have no legacy config/world identity migration.

| Non-active repository | Feature status |
|---|---|
| `better-abyssal-city` | Isolated experimental prototype; not an active pack mod |
| `burnt-grass-compat` | Validation-only; active wildfire owns pack behavior |
| `dynamic-trees-dimension-compat` | Validation-only; Undergarden retired |
| `better-ratlantis-logistics` | Retired/history-only after Ratlantis cut |

## Wild fruit integration

`better-wild-fruit-trees` is active in the release manifest and retains its standalone behavior.
Its pinned Dynamic Trees, DT Fruits Delight, Fruits Delight and Farmer's Delight inputs are
release verification inputs; optional Ars Nouveau, Tinkers' Construct and Mantle pins also bind
its berry/actual-tool-tier native fixtures. The pack's replacement global loot list explicitly retains
`better_wild_fruit_trees:no_propagules` before the final Bumblezone cultivar modifier; no
parallel KubeJS propagation or yield implementation is introduced. See [food progression](food_progression.md)
and the [owning behavior contract](../../mod_source/better-wild-fruit-trees/README.md).
