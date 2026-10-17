# Celestial events

## Scope and authority

[Better Celestial Events](../../mod_source/better-celestial-events/README.md) owns the rare-night
calendar, synchronized moon presentation, Thin Veil encounters and Wandering Lights.
[Dimension Fonts](../../mod_source/better-dimension-fonts/README.md) owns travel costs, return safety,
Font visuals and the read-only natural-site locator. [Spirit Commerce](../../mod_source/better-spirit-commerce/README.md)
owns fair traders, camps and stock. The pack owns the explicit habitat tags under
`kubejs/data/better_celestial_events/tags/worldgen/biome/`. The executable build graph is
`gradle/active-custom-mods.json`; consumers depend on the celestial provider, never the reverse.

Source and authored artwork define intended implementation; isolated checks are not full-pack
acceptance. Deployment, Packwiz refresh, packaging and Dist/Debug remain separate authorization
under [testing](testing.md).

## Four rare nights

One special moon is scheduled every 8–12 Overworld days, in shuffled four-event cycles without
adjacent repeats. The first is 8–12 days after initialization. Effects last from dusk (12000)
until dawn (24000) on the Overworld clock, even for travellers in another dimension. Sleeping
cannot skip these nights; rain does not cancel them. Paused daylight pauses their duration.

| Moon | What changes | What to do |
|---|---|---|
| Thin Veil | Sparse restless spirits and wraiths appear in suitable surface forests and marshes. | Hunt spirits outdoors; ordinary native drops and credited-kill spirit rules still apply. |
| Spirit Fair | One extra themed trader can establish a camp, subject to safe terrain and spawning rules. | Visit the announced camp; goods use existing authored prices and finite stock. |
| Wandering Lights | Pale wisps suggest directions to nearby indexed natural Fonts. | Follow their direction, choosing your own safe route across terrain. |
| Open Ways | Opening, joining and running a bound Font expedition consume no charge until dawn. | Prepare an expedition; watch the dawn warnings and ensure charge or a safe return. |

Wandering Lights consult world-indexed natural sites, not personal exploration history. No known
site within range means no guidance. Neither lights nor Font particle plumes load distant chunks.
Thin Veil has bounded loaded-world attempts and never changes cave ecology, creates bosses,
multiplies drops or introduces event-only progression items. Witching Moon is not implemented.

## Open Ways boundaries

Empty but otherwise valid bound Fonts can open during Open Ways. Their actual charge remains
unchanged by the waiver; active free expeditions do not regenerate charge. Pourers pause fluid
consumption. Existing binding, cooldown, destination, admission and chunk-ticket safety rules
remain. Return-only seals do not become expedition entrances.

Travellers receive warnings before dawn. Normal drain then resumes, without retroactive opening
charges. If the remaining real charge cannot sustain travel, native safe extraction returns the
travellers. Relogging, restarting or changing dimension does not extend the moon.

## Artwork and verification

Selected Codex image-generation masters and prompts are authored inputs in the celestial source
repository's `art/moons`. Its deterministic conversion script makes phase atlases with
nearest-neighbour sampling and explicit masks. Only the Overworld moon texture is substituted;
normal nights, other skies and ordinary moon geometry remain native. Shader compatibility is
not inferred from source checks.

Use the repositories' documented source gates and focused native/visual harnesses. Acceptance
includes sleep/timelapse denial, restart/time-jump safety, encounter budgets and expiry, finite
fair stock, loaded-only lights, charge-free travel and safe dawn return, synchronized clients,
and actual visual review of every moon and Font particles. Full-pack acceptance is separate.
