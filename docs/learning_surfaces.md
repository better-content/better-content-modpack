# Learning surfaces

Better Content teaches through the systems the player encounters, not an achievement
ledger or objective graph. FTB Quests and its custom integration are not active pack
content. Their tasks and rewards are not transferred into Threads or another system.

## Information ownership

| Surface | Responsibility |
| --- | --- |
| Loading lessons and Threads Lessons | Spoiler-free fundamentals and changed mental models, available before the relevant event |
| Death-screen hints | Brief recovery advice and whole-pack discovery after death, with occasional late-game teasers |
| Contextual Thread cards | A broader rule discovered through actual play evidence and remembered across the lineage |
| Item-hover annotations | Concise item-local facts, identical in inventory and EMI hover |
| EMI, Ponder, native guides and system screens | Exact recipes, apparatus, multiblocks, operating instructions, and native state |
| HUD, events and world feedback | Immediate controls, warnings, scouting, rescue, and situation-dependent guidance |

Use a shared concept ID when explanations address the same concept, while preserving
each surface's depth. Hover stays one or two concise lines; a lesson does not reveal
an unknown Thread. Native tooltips that already explain the pack model need no duplicate
annotation. No primary surface sends players to an achievement ledger for instruction.

## Authoring contract

Start with the player action or misconception and identify the implementing recipe,
event, configuration, tag, or owning custom mod. Read that authority before writing
copy. Possession must not be described as operating a machine, and intended future
behavior must not be presented as implemented.

Preserve stable concept, Thread, and lesson identities when their meaning is unchanged.
Threads has 52 live cards, four suits of thirteen; the Lessons reference and loading
rotation share 16 lessons. Follow [Threads](threads.md) for definition schemas,
correlated reveal/completion episodes, native doorways, and persistence. Domain mods
own their gameplay events; Threads adapters translate those events into card signals.

Annotate pack transition families and curriculum-important systems with a natural
item anchor unless native hover already teaches the relevant model. Leave ingredients,
layouts, and ordinary uses to EMI; leave dynamic stack state to the owning mod. Keep
non-item guidance on its event, HUD, loading, Thread, or world surface.

Teaching does not mint commerce spirits or restore retired rewards. Thread facsimiles
remain cosmetic; lessons and the reader are not progression gates. Commerce spirits
come from credited player kills under the native-mapping and fallback rules described
in [Content ownership](content_systems.md).

## Writing tone

Use clear, direct language on every Better Content-owned learning surface. Name the
mechanic, explain the rule, then give a useful action. Titles describe their subject;
proper names such as Threads, Source, and Dimensional Fonts remain unchanged.

Do not add lore, metaphors, personification, rhetorical invitations, or filler to
instructions. Replace internal design terms such as "bootstrap", "authority", and
"progression branch" with what the player can do or needs. Keep concrete requirements,
limits, costs, and warnings. Short copy does not need a minimum word count.

Threads use a title, rule, and action; there is no prose or invitation layer. Hover
annotations remain one or two lines totaling at most 24 words. Apply this tone to
pack-authored guide notices, not to copied third-party guide prose or narrative design
documents.

## Readability and timing

- State the mechanical rule and a useful next action directly. Explain what
  the player can do; do not turn event predicates such as death or a terminal campaign
  outcome into instructions to seek that outcome.
- Preserve complete sentences. Wrap headlines, rules, and actions; use visible scrolling
  when they cannot fit. At narrow GUI widths, catalogues use one column. Decorative art
  may yield space to text, but art keeps its original aspect ratio.
- Use readable neutral text on a dark backing. Aspect pigments belong in badges and
  accents; dark pigments must not become the only way to read a label. Ordinary pack
  hover text uses light gray, with explicit wording for warnings.
- Loading copy fits its actual wrapped height and never delays entering the world.
  A lesson needs eight seconds of rendered exposure to advance loading history; quick
  joins and loading stalls do not count as reading. All lessons remain directly available.
- Movement lessons display the current loaded bindings. Menus must not consume the
  lifetime of a meal recap, and larger recaps need more reading time than a single line.
- Show a native doorway only when its installed target can be opened. Do not substitute
  a raw resource ID, a guessed key binding, or a vague message for a working guide.
- Keep player-authored Trace notes distinct from curated instructions. Their usefulness
  depends on what players leave; the pack supplies readable, contextual presentation.

## Review and validation

- Trace every changed claim and trigger to its implementation; inspect related surfaces
  for contradictory copy or duplicated authority.
- Check stable concepts, current native doorways, bounded copy, and relevant existing
  repository-local tests. Follow [Thread Art Direction](thread_art_direction.md) for art.
- Review reader layout, loading presentation, and development crossfades manually when
  changed. Automated tests must not synthesize mouse movement or clicks.
- Report changed player-visible behavior and verification. Run pack suites only when
  explicitly ordered through `test.main.kts`; otherwise state that pack testing was
  intentionally omitted under the frugal-testing policy.

Do not recreate a quest compiler, layout harness, atlas exporter, or parallel validation
framework. Durable guidance belongs here; raw evidence belongs outside living docs.

## Death-screen hints

Threads owns a 96-entry death-hint catalogue alongside its loading lessons: 40 survival/recovery
tips, 44 broader pack tips, and 12 light late-game teasers. Hints use shared concept IDs and record
mechanical source references. Teasers may introduce an undiscovered possibility without revealing
its complete recipe, unlocking a Thread, or claiming progression credit.

Show one stable hint beneath the existing death controls. Prefer reliable cause-specific advice
on three of four recognized deaths; otherwise choose general advice, with teasers weighted at
one in eight general selections. Unknown damage gets general advice rather than a guessed diagnosis.
Downed episodes retain their original cause until revival or final death. Require relevant mods,
avoid the last twelve displayed hints when possible, and keep this client-local history separate
from loading exposure and lineage progress.

Keep the text concise, neutral, and actionable. Mention actual current bindings where needed.
Wrap without shrinking, and omit the hint when the space beneath native controls is too small.
Hints never delay respawning or change normal/hardcore death controls. Resource packs can replace
`assets/better_content_threads/death_hints/catalogue.json`; invalid catalogues log an error and use
built-in general advice. Validate content and selection locally, and inspect the existing visual
fixture; pack suites still require an explicit user order.
