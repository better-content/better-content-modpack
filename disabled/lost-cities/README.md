# Lost Cities — disabled, not discarded

Lost Cities is out of the active pack. This authored input tree preserves its pinned
manifest, native profiles/server configuration, Creating Space destination, and In Control
structure exclusions. The manifest ends in `.disabled`, so artifact resolution cannot discover it.
The tree is excluded by `.packwizignore` and is outside `package.sh`'s copied content roots.

`integration-fragments.json` retains the removed Earth-orbit adjacency, crafting-policy namespace,
and original compatibility configuration value. These fragments are not live configuration.
The active Earth-orbit route and In Control rules do not reference the absent dimension.

The Java fixes/mixins and their tests remain unchanged in
`/home/dev/mod_source/better-compat-fixes`; their existing mod-presence/version guards keep
Lost Cities-specific code dormant when the mod is absent. The active pack explicitly sets
`lostCities.serializeDhC2meFeaturePlacement = false`. The optional terrain smoke profile
also remains available for future reuse; it does not create a dimension or travel route.

Re-enabling requires an explicit decision: restore the manifest/config/data to their original
paths and merge the retained integration fragments into their respective live files, then
validate and refresh Packwiz only through the authorized release procedure. Do not copy the
retained In Control fragment over the live file: it contains only Lost Cities rules.

The Flesh That Hates remains installed and unchanged. Removing Lost Cities does not authorize
new TFTH integrations, a Lost Cities upgrade, a replacement ecology, packaging, or deployment.
