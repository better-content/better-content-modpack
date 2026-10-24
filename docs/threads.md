# Threads

Threads answers “what just happened to me?” through 48 discoveries. Each card gives the
experienced event, its cause, and a useful next step. Gameplay does not have separate
reveal and completion stages. Exact recipes and apparatus remain in EMI, Ponder, and
native guides; survival fundamentals remain in the 18 loading/Lessons entries.

The canonical definitions, trigger routes, and art scene specifications live in
[`Learning Surfaces authoring roster`](../../mod_source/better-discovery-guides/authoring/discoveries.json).
All 48 active cards have reviewed Journal copperplate illustrations. Their intended scenes
remain in the authoring roster. The packaged card
catalogue is `bc.better_discovery_guides.cards.v4`; the mod network protocol is 3. The new mod
identity starts fresh without saved-history migration. Cards, lessons, and tips are fixed
packaged catalogues for each release.

## Evidence and attribution

Outcome cards follow committed native events. First-use cards
also use personal points of need: closing an EMI recipe view, approaching a Font,
completed food use, and a successful crafting-table craft. Backpack does not credit straw
possession, table pickup, placement or opening. Axe onboarding counts 600 connected
non-spectator player ticks, pausing during spectator scouting and resuming across reconnects. Accepted movement
triggers bounded approach checks; inventory changes trigger bounded item checks. Providers
publish domain events; Learning Surfaces translates them. Outcome adapters remain pinned to
actual output, movement, or work boundaries. Another player's activity cannot stand
in for personal evidence.

Owned operations earn credit at success even while their owner is away or offline.
Persisted job, device, crop, route, or native operator identity supplies attribution.
Offline discoveries and their notices persist across reloads and deliver on reconnect.
No nearest-player fallback exists. Each card counts once per generation; repeated
signals and repeated attempts cannot inflate the score.

There is no hourly quota, quiet-period requirement, or discovery backlog scheduler.
The catalogue shapes pacing by choosing meaningful outcomes. Legitimate bursts remain
possible. All cards can be earned without another human. The World Condenser successor
card remains; integrated single-player Condenser lifecycle support is a separate future
work item, and current dedicated-server support does not require a second player.

## Reader and lineage

The reader shows discovered cards only. All plus seven topic filters cover World, Body,
Materials, Industry, Magic, Travel, and Lineage. Unread cards appear first; opening
selects the oldest unread card. Its art and explanation appear immediately. Continue or
Space selects the next unread card across filters. There is no timed advance.

The text beside a card has no separate panel background and follows the menu field-note
style with right-aligned lines. A strip below applicable explanations shows craftable
result items. Hover for the tooltip and use configured EMI recipe or uses controls on an
item to investigate it. The First Hand Axe page shows rock, flint and bone variants of both
primitive axes and butcher knives. Recipe-backed previews resolve native recipe outputs on
the server and preserve material NBT; recipe controls focus the referenced EMI recipe and
uses controls retain native stack lookup. Missing recipes omit previews rather than showing
generic tools. Thread copy does not disclose ingredient counts or layouts.

World Lifecycle Manager preserves the archive across successors. A successor resets
this generation's distinct-discovery score while preserving lifetime unique discoveries,
per-card generation counts, and read artwork. Ordinary death does not reset generation.
Re-encountering a known card may remind the player once in a successor without turning
old artwork unread again. The fallback server SavedData supports offline credit without
relying on death-cloned player NBT.

Native doorways appear only for installed working targets. Body's six-region overview,
lessons and contextual tips explain Death's Door and injury details without an active
Death's Door card. Card copies remain cosmetic and grant no discovery to their recipients.
Removed cards stay in saved history without migration, but are omitted from reader sync
and notices; surviving read/discovery history is unchanged.

## Death's Door and other teaching

Native Body, lessons and contextual tips cover Death's Door, persistent injuries,
trauma, and treatment. Injury type chooses
medicine; region chooses impairment. HP healing, trauma expiry, and injury treatment
remain different systems.

Main-menu tips are stable per application launch. Esc tips use actual conditions and
remain stable while open. Final-death advice uses the committed final cause and shares
measured space with the native injury/treatment recap. Detailed behavior and provenance
are documented in the [source teaching guide](../../mod_source/better-discovery-guides/authoring/teaching-surfaces.md).

## Review

All 48 active card illustrations and 18 loading illustrations follow the
[no-humans art direction](thread_art_direction.md). Compact/wide journal and native death-screen
rendering are distinct presentation surfaces.
