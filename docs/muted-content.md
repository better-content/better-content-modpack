# Muted content

## Purpose and authority

Muted content fits Better Content and remains installed, but one or more of its normal
acquisition channels are deliberately limited. Muting is not rejection, removal from the
registry, or a promise of a future unlock. Each entry specifies its own access boundary;
muting does not automatically mean hiding items, deleting saves, or disabling existing uses.

This document owns the muted-content classification and current rationale. Authored source
and pinned upstream data establish implementation; the [balance register](balance_policy.md)
summarizes departures. Acquisition-channel policy remains in [crafting policy](crafting_policy.md).
Add entries only for decided restrictions, not speculative backlog or audit dumps.

## Current register

| Content | Restricted acquisition | Retained access and uses | Rationale and owner |
|---|---|---|---|
| Starcatcher End-only catches: `starcatcher:charfish`, `starcatcher:chorus_crab`, `starcatcher:end_glow`, `starcatcher:voidbiter` | Normal survival acquisition is closed: fishing retains the upstream End-only world restrictions, rather than relocating catches into reachable waterways | Existing items, filled buckets and placed fish, their normal uses, and creative/command access remain; no explicit guide/viewer hiding | These species fit the pack, but their native destination is inaccessible under the existing closed-End policy. Pack Starcatcher data owns the restriction; no replacement habitat or exceptional survival route is introduced |

## Starcatcher implementation boundary

The baseline is Starcatcher 2.2.1 Forge 1.20.1, pinned by
[`mods/starcatcher.pw.toml`](../mods/starcatcher.pw.toml). Its native
`data/starcatcher/starcatcher/fish/` definitions identify the four catches above as exclusively
End-bound. There are no additional biome-only End catches in that pinned definition set.
A future pin change must repeat this classification, not assume the list is timeless.

The pack overrides for [End Glow](../kubejs/data/starcatcher/starcatcher/fish/end_glow.json),
[Voidbiter](../kubejs/data/starcatcher/starcatcher/fish/voidbiter.json), and
[Chorus Crab](../kubejs/data/starcatcher/starcatcher/fish/chorus_crab.json) retain the corresponding
upstream `world_restrictions`. Voidbiter additionally requires the `minecraft:is_end` biome
tag and excludes the central `minecraft:the_end` biome. Charfish already inherits its native
End-only definition and needs no redundant override. Bait, difficulty, rewards, registry
identities, item tags and guide-entry flags are unchanged.

Native `FishProperties.getChance` checks the dimension allowlist before applying ordinary bait
bonuses. `FishingBobEntity` uses that weight for candidate selection. The native developer worm
bypasses these restrictions; it has no recipe or loot producer in the pinned Starcatcher data
and is not a new survival route. Creative/debug access is intentionally retained. Native guide
area filtering also uses habitat predicates: unchanged guide flags do not promise an unchanged
area-specific listing.

Inspection of the pinned Starcatcher recipes/loot and direct species references in pack content
found no alternate producer needing a new cut. Item-tag membership, nutrition eligibility and
recipes consuming existing fish are not acquisition producers and remain intact. This is not a
universal runtime audit of every mod's generic hooks. End access is an existing policy dependency,
not independently re-proven by the fishing change; see the [balance register](balance_policy.md).

## Verification boundary

`StarcatcherMutedContentContractTest` inspects the hash-pinned native archive, exhaustively
classifies its End selectors, compares effective restrictions and preserved authored definitions,
and checks the native selector call path. Run it through the Minecraft-free Dev gate described
in [testing](testing.md). Source checks do not demonstrate save compatibility or a live catch.

Separately authorized, exact-candidate runtime acceptance must inspect eligibility in Overworld
water and other reachable fishing destinations, confirm ordinary catches still qualify, and
exercise loading/use of an existing fish item, filled bucket and placed fish plus creative/command
access. A finite streak without a catch is not proof of exclusion. No worlds, saved fish or
retained evidence are deleted by this policy; [retention](policies/generated-data.md) still applies.
