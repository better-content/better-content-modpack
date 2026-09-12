# Learning surfaces

Better Content teaches through the systems the player encounters, not an achievement
ledger or objective graph. FTB Quests and its custom integration are not active pack
content. Their tasks and rewards are not transferred into Threads or another system.

## Information ownership

| Surface | Responsibility |
| --- | --- |
| Loading lessons and Threads Lessons | Spoiler-free fundamentals and changed mental models, available before the relevant event |
| Main-menu tips | One stable preparation or possibility tip per application launch |
| Esc-menu tips | Practical advice selected from actual current conditions, stable while open |
| Death tips and native recap | Final-cause advice plus the current life’s injury and treatment history |
| Contextual Thread cards | A broader rule discovered through actual play evidence and remembered across the lineage |
| Item-hover annotations | Concise item-local facts, identical in inventory and EMI hover |
| EMI, Ponder, native guides and system screens | Exact recipes, apparatus, multiblocks, operating instructions, and native state |
| HUD, events and world feedback | Immediate controls, warnings, scouting, rescue, and situation-dependent guidance |

Use a shared concept ID when explanations address the same concept, while preserving
each surface's depth. Hover stays one or two concise lines; a lesson does not reveal
an unknown Thread. Native tooltips that already explain the pack model need no duplicate
annotation. No primary surface sends players to an achievement ledger for instruction.

Ore processing uses three contextual hover anchors. Chunks introduce String-Mesh
hand Sifting, crushed feeds contrast dry and waterlogged Sifting with Spouting,
and rinsed feeds state the four-at-2-bar pressure batch. The Encased Fan explicitly
corrects the retired Bulk Washing model; EMI owns exact chances and recipe layouts.

## Authoring contract

Start with the player action or misconception and identify the implementing recipe,
event, configuration, tag, or owning custom mod. Read that authority before writing
copy. Possession must not be described as operating a machine, and intended future
behavior must not be presented as implemented.

Preserve stable concept, Thread, and lesson identities when their meaning is unchanged.
Threads has 52 live discoveries across seven topics; the Lessons reference and loading
rotation share 17 lessons. Follow [Threads](threads.md) for the single-explanation schema,
committed outcome evidence, native doorways, and lineage persistence. Domain mods
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

Threads use a title, the event experienced, its cause, and a useful next action. Hover
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
- Loading copy fits its actual wrapped height and never delays world generation.
  Initial joins carry the current lesson into a paused World Ready screen until
  the player selects Begin or uses Enter, Space, or Escape.
  A lesson needs eight seconds of rendered exposure to advance loading history; quick
  joins and loading stalls do not count as reading. All lessons remain directly available.
- Movement lessons display the current loaded bindings. Menus must not consume the
  lifetime of a meal recap, and larger recaps need more reading time than a single line.
- Show a native doorway only when its installed target can be opened. Do not substitute
  a raw resource ID, a guessed key binding, or a vague message for a working guide.
- Keep player-authored Trace notes distinct from curated instructions. Their usefulness
  depends on what players leave; the pack supplies readable, contextual presentation.

Cards credit owned remote work at its actual successful outcome, including while the owner is
offline. Setup, inventory possession and proximity do not prove work. No card requires another
human; the World Condenser remains limited by its current dedicated-server lifecycle support.
There is no hourly discovery quota or minimum interval.

All card and loading illustrations exclude humans, humanoids, humanlike spirits, body parts,
silhouettes and mannequins. Use concrete objects, mechanisms, environments, and nonhumanoid
creatures to explain the event.

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

## Death and Esc-menu tips

Threads owns a 192-entry catalogue alongside its loading lessons: 80 survival/recovery tips,
88 broader pack tips, and 24 light late-game teasers. Hints use shared concept IDs and record
mechanical source references. Teasers introduce possibilities without revealing their complete
recipes, unlocking a Thread, or claiming progression credit.

Show separate stable tips on the death screen and Esc menu, sharing one unseen pool. Display every
eligible entry before repeating; exhausted contextual categories yield to other unseen entries.
Prefer reliable cause-specific advice on three of four eligible death selections, and weight
teasers at one in eight general selections while unseen entries remain in both pools. Unknown
damage gets general advice. Downed episodes retain their cause until revival or final death.
Require relevant mods and avoid the last death/Esc tips at cycle boundaries when alternatives exist.

Choose the Esc tip on first use and retain it across menu openings, resizing, reconnecting, and
restarts. Rotate it once per confirmed death, choosing its replacement when Esc next opens.
Respawning does not rotate it again. Record only rendered tips; hidden selections must not consume
entries or prematurely exhaust the pool. Client-local history schema 2 stores shown IDs, cycle,
and the current Esc tip; schema 1 preserves its known recent IDs during migration. Keep this
history independent of loading exposure and lineage progress. Network protocol remains 10.

Keep text concise, neutral, and actionable, with current bindings where needed. Wrap at normal
font size. Pause controls may move upward to fit a tip, with Threads in the top-right corner;
all built-in copy fits a 320×240 GUI. Omit tips from smaller or oversized resource-pack layouts
when space is insufficient, without recording them. Hints never delay respawning or change
normal/hardcore death controls. Resource packs can replace
`assets/better_content_threads/death_hints/catalogue.json`; invalid catalogues log an error and use
built-in general advice without erasing history. Validate selection/persistence locally and inspect
native-client fixtures; pack suites still require an explicit user order.
