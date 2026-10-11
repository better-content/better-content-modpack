# Thread art direction

Cards illustrate a concrete cause and its visible consequence. The image supports the
explanation of what happened; a generic achievement emblem or unexplained fantasy scene
is insufficient. Every card and design may change when its teaching improves.

## No humans

All card and loading/Lessons art excludes humans, players, villagers, humanoid mobs,
humanlike spirits, humanoid silhouettes, mannequins, body parts, and distant background
figures. Nonhumanoid animals and creatures are permitted when relevant. Injury concepts
use damaged materials, medicine, impact, or shelter; they do not require anatomy.

## Shared visual language

Use copperplate or etched archive illustrations with precise lines, deep ink shadows,
parchment highlights, restrained mineral pigments, and a strong physical focal point.
Keep mechanical relationships legible at runtime size. Avoid letters, words, numerals,
frames, UI, logos, and watermarks. Code owns framing and typography. Do not impose
unrelated occult symbols, forced aspect links, or recurring mystery motifs.

Card masters are 2:3 portraits. Ship 256×384 full-color images and grayscale thumbnails,
plus cosmetic item derivatives whose predicate follows the global card order 1–53.
Loading masters are 2:1 landscapes and ship at 512×256. Seven topics organize the reader;
they do not require a whole-image tint or the retired four-suit composition rules.

## Editable source and review

The 52 active card scenes and teaching definitions are maintained in
[`authoring/discoveries.json`](../../mod_source/better-discovery-guides/authoring/discoveries.json).
[`art-grammar.txt`](../../mod_source/better-discovery-guides/authoring/art-grammar.txt)
contains the shared generation prompt. Current authored card masters live in
[`authoring/masters/`](../../mod_source/better-discovery-guides/authoring/masters/),
and loading masters in [`authoring/lessons/`](../../mod_source/better-discovery-guides/authoring/lessons/).
These are source inputs, not historical review captures.

`authoring/prepare_art.py` uses those canonical inputs to create deterministic runtime derivatives
and facsimile model overrides; an optional active-task `REVIEW_BUNDLE` can override them during
review. It does not generate new artwork. Visually inspect every master for forbidden figures,
subject accuracy, text artifacts and small-scale clarity, then inspect actual compact/wide reader
and loading surfaces when runtime review is explicitly authorized. Contact sheets, original
imagegen/session outputs and earlier review bundles are disposed of at handoff under
[generated-data.md](policies/generated-data.md), not archived as superseded evidence.
