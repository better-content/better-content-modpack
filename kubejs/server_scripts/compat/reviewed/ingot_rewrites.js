// priority: 0
ServerEvents.recipes(event => {
    // Legacy steel rewrites are intentionally disabled. The expert progression now
    // gates machine families through tiered casings instead of becoming a steel pack.
    console.log('[kubejs] iron/copper->steel rewrites disabled by casing progression pass');
    // The legacy brass lists mix deliberate machine gates with copper-themed
    // building blocks, storage tiers, drinks, and stale recipes from absent mods.
    // Explicit era-root recipes now own progression; do not mutate unrelated inputs.
    console.log('[kubejs] broad iron/copper->brass rewrites disabled; explicit recipe gates own progression');
});
