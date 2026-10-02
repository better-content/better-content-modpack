# Learning surfaces

Better Content teaches through the systems the player encounters, not an achievement
ledger or objective graph. FTB Quests and its custom integration are not active pack
content. Their tasks and rewards are not transferred into Learning Surfaces or another system. The `better_discovery_guides` mod owns cards, lessons, and contextual tips; Threads remains the card reader and card vocabulary.

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
Learning Surfaces has 53 live cards across seven topics; the Lessons reference and loading
rotation share 18 lessons. The main-menu, Esc, and death contexts share 199 authored tips. Follow [Threads](threads.md) for the single-explanation schema,
personal trigger evidence, native doorways, and lineage persistence. Domain mods
own their gameplay events; Learning Surfaces adapters translate those events into card signals.

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
offline. First-use cards use explicit acquisition, completed use, screen-close, and approach
cues at the useful moment. Item checks run after relevant inventory changes; approach checks
run after accepted movement. Timed onboarding uses a due-time queue. These cues do not infer
machine operation from possession or proximity. No card requires another human; the World
Condenser remains limited by its current dedicated-server lifecycle support. There is no
hourly discovery quota or minimum interval.

Server and provider events own gameplay outcomes, ownership, lineage history, and rewards.
A bounded client report may credit only personal, non-reward teaching that the server
cannot observe directly, such as closing an EMI recipe. The server checks the report's
allowed action and target; it never treats that report as proof of an owned machine result.
The mod's three catalogues are packaged and fixed for a release. Resource packs may
change artwork but cannot reload copy or trigger rules while running.

All card and loading illustrations exclude humans, humanoids, humanlike spirits, body parts,
silhouettes and mannequins. Use concrete objects, mechanisms, environments, and nonhumanoid
creatures to explain the event. All 53 card illustrations and 18 loading/Lessons
illustrations use reviewed Journal copperplate art; the card copy and triggers are active.

## Review and validation

- Trace every changed claim and trigger to its implementation; inspect related surfaces
  for contradictory copy or duplicated authority.
- Check stable concepts, current native doorways, bounded copy, and relevant existing
  repository-local tests. Follow [Thread Art Direction](thread_art_direction.md) for art.
- Review reader layout, loading presentation, and development crossfades manually when
  changed. Automated tests must not synthesize mouse movement or clicks.
- Report changed player-visible behavior and verification. Run pack suites only when
  explicitly ordered through `test.main.kts`; otherwise state that pack testing was
  intentionally omitted under the Dev/Dist/Debug testing policy.

Do not recreate a quest compiler, layout harness, atlas exporter, or parallel validation
framework. Durable guidance belongs here; raw evidence belongs outside living docs.

## Main-menu, Esc-menu, and death tips

The fixed 199-tip catalogue declares each tip's eligible surface, requirements, relevant
mods, concept, and mechanical source. The main menu chooses one stable preparation or
possibility tip per application launch. Esc selects practical advice from current
server context when opened and keeps it still while the screen is open. A stale context
falls back to general advice. Death uses the committed final cause, including the
injury provider's final-death event when present, and stays fixed until respawn or
logout. Contextual relevance takes priority over unseen novelty.

Each surface keeps its own client-local exposure history. A tip counts as seen only
when its copy is actually rendered. Hidden or clipped advice does not consume history.
The native injury recap owns death controls and space; Learning Surfaces uses its measured
remaining area and omits a tip if the full copy will not fit. Tips never delay respawn
or alter normal and hardcore death controls. The packaged catalogue is fixed for a
release; invalid packaged data fails validation rather than silently swapping copy.
