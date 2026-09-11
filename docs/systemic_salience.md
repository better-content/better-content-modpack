# Systemic Salience

Systemic Salience is an exact identity language shared by three separate authorities: RPG Stats owns permanent Life development, Diet plus `systemic_salience` own temporary bodily state, and Realistic Ores owns geological deposits. The systems share meaning and presentation but never share state. TConstruct is intentionally outside this identity matrix; its materials keep their native behavior.

## Canonical identity matrix

Every row has exactly one representative in each authority. Aspect words appear in tooltips and supporting cues, not in the ordinary stat, food-group, or deposit names.

| Aspect | Glyph | sRGB | RPG development | Diet identity | Ore identity |
| --- | --- | --- | --- | --- | --- |
| Impact | ✦ | `#FF4055` | Strength | Proteins | Hotstone |
| Tempo | » | `#00A985` | Dexterity | Sugar | Copper Bloom |
| Work | ⚒ | `#F0E2C5` | Aptitude | Grains | Tin Quartz |
| Mobility | ➜ | `#E0B01F` | Agility | Fruits | Brassroot |
| Endurance | ∞ | `#52606A` | Constitution | Fats | Coal Measures |
| Robustness | ◆ | `#AF6A2F` | Fortitude | Vegetables | Ironstone |
| Renewal | ✚ | `#6CCAF0` | Vitality | Dairy | Evaporite Beds |
| Control | ⊕ | `#8E5BB7` | Perception | Alcohol | Black Shale |

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

The eight 18×18 badges are canonical byte-identical assets across RPG Stats, Systemic Salience, and Realistic Ores. Audio is a semantic family rather than a shared file: each aspect keeps the phrase in the table while RPG allocation UI, nutrition and systemic bodily cues, and Realistic Ores discovery use context-specific mixes. Tempo may add an event-specific broken-cadence variant without becoming a ninth identity. Realistic Ores reuses the badge strip while its world identity remains morphology-first dirty geology rather than an aspect-colour wash.

## Teaching surfaces

There is deliberately no explicit comparison screen. Discovery is reinforced through the RPG allocation screen, Diet's native nutrition screen, food and ore tooltips, nutrition activation feedback, ore blocks in the world, and EMI's ordinary tooltip/recipe surfaces. Natural names lead; the aspect badge, glyph, colour, and name appear when the player interacts closely enough to need the formal identity.

RPG point increments provide a quiet preview pulse and motif. A successful server-confirmed allocation produces a short recap. Nutrition threshold crossings and discrete abilities use the corresponding motion and motif, while the meal recap explains which bodily state changed and for approximately how long. Recap labels wrap within the viewport and use bright neutral text beside the aspect badge. Four to ten seconds of display time scale with the number of changed states; menus and a hidden HUD pause that timer. Ore tooltips formally name the identity and preserve Shift for the assay summary; ore discovery never interrupts play with a toast or action-bar message.

## RPG development

RPG allocations use `cap × points / (points + 20)`: twenty committed points reach half the cap, allocation cannot be refunded during the life, and death wipes the ledger. Each visible category is singular and mechanically coherent:

- Strength increases damaging impact and knockback.
- Dexterity increases attack and item-use cadence.
- Aptitude increases productive mining speed and reach.
- Agility increases movement, swimming, and step capability.
- Constitution conserves hunger, thirst, and stamina.
- Fortitude resists temperature and knockback.
- Vitality shortens harmful effects and preserves beneficial effects; it never grants health, healing, or combat recovery.
- Perception improves recoil, dispersion, and spell range.

Slice of Life: Carrot remains the sole owner of maximum-health progression.

## Nutrition

Diet stores six ordinary groups: Proteins, Grains, Fruits, Fats, Vegetables, and Dairy. Sugar and Alcohol are optional meta-food loads, never required for dietary completeness. Ordinary thresholds are `50%` supported, `75%` prepared, and `90%` feast; the upper range decays quickly so feast states are deliberate preparation rather than permanent upkeep.

- Proteins culminate in a telegraphed Heavy Blow after briefly withholding attacks.
- Grains build Work Rhythm through consecutive correct-tool blocks.
- Fruits reward sustained sprinting with a stronger Stride.
- Fats conserve bodily resources and can fund one emergency reserve.
- Vegetables resist environmental drift and periodically brace one extreme exposure or knockback.
- Dairy accelerates harmful-effect clearance, preserves beneficial effects through milk, and periodically cleanses one harmful effect.
- Sugar accelerates attack/use cadence while sharply increasing nutrient expenditure and later metabolic debt.
- Alcohol has a narrow moderate-composure window; high load degrades handling and can cause stumbles. Maximum alcohol is unambiguously bad.

Diet's native screen remains the authoritative nutrition display. Generic Diet-wide bonuses are disabled so the discrete behavior is the lesson.

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
