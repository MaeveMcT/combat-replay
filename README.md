# Combat Replay

A RuneLite plugin that records client-observed combat state and replays it in a resizable, tick-accurate 2D viewer.

## Current capture

On each game tick the plugin records all visible players and NPCs in the top-level world view, including position, footprint, orientation, animations, approximate health, interaction target, and death state. For visible players it also records appearance-derived equipment and overhead protection-prayer icons, allowing teammate gear switches and prayer changes to be presented as observed actions. It also records hitsplats, projectiles, tile graphics, actor graphics, interaction changes, NPC transformations, actor and scene-object spawns/despawns, deaths, active local-player prayers, and NPC overhead text. Scene terrain, heights, collision, walls, and objects are captured as generic tile observations and retained as the player moves.

The local player's complete inventory and equipment are captured by slot every tick and whenever either container changes, preserving intra-tick gear switches. Item actions such as Eat, Drink, Wear, and Wield are recorded with the item ID and inventory slot. Current/base Hitpoints and Prayer are captured every tick, with each increase or decrease represented as a resource-change event. Together these observations allow consumable actions to be correlated with the resulting HP or Prayer gain without relying on a static food or potion list.

Player names are never written to recordings. Other players receive recording-local aliases.

## Using it

1. Open the **Combat Replay** sidebar.
2. Select **Start recording** while logged in.
3. Complete or observe an encounter.
4. Select **Stop and save**.
5. Select the saved recording and choose **Open** (or double-click it) to launch the expanded viewer.
6. Click actors to inspect them, drag/wheel the map to pan/zoom, and use the timeline or tick controls for playback.

The sidebar recording library can open, rename, delete, and reveal recordings. Recordings receive encounter/result/duration names when those facts can be detected and are stored as JSON under RuneLite's `combat-replay` directory. The current format stores compact per-tick deltas, a recording-level item-name dictionary, and packed scene observations; replays are reconstructed into complete tick states when opened.

## Limits

This records observations available to the local client, not authoritative server combat state. Hitsplats identify their target but often cannot identify their source; melee attribution therefore requires later encounter-specific interpretation. Other players' inventory, exact resources, offensive prayers, and actions without visible effects are unavailable. Off-screen and unloaded entities cannot be reconstructed. The viewer presents observed evidence and generic event correlations; mechanic-aware conclusions still require encounter interpreters.

A future group-content phase should support merging cooperative recordings from multiple consenting clients. That would provide each participant's local inventory, resources, prayers, and item actions while retaining recording-local pseudonyms.

## Development

Use Java 21 to run Gradle while targeting Java 11 bytecode:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
./gradlew run
```
