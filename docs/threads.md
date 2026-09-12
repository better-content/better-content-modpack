# Threads

Threads is a collection of 52 illustrated learning cards, unlocked through play rather than assigned objectives. Its four suits each contain thirteen cards: World covers exploration and survival, Works covers machines and crafting, Powers covers stats and magic, and Fragility covers risks and changed game rules.

All 52 cards are live teaching surfaces. Fragility 7–13 explain seven especially important divergences from ordinary Minecraft: sleep advances simulation, hunger differs from nutrition, food carries temperature, player kills release colour-matched commerce spirits, the End is not a vanilla doorway, EMI is the recipe authority, and the body has custom motions.

## Player contract

- Nothing appears merely because the player logged in or returned. Discovery must follow a contextual native action.
- The automatic tease never captures input. An 8×8 code-drawn archive glyph and one localized line form a compact lockup centered at `screenWidth / 2`, with the lockup's visual center at `screenHeight / 3`. The glyph is two logical pixels above the text. No item, block, panel, or card artwork is rendered.
- The line is 72% of the normal GUI font, shrinking no lower than 55% for a long title, and remains one line. It is white with a true-black eight-neighbour outline. Reveal copy is exactly `Card unlocked: %s`; completion copy is `Card completed: %s`. A second, still smaller line shows the current reader binding (`M` by default) while the tease is visible.
- A notice lasts 3.2 seconds: 400 ms fade-in, 2.2 seconds held, and 600 ms fade-out. Twelve deterministic one-pixel wisps use the exact Systemic Salience aspect color; eight dust motes use archive gold `#C6A15B`. They remain tightly bounded around the glyph and drift upward/outward without tint, blur, progress styling, or crosshair coverage.
- The subdued native page/plate rustle plays once at fade-in. There is no synthetic tone. An open screen pauses both time and particles. Notices queue and display one at a time; the collapsed unread marker continues to represent unread plates.
- `M` by default and the pause-menu button open the reader. Every reminder renders the current conflict-aware binding in a physical keycap beside the literal label `Threads`. Its catalogue has four suit tabs and thirteen stable positions per suit; unknown cards remain sealed.
- An unread thumbnail is a dark archive plate with a faint suit edge and leaking aspect trace. Selecting it shows the unexposed plate and `Open this card / Click or press Space to open`; development does not start automatically.
- Development is a single 800 ms linear crossfade from the neutral code-drawn archive plate to the illustration. Existing illustrations retain their authored pixels, and their thumbnails remain derived from the same art. The code-drawn aspect cues use the shipped ore-matched palette. A click or Space during development completes it and is consumed before any doorway or facsimile control.
- Title, rule, action, doorway, and card-copy controls remain hidden until development completes. The detail reader presents a descriptive title, a plain mechanical rule, and a useful next action. There is no prose or invitation layer. Text wraps in a scrollable pane; wheel, Up/Down, and Page Up/Down read the text, while Left/Right change cards. Narrow catalogues use one column, with keyboard selection and an explicit scrolling hint. Only completion marks a plate read; Escape or navigation beforehand leaves it unread.
- Known cards and art persist across the lineage. Active state and current completion reset for each successor generation. Re-encountering a known card in a successor produces a contextual notice without marking its plate unread again. Completion history retains total count, first generation, last generation, and route counts.
- A live card exposes a doorway only when a specific authoritative native surface exists. The client exposes working EMI/Ponder targets and explicitly matched, bound nutrition, RPG, or Trace Sight controls. Other declared Font, guide, power, campaign, and lifecycle targets stay hidden until an actual opener exists. A card such as Ruins may omit the control rather than offer a vague or false destination. Signed facsimiles are freely reissued cosmetic copies; they retain collector and lineage identity, grant nothing, and never unlock a card for their recipient.
- On-character display remains unresolved. Do not ship a placeholder cosmetic render.

## Trigger contract

Every live card has separate reveal and completion routes. A single signal may reveal a card or complete an already-active card, never both. Signals are exact bounded lowercase types and bounded values; route values may use an exact value, `|` alternatives, or `*`. Native Better Content providers publish typed Forge gameplay events and do not depend on Threads. Threads-owned adapters translate those events into `ThreadSignals.emit(ServerPlayer, type, value, correlationToken)` calls. Providers retain ownership of what counts as a real action; adapters preserve the same episode token across reveal and completion.

Pack bridges observe operating boundaries rather than inventory proxies wherever the installed mod exposes them. Create's action-backed criteria cover the deployer, water wheel, pump, train, long travel, and finished Precision Mechanism. Create: Power Grid requires energized terminals plus an actual wire connection, followed by a live consumer. AE2 begins when its pattern-encoding menu is used and completes at the crafting CPU's native job-finished boundary. A Valkyrien Skies episode follows one physical ship until the player makes landfall after 128 blocks. Occultism uses the golden bowl's valid ritual start and successful stop. Relics uses positive native relic experience twice on the same tagged physical stack in distinct contexts. Ars Energistique begins only when positive Source is converted into AE power and completes when the correlated AE2 crafting CPU finishes work. The current pack disables Ars Energistique Source conversion; those existing adapter routes are therefore unavailable and are not presented as build instructions. The tone-only update preserves their route identities and predicates. Unsupported OC2-file and generic Hexerei variants are not advertised as acquisition routes.

World journeys bind realm identity to a complete visit. Entering The Bumblezone reveals
`deep_own_light` / Exploring the Bumblezone; returning from that same journey completes it.
Entering Ratlantis reveals `silence_has_teeth` / Exploring Ratlantis; its correlated return completes
it. The retired generic pollen and logistics identities have no aliases or migration because no
historical player card data exists to preserve.

Fragility is deliberately stricter than a generic milestone list:

| Card | Reveal | Completion |
| --- | --- | --- |
| Downing and Revival | the player enters Downed Player Revival's actual downed state | the active card's player later dies |
| Exploring Ruins | the player physically enters the bounds of a major ruin-like registered structure | the player leaves alive carrying an item identity absent on entry |
| Hostile Faction Combat | two distinct hostile mobs target the player within one 45-second encounter | one of those tracked hostiles damages another |
| Saving Builds as Schematics | a substantial Create schematic is successfully accepted for publication | the correlated publication succeeds |
| Preparing for Pillager Assaults | Pillager Campaigns enters gathering, approaching, or materialized state for the player | that campaign reaches survived, defeated, retreated, or target-dead outcome |
| Resetting the World | an operator commits through a standalone World Condenser Interface | the next lineage generation is verified at successor login |

Fragility 7–13 are active divergence lessons with normal reveal, completion,
correlation, history, art, and facsimile behavior. Their producers remain the
authoritative source of evidence; the Threads catalogue does not simulate the
underlying mechanic.

## Loading and arrival briefs

Top-level world and server joins select one of 16 spoiler-free mental-model
lessons. The first introduces Threads; the others cover the fading HUD,
hydration, nutrition, food variety, metabolism, body temperature, configured
movement, simulated sleep, downing and revival, life stats, seasons, moving
weather, pollution, structural support, and pillager campaigns. Lessons resume
at the next unseen entry, cycle in manifest order, and retain client-local
history without becoming progression gates. A loading lesson is recorded only after eight seconds of rendered exposure; fast joins and stalls do not consume unread lessons.

The selected lesson and its 512×256 illustration render over the connection or
level-loading screen with Previous, Next, and Keep Reading controls. Loading
never imposes mandatory onboarding: the world opens immediately when ready
unless the player explicitly selects Keep Reading. That opt-in carries the exact
page into a voluntary paused World Ready screen; Continue, Enter, Space, or
Escape enters the world. A disconnected attempt does not consume the lesson,
and dimension changes do not start a new loading episode.

The Threads reader exposes a separate Lessons mode containing all 16 lessons
from the start. Opening a lesson there records it in the same client-local
rotation history. A lesson may point to one related Thread and, through that
card's exact doorway, an available native Ponder, EMI, or system surface. Unsupported native targets
are hidden rather than replaced by a resource-ID message. Related Thread details remain sealed until the card is contextually
known; the lesson library never marks cards known, active, read, or complete.

## Definition and delivery contract

Every `bc.threads.v3` definition includes a stable `concept_id`, authoritative
`owner`, nonblank rule of at most 35 words and 240 characters, exact `suit`, `order`, and canonical lowercase
`aspect`: `impact`, `tempo`, `work`, `mobility`, `endurance`, `robustness`,
`renewal`, or `control`. Loading requires exactly 52 active identities and
exactly orders 1–13 in every suit. The clean-break player schema is version 4;
state from earlier experimental schemas is intentionally ignored.

Every `bc.loading_briefs.v3` definition includes a stable lesson ID, concept ID,
authoritative owner, category, bounded headline/body/action copy, 512×256 art,
and an optional related Thread ID. Related lessons reuse the Thread concept ID;
unrelated fundamentals retain their own stable concepts. Existing schema-2
client rotation files remain readable because their stable lesson IDs did not
change.

Bounded packet protocol 9 validates card and concept IDs, title, suit, order,
aspect, resource locations, rule/action bounds, state, history, unique identities,
and list sizes no larger than 52. Unknown cards leak no rule or action copy. Protocol 9 removes prose and invitation fields; matching client/server versions are required. Card identities and player-state schema 4 are unchanged.
Automatic notices contain only notice kind, card ID, bounded title, suit, and
aspect—never a game asset, artwork, prose, or trigger data.

The collection persists per player at the World Lifecycle Manager lineage boundary. Physical facsimiles may be lost with a world and reissued in its successor. The narrative design in `lineage_endgame.md` is separate from player instructions; Threads does not implement those proposed endgame systems.
