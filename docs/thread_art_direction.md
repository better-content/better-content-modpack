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
plus cosmetic item derivatives whose predicate follows the global card order 1–52.
Loading masters are 2:1 landscapes and ship at 512×256. Seven topics organize the reader;
they do not require a whole-image tint or the retired four-suit composition rules.

## Editable source and review

The 52 card scenes and teaching definitions are maintained in
[`authoring/discoveries.json`](../../mod_source/better-content-threads/authoring/discoveries.json).
[`art-grammar.txt`](../../mod_source/better-content-threads/authoring/art-grammar.txt)
contains the shared generation prompt. Exact prompts, original image-generation paths,
reviewed masters, contact sheets, and hashes are retained in
`/home/dev/workspace_artifacts/reviews/better-content-threads/20260912-discovery-redesign/`.

`authoring/prepare_art.py REVIEW_BUNDLE` creates deterministic runtime derivatives and
facsimile model overrides. It does not generate new artwork. Visually inspect every
master for forbidden figures, subject accuracy, text artifacts, and small-scale clarity,
then review the actual compact/wide reader and loading surfaces. Preserve earlier
review bundles as superseded evidence rather than mixing them into the shipped roster.
