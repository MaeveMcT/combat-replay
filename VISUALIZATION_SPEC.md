# Combat Replay Visualization Specification

> Archived design notes for the former desktop viewer. The plugin no longer includes a viewer; visualization now belongs in `combat-replay-web/`. Do not use the desktop UI instructions below as current plugin behavior.


# NEW PRIORITIES- REMOVE THIS SECTION WHEN DONE
some sort of healthbar visualisation for actors that recently took damage
can we get player names associated with the player actors rather than numbering them?
for collaborative replays, we'd need to sync somehow. we could use the current server tick as a synchronizsation point


## Status

Agreed product direction for the first major visualization pass. This document describes the target experience, not the current implementation.

## Goals, in priority order

1. Explain whether encounter mechanics were handled correctly.
2. Explain why a player died.
3. Analyze individual player performance.
4. Analyze team coordination.
5. Provide a cinematic overview of the encounter.

## Product principles

- Record generic client observations independently from their interpretation.
- Build one generic renderer rather than custom UI for individual bosses.
- Derive maps from RuneScape scene data wherever possible.
- Allow encounter interpreters to translate known IDs and event combinations into mechanic labels without changing the renderer.
- Clearly distinguish observed facts from inferred conclusions.
- Keep recording controls and the recording library inside RuneLite, but open replays in a larger resizable desktop window.

## Encounter interpretation

Use a hybrid model:

1. The recorder stores generic observations such as actor snapshots, animations, projectiles, graphics, hitsplats, objects, inventory changes, and resource changes.
2. Optional encounter interpreters recognize combinations of those observations.
3. Interpreters produce generic mechanic events with human-readable labels.
4. The map, timeline, event feed, and actor inspector render those mechanic events using shared components.

Giant Mole is the first encounter used to validate the system. The Giant Mole interpreter should eventually identify burrows and combat/chase periods, but there must be no Giant Mole-specific replay UI.

For Giant Mole, the replay should make all of the following visible:

- Initial location and every burrow destination
- Time spent fighting versus chasing
- Player movement through the lair
- Mole targeting and attacks
- Damage dealt by each side
- Protection and offensive prayers
- Prayer-point changes
- Gear switches and attack-style changes
- Food and potion usage with resulting resource gains
- Idle ticks
- Kill duration and location breakdown

## Application layout

Use a hybrid RuneLite/desktop presentation.

### RuneLite sidebar

The sidebar is responsible for:

- Starting and stopping recordings manually
- Showing recording status
- Listing recent and saved recordings
- Opening, renaming, deleting, and revealing recording files
- Launching the expanded replay viewer

### Expanded replay viewer

The replay opens in a resizable desktop window with this general layout:

```text
+-------------------------------------+------------------+
|                                     | Selected actor   |
|             Replay map              | Current gear     |
|                                     | Inventory        |
|                                     | HP / Prayer      |
|                                     | Active prayers   |
|                                     | Recent events    |
+-------------------------------------+------------------+
| Controls       Timeline with event markers             |
+--------------------------------------------------------+
```

The map is the dominant element. The actor inspector contains both current state and a recent event feed.

## Generic map

### Data sources

Construct the map generically from recorded RuneScape data where available:

- Scene tiles and planes
- World and scene coordinates
- Tile heights and settings
- Collision flags
- Walls
- Game, ground, wall, and decorative objects
- Instanced-region mappings
- Terrain/minimap colours or imagery when RuneLite exposes a stable source

Never require a hand-authored boss arena map.

### Layers

Use minimap-style terrain as the default base. Provide independently configurable layers for:

- Terrain
- Walls and scene objects
- Collision
- Tile grid
- Height information
- Movement trails
- Interaction lines
- Projectiles
- Ground hazards and graphics
- Labels

An abstract navigation grid remains the fallback when richer scene information is unavailable.

### Coordinates and orientation

- Start with a north-up map.
- Support free pan and zoom.
- Stitch observed scene sections together using RuneScape world tiles.
- Preserve previously observed terrain after the player leaves it.
- Leave unobserved areas dark rather than guessing their contents.
- Keep instanced-region coordinates distinct so unrelated rooms do not overlap.
- Display a compass and optional tile-coordinate readout.
- Freely rotatable maps are deferred.

### Camera modes

Support:

- Follow action: frame the selected player, current target, nearby projectiles, and hazards. This is the default.
- Whole encounter: frame the complete visited encounter area.
- Manual: user-controlled pan and zoom.

Automatic cinematic zoom transitions are deferred.

## Actor filtering

Record all visible actors, but default the viewer strongly toward an encounter-focused set:

- Actors participating in combat
- Actors interacting with participants
- Actors receiving or causing observed damage
- Relevant spawned NPCs
- Relevant projectiles, graphics, hazards, and objects

Provide filters to show nearby actors or every recorded actor. The renderer must not discard hidden actors from the recording.

## Actor rendering and selection

- Represent actors as labelled dots.
- Distinguish players, NPCs, selected actors, dead actors, and encounter mechanics by colour and styling.
- Use transformed NPC names where available.
- Clicking an actor selects and follows it.
- Selection highlights the actor's movement path and interaction target.
- The inspector displays current equipment, inventory, HP, Prayer, active prayers, recent damage, attacks, item actions, and gear switches.

Player identities are configurable and anonymised by default. Recording-local aliases must remain stable throughout a replay.

## Playback

Use strict tick-based playback initially:

- One authoritative state per game tick
- No interpolated movement
- Play and pause
- Previous and next tick
- Playback speed controls may include 0.25x, 0.5x, 1x, 2x, and 4x while retaining discrete tick transitions
- Optional tick-grid overlay

Smooth interpolation is deferred.

## Movement paths

- Show the previous eight ticks by default.
- Provide toggles for the complete past path and future path.
- Show direction arrows.
- Show tick-number markers while paused.
- Represent stationary/idle ticks without drawing duplicate path points, for example with a growing ring.
- Render teleports and boss burrows as dashed jumps rather than continuous walking paths.

## Timeline

Start with one simple scrubber containing coloured event markers. An expandable multi-lane timeline is deferred.

Marker categories should include:

- Mechanics
- Damage and healing
- Deaths
- Notable attacks
- Prayer changes
- Gear switches
- Consumables
- Pickups and drops
- Status applications/removals

Clicking a marker should:

1. Pause playback.
2. Move to the relevant tick.
3. Focus/select the relevant actor or location.
4. Show the underlying observed and inferred evidence.

## Combat events

### Damage and healing

- Show damage beside the affected actor for one game tick.
- Use red for damage, green for healing, and blue for Prayer restoration.
- Stack multiple changes occurring on the same tick.
- Pulse a source-to-target line when the source is known.
- Display unknown-source damage without inventing a source.
- Make indicators selectable to inspect evidence.
- Provide an optional cumulative damage/healing display.
- Render zero-damage hits subtly but keep them selectable.

### Attacks

Ordinary attacks should animate on the map and appear in the selected actor's event feed. Do not mark every ordinary attack on the default timeline.

Notable attack markers include:

- Special attacks
- Attack-style or weapon changes
- First attacks after idle periods
- Encounter mechanic attacks
- Attacks immediately preceding a death
- Missed attack opportunities when confidently detectable

Provide a toggle to show every attack marker.

### Gear switches

Group all equipment changes within one game tick into one gear-switch event.

Represent a switch using all three forms:

- Timeline marker
- Brief map indicator such as `4-slot gear switch`
- Equipment inspector updates with changed slots flashing

The detail view should show before-and-after items for every changed slot.

### Inventory changes

Show timeline/feed events for:

- Consumables
- Equipment changes
- Ammo or rune quantity changes
- Depleted items
- Potion dose or charge changes
- Pickups
- Drops
- Received items

Hide pure slot rearrangement from the main timeline, while retaining it in raw recording data.

### Consumables and resources

Correlate, without conflating, these observations:

- Item action
- Inventory change
- HP or Prayer change

A click is an attempted action, not proof of successful consumption. Display the resulting HP or Prayer gain when observed. Avoid requiring a static food or potion catalogue for basic tracking.

### Prayers

Track and display both protection and offensive prayers:

- Active prayer icons beside/above actors
- Active prayer list in the inspector
- Activation and deactivation markers
- Brief map indicators for changed prayers
- Prayer-point drain and restoration as separate resource events

Do not label prayer usage as correct or incorrect unless an encounter interpreter has sufficient evidence.

### Status effects

Use configurable actor icons and timeline markers for important combat statuses, including:

- Poison and venom
- Frozen or bound
- Stunned
- Teleblocked
- Vengeance
- Thralls
- Stat boosts and drains
- Encounter-specific effects

When an effect cannot be identified, show a generic effect with the raw animation or graphic ID.

### Death analysis

Death markers are selectable, but death analysis must not open automatically.

When explicitly opened, death analysis should show:

- The actor's final ten ticks of movement
- Incoming damage by tick
- HP and Prayer state
- Active prayers
- Gear and consumables
- Nearby projectiles, hazards, and interacting actors
- The final damage sequence on the timeline

Label conclusions by confidence:

- Observed
- Strongly inferred
- Unknown/unavailable

## Recording lifecycle and library

Use manual recording boundaries only for now. One manually started/stopped session equals one replay. Automatic encounter segmentation is deferred.

Generate recording names automatically using encounter, result, duration, and timestamp where available, for example:

```text
Giant Mole — Kill — 1:42 — 2026-09-04 18:37
Unknown encounter — Manual stop — 0:47 — 2026-09-04 18:40
```

Allow users to rename recordings afterward.

Recording library entries should eventually show:

- Name
- Timestamp
- Duration and tick count
- Detected location/regions
- Primary encountered NPC
- Result when detectable
- Damage dealt and taken
- Food used
- Prayer restored
- Gear-switch count
- Recording format and client version
- Optional notes

## First major visualization milestone

Implement these together as the next cohesive visualization pass:

1. Resizable desktop viewer launched from the RuneLite sidebar
2. North-up generic tile map with pan and zoom
3. Scene-derived terrain, walls, objects, and optional collision/grid layers
4. Encounter-focused labelled actor dots
5. Strict tick stepping with play/pause
6. Single scrubber with coloured event markers
7. Clickable actors and combined state/event inspector
8. Damage, healing, and Prayer-restoration indicators
9. Recent movement trails
10. Gear-switch, consumable, pickup, and drop presentation
11. Automatically named saved recordings

## Deferred work

- Expanded multi-lane timeline
- Smooth movement interpolation
- Freely rotatable map
- Automatic camera transitions
- Automatic fight segmentation
- Richer terrain rendering where API support is uncertain
- Advanced attack and damage-source attribution
- Full death-analysis workflow
- Broad status-effect interpretation
- Giant Mole mechanic labels beyond the generic foundation
- Additional encounter interpreters
- Cooperative multi-client recording merge for complete group telemetry

## Current known constraints

- The client exposes observed state, not authoritative server combat state.
- Hitsplats identify their target but often not their source.
- Off-screen and unloaded entities cannot be reconstructed.
- Health values for other actors are commonly ratios rather than exact HP.
- Terrain and object reconstruction depends on stable RuneLite API access.
- Inferences must retain links to their raw evidence and communicate uncertainty.
