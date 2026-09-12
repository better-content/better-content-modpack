# Threads

Threads answers “what just happened to me?” through 52 discoveries. Each card gives the
experienced event, its cause, and a useful next step. Gameplay does not have separate
reveal and completion stages. Exact recipes and apparatus remain in EMI, Ponder, and
native guides; survival fundamentals remain in the 17 loading/Lessons entries.

The canonical definitions, trigger routes, and art scene specifications live in
[`better-content-threads/authoring/discoveries.json`](../../mod_source/better-content-threads/authoring/discoveries.json).
The runtime catalogue is `bc.threads.v4`; network protocol is 11. Old experimental card
state and identities have no migration requirement.

## Evidence and attribution

Every discovery follows a committed native outcome. Providers publish domain events;
Threads translates them. Third-party adapters are version-pinned to actual output,
movement, or work boundaries. Setup, recipe previews, inventory acquisition, nearby
machines, and another player's work cannot stand in for personal evidence.

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
selects and automatically develops the oldest unread card. Continue or Space after
reading selects and develops the next unread card across filters. A press that finishes
the illustration animation cannot also skip the explanation. There is no timed advance.

World Lifecycle Manager preserves the archive across successors. A successor resets
this generation's distinct-discovery score while preserving lifetime unique discoveries,
per-card generation counts, and read artwork. Ordinary death does not reset generation.
Re-encountering a known card may remind the player once in a successor without turning
old artwork unread again. The fallback server SavedData supports offline credit without
relying on death-cloned player NBT.

Native doorways appear only for installed working targets. The four injury cards lead
to Body's six-region overview. Card copies remain cosmetic and grant no discovery to
their recipients.

## Death's Door and other teaching

The injury discoveries separately explain entering Death's Door, recovering HP while
injuries persist, later trauma amplifying pre-existing functional injuries, and finished
self-treatment. Initial maims, head-only risk, saturated penalties, and another player's
treatment cannot masquerade as those outcomes. Injury type chooses medicine; region
chooses impairment. HP healing, trauma expiry, and injury treatment are different systems.

Main-menu tips are stable per application launch. Esc tips use actual conditions and
remain stable while open. Final-death advice uses the committed final cause and shares
measured space with the native injury/treatment recap. Detailed behavior and provenance
are documented in the [source teaching guide](../../mod_source/better-content-threads/authoring/teaching-surfaces.md).

## Review

All 52 card illustrations and 17 loading illustrations follow the
[no-humans art direction](thread_art_direction.md). The source repository's required
local gate is `./gradlew verifyFull stageRuntimeJar`; isolated real-client fixtures
review compact/wide journal and native death-screen rendering. Provider repositories
verify their own outcomes. These checks do not imply pack deployment or full-pack tests.
