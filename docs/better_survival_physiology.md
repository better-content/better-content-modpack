# Systemic Salience

Systemic Salience is an exact identity language shared by three separate authorities: RPG Stats owns permanent Life development, Diet plus `better_survival_physiology` own temporary bodily state, and Realistic Ores owns geological deposits. The systems share meaning and presentation but never share state. TConstruct is intentionally outside this identity matrix; its materials keep their native behavior.

## Canonical identity matrix

Every row has exactly one representative in each authority. Formal aspect labels appear in tooltips and supporting cues; ordinary stat, food-group, and deposit names describe their practical identity. Endurance is also the natural capability name for conserving bodily reserves.

| Aspect | Glyph | sRGB | RPG development | Diet identity | Ore identity |
| --- | --- | --- | --- | --- | --- |
| Impact | ✦ | `#FF4055` | Strength | Sinew (Proteins) | Hotstone |
| Tempo | » | `#00A985` | Hand Speed | Nectar (Sugar) | Copper Bloom |
| Work | ⚒ | `#F0E2C5` | Tool Handling | Grain (Grains) | Tin Quartz |
| Mobility | ➜ | `#E0B01F` | Athletics | Berry (Fruits) | Brassroot |
| Endurance | ∞ | `#52606A` | Endurance | Tallow (Fats) | Coal Measures |
| Robustness | ◆ | `#AF6A2F` | Hardiness | Root (Vegetables) | Ironstone |
| Renewal | ✚ | `#6CCAF0` | Preservation | Cream (Dairy) | Evaporite Beds |
| Control | ⊕ | `#8E5BB7` | Marksmanship | Draught (Alcohol) | Black Shale |

The shipped ore identity palette is authoritative and is shared by the RPG, bodily-state,
and Threads aspect definitions. Glyph, name, value structure, morphology, and actual
behavior remain mandatory parallel cues; hue is never authoritative alone. Ore morphology
and existing artwork are preserved rather than recolored to a separate documentation palette.

## Sensory grammar

Every identity has one recurring motion and sound phrase. These cues play only at meaningful events—allocation previews and commits, nutrient threshold changes, and discrete nutrition activations—not on every attack, step, or mined block.

| Aspect | Motion | Sound motif | Geological expression |
| --- | --- | --- | --- |
| Impact | radial burst | low thud and transient | radiating Hotstone |
| Tempo | two rapid pulses | paired clicks | clustered Copper Bloom |
| Work | descending, settling sparks | tool strike and knock | Tin Quartz ribbons |
| Mobility | directional rising sweep | short whoosh | branching Brassroot |
| Endurance | slow expanding ring | sustained low tone | continuous Coal Measures seam |
| Robustness | inward brace | shield-like clack | dense Ironstone bands |
| Renewal | rising spiral and bloom | ascending chimes | Evaporite crystal bloom |
| Control | converging points | ticks resolving to a ping | Black Shale convergence |

Systemic Salience and Realistic Ores share canonical byte-identical 18×18 aspect badges. RPG Stats uses distinct 18×18 body-part badges in the same palette and keeps the matching aspect cues. Audio is a semantic family rather than a shared file: each aspect keeps the phrase in the table while RPG allocation UI, nutrition and systemic bodily cues, and Realistic Ores discovery use context-specific mixes. Tempo may add an event-specific broken-cadence variant without becoming a ninth identity. Realistic Ores reuses the badge strip while its world identity remains morphology-first dirty geology rather than an aspect-colour wash.

## Teaching surfaces

There is deliberately no explicit comparison screen. Discovery is reinforced through the RPG allocation screen, Diet's native nutrition screen, food and ore tooltips, nutrition activation feedback, ore blocks in the world, and EMI's ordinary tooltip/recipe surfaces. Natural names lead; the aspect badge, glyph, colour, and name appear when the player interacts closely enough to need the formal identity.

The full-screen RPG allocation view is a learning space: all eight capabilities show a concise purpose, every active effect, the Life-point bonus and the next-point gain without requiring hover. Color identifies cards and badges; readable neutral effect text and brighter capability headings preserve contrast. Draft controls, the remaining-point budget and the irreversible commit rule stay visible. RPG point increments provide a quiet preview pulse and motif. A successful server-confirmed allocation produces a short recap. Nutrition threshold crossings and discrete abilities use the corresponding motion and motif. A graphical strip above the hotbar shows current nutrient and Nectar/Booze percentages with aspect badges, wraps on narrow windows, and is hidden while a screen is open or the HUD is hidden. There is no timed text meal recap or meal-duration estimate. Ore tooltips formally name the identity and preserve Shift for the assay summary; ore discovery never interrupts play with a toast or action-bar message.

## Pulse pacing

For systems whose salience fades under constant trickle, use **accumulate → discharge → recover**. Accumulation should have a meaningful input and a visible limit; discharge should be a discrete event with enough warning and bounded output to read; recovery should provide a real interval before the same pressure can build again. Preserve owner-specific clocks and consequences instead of forcing one shared timer onto every system.

| Owner surface | Accumulation and discharge | Recovery or quiet interval |
| --- | --- | --- |
| Depth Director | Eligible underground time builds encounter pressure. Each ecology has a warning, bounded surge and packet budget. | Ecology profiles specify a recovery phase; pressure also decays while on the surface. New injury relief is a separate one-time signal, not an encounter reset. |
| Pillager Campaigns | Eligible play schedules scouts or three-wave assaults; new interception additionally requires personal regional familiarity (30 minutes accumulated living Survival presence, strongest within 128 blocks and fading to zero at 256). Assault arrival receives a two-minute surface warning and spawn work is capped per tick and second. | Familiarity persists across visits and restart, without offline accumulation. Approaching encounters wait in unfamiliar territory; already-materialized enemies may pursue normally. Resolving a campaign schedules the next eligible window. A stalled wave has a finite progress timeout. Do not add a second encounter in the same track while an invasion remains active. |
| Spirit economy | Credited kills release physical Better Content spirits at the dying mob immediately. | The spirits home to their credited player through Malum's ordinary collection behavior; there is no release cadence or reward ledger. |
| Injury-pressure relief | A newly observed maim creates a temporary director-relief signal. | The saved signal decays each second on the configured curve (180 seconds by default), whether the injury remains or is treated; encounter budget and target state remain intact. |

These owners already implement meaningful pauses at their own scopes. Keep ordinary hunger, hydration, temperature, machine throughput and other continuous simulations continuous; report discrete threshold changes or milestones without turning every tick into a pulse. Do not add periodic drops, automatic reset, or an all-systems wave scheduler merely to make the doctrine look uniform.

## RPG development

RPG allocations use `cap × points / (points + 20)`: twenty committed points reach half the cap, allocation cannot be refunded during the life, and death wipes the ledger. Each visible category is singular and mechanically coherent:

- Strength increases knockback and stagger impact, not damage.
- Hand Speed increases attack and item-use cadence.
- Tool Handling increases productive block reach, not mining speed.
- Athletics increases step capability and underwater air capacity, not movement/swimming speed; 20 points extend the base 15-second reserve to 22.5 seconds.
- Endurance increases stamina capacity, not hunger/thirst efficiency or stamina cost reduction.
- Hardiness resists knockback, not temperature.
- Preservation extends beneficial effects and protects them during milk drinking from the first positive investment; it never shortens harmful effects or grants health, healing, or combat recovery.
- Marksmanship narrows arrow spread and gently increases arrow speed and effective range from the start; later guns gain recoil and dispersion control, while Goety spells gain range.

These player-facing capability names retain the existing `arms`, `hands`, `fingers`, `lungs`,
`blood`, `skin`, `liver`, and `eyes` IDs respectively. Body-part badges, palettes, aspect mappings,
and allocation data remain unchanged. Authored effects now complement nutrition's primaries rather than duplicating them; no stat-ID migration is introduced.

Slice of Life: Carrot remains the sole owner of maximum-health progression.

## Nutrition

The eight food groups match the eight aspect identities and colours. Sinew, Grain, Berry,
Tallow, Root and Cream are ordinary nutrition; Nectar and Draught are optional temporary
loads, never required for dietary completeness. Every ordinary primary scales linearly from
zero: at 10% nutrition, receive 10% of the unamplified maximum, with no deficiency penalty.

| Group | Primary at 100%, without Nectar | Secondary unlocks |
| --- | --- | --- |
| Sinew | +40% melee damage | 40% charged strike; 80% cleave |
| Grain | +100% correct-tool mining speed | 40% break sequence; 60% longer grace; 80% work burst |
| Berry | +25% movement/swimming speed | 20% safer falls; 40% sustained-sprint stride; 80% sprinting leap |
| Tallow | −50% hunger/thirst/stamina costs | 80% emergency reserve |
| Root | −70% heat/cold drift | 80% temperature-hit guard |
| Cream | 2× harmful-effect expiry speed | 40% meal recovery; 80% periodic cleanse |

Nectar amplifies these effects by `1 + load` and accelerates consumption. It does not provide
another cadence bonus, alter unlock thresholds or multiply RPG allocations. Tallow and Root
reductions cap at 85% even with Nectar. Draught grants `min(30%, 30% × load × Nectar amplification)`
incoming damage reduction. Its movement, work, cadence and resource-cost penalties start above
30% load and are **also** amplified by Nectar, up to their impairment caps. Blackout starts at
100% raw load and ends at 85%; maximum Draught remains bad.

Secondary abilities have different counts and roles rather than a compulsory four-tier ladder.
Nutrition no longer grants knockback/stagger, step height, beneficial-effect preservation or
Root movement speed. RPG owns those complementary capabilities. Exact formulas, perk timings
and source authority belong to the [physiology balance contract](../../mod_source/better-survival-physiology/README.md#nutrition-balance).
Diet retains native nutrition decay; the additional upper-band drain begins above 80%.

Sugar's exponential consumption multiplier (`8^load`, up to `8×` at full load) applies
uniformly to all eight groups, including sugar itself and alcohol clearance. Each retains its
own baseline decay curve; sugar's configured half-life is the unaccelerated baseline. Sugar
and alcohol time estimates account for sugar falling during the estimate.

Heavy Blow readiness uses the same server-side eligibility predicate as combat. Becoming ready
plays one Impact systemic-salience motif with its radial particle burst and a transient activation
label; remaining ready does not repeat the cue. Login/reconnect establishes a silent baseline.
The existing empowered strike cue remains separate and occurs once at the target. This adds no
permanent readiness HUD widget. Source checks cover ordinary attacks and
Epic Fight's native attack route; full-pack audiovisual acceptance is separate.

Diet remains authoritative for nutrient values. The nutrition strip displays current group/load percentages; the benefit presentation helpers describe continuous primaries and only real perk unlocks. Opening/layout behavior is not changed by this balance work. Food hover consolidates nutrient gains into one horizontal row of graphical aspect badges, pack names and per-serving percentages, without ASCII glyphs or effect descriptions. This replaces Diet's default-name list and “When eaten” separator. Quark's duplicate food icons and saturation description are disabled; the bottom native restoration displays, eaten status and source-mod attribution remain. Freshness labels are capitalized and colored green (Fresh), yellow (Stale), gold (Spoiled) and red (Rotten). There is no text meal recap. Generic Diet-wide bonuses stay disabled so they cannot duplicate the aspect abilities.

## Ores

The complete player-facing roster is eight deposits plus the unrelated Oil Seep surface feature. Each deposit uses its aspect palette as an accent, a unique geological morphology, an immediate promise, and deeper assay routes:

- Hotstone: dangerous heat and heavy power.
- Copper Bloom: responsive copper mechanisms.
- Tin Quartz: productive toolmaking, quartz, and later gem-bearing assays.
- Brassroot: zinc/brass for moving machinery.
- Coal Measures: stored fuel for sustained work.
- Ironstone: durable iron and nickel-bearing depth.
- Evaporite Beds: salt, preservation, and fertile chemistry.
- Black Shale: redstone control, soul contamination, and precious traces.

Complex constituent recovery remains processing depth. Removed worldgen identities have no compatibility aliases or migrations.

## Acceptance rule

For every representative, test whether a player can predict its broad use from name, morphology, tooltip, and immediate behavior before reading exact numbers. A cue fails if it needs another row's identity to explain it, or if it merely exposes a weighted profile. New mechanics may project from one categorical identity, but new player-facing representatives must preserve the one-to-one matrix.
