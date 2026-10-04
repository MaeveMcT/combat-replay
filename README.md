# Combat Replay

A RuneLite plugin that records client-observed combat state and privately sends recordings to the Combat Replay web viewer.

## Current capture

On each game tick the plugin records all visible players and NPCs in the top-level world view, including position, footprint, orientation, animations, approximate health, interaction target, and death state. For visible players it also records appearance-derived equipment and overhead protection-prayer icons, allowing teammate gear switches and prayer changes to be presented as observed actions. It also records hitsplats, projectiles, tile graphics, actor graphics, interaction changes, NPC transformations, actor and scene-object spawns/despawns, deaths, active local-player prayers, and NPC overhead text. Scene terrain, heights, collision, walls, and objects are captured as generic tile observations and retained as the player moves.

The local player's complete inventory and equipment are captured by slot every tick and whenever either container changes, preserving intra-tick gear switches. Item actions such as Eat, Drink, Wear, and Wield are recorded with the item ID and inventory slot. Current/base Hitpoints and Prayer are captured every tick, with each increase or decrease represented as a resource-change event. Together these observations allow consumable actions to be correlated with the resulting HP or Prayer gain without relying on a static food or potion list.

Format v1 writes the exact display names of every visible player into each private recording, including players who do not use Combat Replay. Names are sensitive replay content: recordings should be shared only with people you trust. When web upload is enabled, the private upload will contain these names; they are not used for public profiles, global search, analytics, or dashboard summaries. Recordings also include the local player's inventory and equipment, nearby actors and scene data, and action attempts. Uploads go to the configured website; its operator can access stored data. The plugin retains local JSON after upload. Owner-local Gauntlet diagnostics are opt-in and never uploaded.

## Using it

1. Open the **Combat Replay** sidebar.
2. Select **Start recording** while logged in.
3. Complete or observe an encounter.
4. Select **Stop and save**.
5. To enable private web uploads, enter the website's HTTPS address under **Web address**, choose **Pair web device**, and enter the pairing code shown by that website. Uploads are off by default. The address may be a hostname or an IPv4/IPv6 address; HTTPS certificate validation still applies to IP addresses. Plain HTTP is allowed only for localhost or loopback IPs (127.0.0.1 and [::1]) for local development.
6. With uploads enabled, view your recordings on the Combat Replay website. The plugin has no local replay viewer.

The web service supports email-verified accounts and provides the replay viewer. Storage allowances and subscription options are shown on the website; web plans never restrict local recording.

If web storage is full, the uploader persists a paused state and retains the local file. Free space on the web or obtain more storage, then select the recording in the library and choose **Upload / retry**. Selecting a recording shows its stored upload status; hover the status for full guidance. A quota pause never retries automatically, including after restart. Revoked devices must be paired again. Upload work is stopped on plugin shutdown.

When paired and upload is enabled, newly saved recordings are gzip-compressed and checksummed away from the client thread, then uploaded without deleting or replacing the local JSON. Transient failures retry with bounded backoff, and upload state is stored by recording UUID under the local `.uploads` directory. The sidebar recording library can rename, delete, copy the recording folder path, and retry uploads of recordings. Renaming only changes the local filename, not the recording's internal or web title; its upload bytes remain unchanged. Avoid putting player names or other secrets in a filename. Recordings receive encounter/result/duration names when those facts can be detected and are stored as format-v1 JSON under RuneLite's `plugin-data/combat-replay` directory. All recording, sidecar, diagnostic, and temporary upload file I/O uses RuneLite's confined `Filepath` API; temporary upload files stay in the recording directory and are deleted after upload attempts. Version 1 includes a stable recording UUID, producer and synchronization metadata, explicit observation capabilities, item-name dictionaries, world/view context, instance mappings, actor/scene operations, local state, and evidence-labelled events. Earlier internal recording formats are intentionally unsupported.

## Limits

This records observations available to the local client, not authoritative server combat state. Hitsplats identify their target but often cannot identify their source. The recorder conservatively emits inferred kill-attribution events when a terminal hitsplat is marked as the local player's damage or has exactly one recently observed projectile source; ambiguous, melee-only, poison/recoil, and stale evidence remains unattributed. Other players' inventory, exact resources, offensive prayers, and actions without visible effects are unavailable. Off-screen and unloaded entities cannot be reconstructed. The web viewer presents observed evidence and generic event correlations; mechanic-aware conclusions still require encounter interpreters.

A manual recording stops and saves automatically after 3,000 game ticks (about 30 minutes) to bound memory use. For longer sessions, start another recording. A future group-content phase may support merging cooperative recordings from multiple consenting clients. Format v1 captures synchronization evidence for that future work, but does not claim a globally authoritative server tick.

## Development

Use Java 21 to run Gradle while targeting Java 11 bytecode:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
./gradlew run
```

For local multi-plugin testing, use the sibling `runelite-plugin-dev-client/` harness. The web address must be configured explicitly before pairing; this project does not bundle or deploy the web service.

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
