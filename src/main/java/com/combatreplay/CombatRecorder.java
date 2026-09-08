package com.combatreplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.VarPlayer;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.kit.KitType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLiteProperties;

@Singleton
final class CombatRecorder
{
	private final Client client;
	private final DefinitionMetadataResolver metadataResolver;
	private final Map<Actor, String> actorKeys = new IdentityHashMap<>();
	private final List<RecordedEvent> pendingEvents = new ArrayList<>();
	private final List<ContainerSnapshot> pendingContainerChanges = new ArrayList<>();
	private final Map<String, String> recordedTiles = new HashMap<>();
	private final Set<String> previousPrayers = new HashSet<>();
	private final KillAttributionTracker killAttribution = new KillAttributionTracker();
	private final ProjectileTracker projectiles = new ProjectileTracker();
	private CombatRecording recording;
	private int nextActorId;
	private int nextEventId;
	private int previousHitpoints = -1;
	private int previousPrayer = -1;
	private long recordingStartedNanos;

	CombatRecorder(Client client)
	{
		this(client, new RuneLiteDefinitionMetadataResolver(client));
	}

	@Inject
	CombatRecorder(Client client, RuneLiteDefinitionMetadataResolver metadataResolver)
	{
		this.client = client;
		this.metadataResolver = metadataResolver;
	}

	void start()
	{
		Package pluginPackage = CombatReplayPlugin.class.getPackage();
		String pluginVersion = pluginPackage == null ? null : pluginPackage.getImplementationVersion();
		recording = new CombatRecording(System.currentTimeMillis(),
			pluginVersion == null ? "development" : pluginVersion,
			RuneLiteProperties.getVersion() == null ? "unknown" : RuneLiteProperties.getVersion(),
			client.getRevision());
		recordingStartedNanos = System.nanoTime();
		actorKeys.clear();
		pendingEvents.clear();
		pendingContainerChanges.clear();
		recordedTiles.clear();
		previousPrayers.clear();
		killAttribution.reset();
		projectiles.reset();
		nextActorId = 1;
		nextEventId = 1;
		previousHitpoints = -1;
		previousPrayer = -1;
	}

	CombatRecording stop()
	{
		if (recording == null)
		{
			return null;
		}
		// Events arrive throughout a game tick and are normally attached by the next
		// GameTick callback. Preserve that final partial-tick evidence when recording
		// is stopped before another callback occurs.
		recording.appendToLastTick(pendingContainerChanges, pendingEvents);
		CombatRecording result = recording.completed(System.currentTimeMillis());
		recording = null;
		pendingEvents.clear();
		pendingContainerChanges.clear();
		killAttribution.reset();
		projectiles.reset();
		return result;
	}

	boolean isRecording()
	{
		return recording != null;
	}

	CombatRecording snapshot()
	{
		return recording == null ? null : recording.snapshot();
	}

	void addEvent(String type, Actor actor, Actor target, int id, int value,
		LocalPoint location, String detail)
	{
		if (!isRecording())
		{
			return;
		}
		RecordedEvent observed = observedEvent(type, keyFor(actor), keyFor(target), id, value,
			location == null ? -1 : location.getSceneX(),
			location == null ? -1 : location.getSceneY(), detail);
		pendingEvents.add(observed);
		killAttribution.observe(observed);
		if ("DEATH".equals(type))
		{
			KillAttributionTracker.Attribution attribution = killAttribution.attribute(observed,
				keyFor(client.getLocalPlayer()));
			if (attribution != null)
			{
				pendingEvents.add(new RecordedEvent("event-" + nextEventId++, "KILL_ATTRIBUTION",
					observed.gameCycle, null, attribution.killerKey, observed.actorKey,
					null, null, null, null, -1, -1, null, null, "inferred",
					attribution.ruleId, attribution.evidenceEventIds));
			}
		}
	}

	void captureProjectile(Projectile projectile, LocalPoint movedTo)
	{
		if (!isRecording() || projectile == null) return;
		WorldView worldView = client.getTopLevelWorldView();
		String viewKey = worldView == null ? null : "view-" + viewIdentity(worldView);
		ProjectileTracker.Observation observation = projectiles.observe(projectile, viewKey, this::keyFor);
		if (!observation.first) return;
		LocalPoint location = movedTo;
		RecordedEvent event = new RecordedEvent("event-" + nextEventId++, "PROJECTILE",
			client.getGameCycle(), null, keyFor(projectile.getSourceActor()),
			keyFor(projectile.getTargetActor()), projectile.getId(), null, null, null,
			observation.key, viewKey, worldView == null ? null : worldView.getPlane(),
			location == null ? -1 : location.getSceneX(), location == null ? -1 : location.getSceneY(),
			location == null ? null : "scene", null, "observed", null,
			java.util.Collections.emptyList());
		pendingEvents.add(event);
		killAttribution.observe(event);
	}

	void captureNpcTransform(NPC npc, NPCComposition oldComposition)
	{
		if (!isRecording() || npc == null) return;
		NPCComposition current = npc.getTransformedComposition();
		Integer fromId = oldComposition == null ? null : oldComposition.getId();
		Integer toId = current == null ? null : current.getId();
		recordNpcDefinition(oldComposition);
		recordNpcDefinition(current);
		LocalPoint location = npc.getLocalLocation();
		pendingEvents.add(new RecordedEvent("event-" + nextEventId++, "NPC_CHANGED",
			client.getGameCycle(), null, keyFor(npc), null, toId, null, fromId, toId,
			null, null, null, location == null ? -1 : location.getSceneX(),
			location == null ? -1 : location.getSceneY(), location == null ? null : "scene",
			null, "observed", null, java.util.Collections.emptyList()));
	}

	void captureObjectDefinition(int definitionId)
	{
		if (!isRecording() || definitionId < 0 || recording.objectDefinitions.containsKey(definitionId)) return;
		DefinitionMetadataResolver.ResolvedObjectDefinition resolved = metadataResolver.object(definitionId);
		if (resolved != null) recording.recordObjectDefinition(definitionId, resolved.metadata);
	}

	void captureContainerChange(int containerId, ItemContainer container)
	{
		if (!isRecording() || container == null)
		{
			return;
		}
		String type;
		if (containerId == InventoryID.INV)
		{
			type = "INVENTORY";
		}
		else if (containerId == InventoryID.WORN)
		{
			type = "EQUIPMENT";
		}
		else
		{
			return;
		}
		pendingContainerChanges.add(new ContainerSnapshot(type, client.getGameCycle(), snapshotItems(container)));
	}

	void captureTick()
	{
		if (!isRecording())
		{
			return;
		}
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		List<ActorSnapshot> actors = new ArrayList<>();
		for (Player player : worldView.players())
		{
			actors.add(snapshotActor(player));
		}
		for (NPC npc : worldView.npcs())
		{
			actors.add(snapshotActor(npc));
		}
		int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		recordResourceChange("HITPOINTS", hitpoints, previousHitpoints);
		recordResourceChange("PRAYER", prayer, previousPrayer);
		previousHitpoints = hitpoints;
		previousPrayer = prayer;
		List<String> activePrayers = capturePrayers();

		long observedAt = System.currentTimeMillis();
		long elapsedMillis = Math.max(0L, (System.nanoTime() - recordingStartedNanos) / 1_000_000L);
		int world = client.getWorld();
		int viewIdentity = viewIdentity(worldView);
		ProjectileTracker.Delta projectileDelta = projectiles.drain(client.getGameCycle(),
			"view-" + viewIdentity);
		LocalCombatState combatState = captureCombatState();
		recording.add(new RecordedTick(recording.ticks.size(), client.getGameCycle(),
			client.getTickCount(), observedAt, elapsedMillis, world > 0 ? world : null,
			"view-" + viewIdentity,
			worldView.isInstance() ? worldView.getInstanceTemplateChunks() : null,
			worldView.getPlane(), worldView.getBaseX(), worldView.getBaseY(), worldView.isInstance(),
			hitpoints, client.getRealSkillLevel(Skill.HITPOINTS),
			prayer, client.getRealSkillLevel(Skill.PRAYER), actors,
			snapshotItems(client.getItemContainer(InventoryID.INV)),
			snapshotItems(client.getItemContainer(InventoryID.WORN)),
			pendingContainerChanges, pendingEvents, captureScene(worldView), Collections.emptyList(),
			activePrayers, projectileDelta.upserts, projectileDelta.removals, combatState));
		pendingEvents.clear();
		pendingContainerChanges.clear();
	}

	private LocalCombatState captureCombatState()
	{
		return new LocalCombatState(
			client.getBoostedSkillLevel(Skill.ATTACK), client.getRealSkillLevel(Skill.ATTACK),
			client.getBoostedSkillLevel(Skill.STRENGTH), client.getRealSkillLevel(Skill.STRENGTH),
			client.getBoostedSkillLevel(Skill.DEFENCE), client.getRealSkillLevel(Skill.DEFENCE),
			client.getBoostedSkillLevel(Skill.RANGED), client.getRealSkillLevel(Skill.RANGED),
			client.getBoostedSkillLevel(Skill.MAGIC), client.getRealSkillLevel(Skill.MAGIC),
			client.getEnergy(), client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT),
			client.getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1);
	}

	private RecordedEvent observedEvent(String type, String actorKey, String targetKey,
		int id, int value, int sceneX, int sceneY, String detail)
	{
		return new RecordedEvent("event-" + nextEventId++, type, client.getGameCycle(), null,
			actorKey, targetKey, RecordedEvent.usesDefinitionId(type) && id >= 0 ? id : null,
			RecordedEvent.usesAmount(type) ? value : null, null, null, sceneX, sceneY,
			sceneX < 0 || sceneY < 0 ? null : "scene", detail, "observed", null,
			java.util.Collections.emptyList());
	}

	private void recordResourceChange(String resource, int current, int previous)
	{
		if (previous >= 0 && current != previous)
		{
			pendingEvents.add(observedEvent("RESOURCE_CHANGE", keyFor(client.getLocalPlayer()),
				null, 0, current - previous, -1, -1, resource));
		}
	}

	private List<String> capturePrayers()
	{
		List<String> active = new ArrayList<>();
		Set<String> current = new HashSet<>();
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				current.add(prayer.name());
				active.add(prayer.name());
			}
		}
		// RuneLite reports each upgraded prayer together with the prayer it replaces.
		suppressReplacedPrayer(current, active, Prayer.DEADEYE, Prayer.EAGLE_EYE);
		suppressReplacedPrayer(current, active, Prayer.MYSTIC_VIGOUR, Prayer.MYSTIC_MIGHT);
		for (String prayer : current)
		{
			if (!previousPrayers.contains(prayer))
			{
				pendingEvents.add(observedEvent("PRAYER_CHANGE", keyFor(client.getLocalPlayer()),
					null, 0, 1, -1, -1, prayer));
			}
		}
		for (String prayer : previousPrayers)
		{
			if (!current.contains(prayer))
			{
				pendingEvents.add(observedEvent("PRAYER_CHANGE", keyFor(client.getLocalPlayer()),
					null, 0, 0, -1, -1, prayer));
			}
		}
		previousPrayers.clear();
		previousPrayers.addAll(current);
		return active;
	}

	private static void suppressReplacedPrayer(Set<String> current, List<String> active,
		Prayer upgrade, Prayer replaced)
	{
		if (current.contains(upgrade.name()))
		{
			current.remove(replaced.name());
			active.remove(replaced.name());
		}
	}

	private List<SceneTileSnapshot> captureScene(WorldView worldView)
	{
		List<SceneTileSnapshot> changes = new ArrayList<>();
		Tile[][][] tiles = worldView.getScene().getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return changes;
		}
		int[][] collision = worldView.getCollisionMaps() != null
			&& plane < worldView.getCollisionMaps().length && worldView.getCollisionMaps()[plane] != null
			? worldView.getCollisionMaps()[plane].getFlags() : null;
		for (int x = 0; x < tiles[plane].length; x++)
		{
			for (int y = 0; y < tiles[plane][x].length; y++)
			{
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}
				SceneTilePaint paint = tile.getSceneTilePaint();
				int color = paint == null ? -1 : paint.getRBG();
				int flags = collision != null && x < collision.length && y < collision[x].length
					? collision[x][y] : 0;
				GameObject[] objects = tile.getGameObjects();
				List<Integer> ids = new ArrayList<>();
				if (objects != null)
				{
					for (GameObject object : objects)
					{
						if (object != null && !ids.contains(object.getId()))
						{
							ids.add(object.getId());
							captureObjectDefinition(object.getId());
						}
					}
				}
				int[] objectIds = ids.stream().mapToInt(Integer::intValue).toArray();
				captureObjectDefinition(objectId(tile.getWallObject()));
				captureObjectDefinition(objectId(tile.getGroundObject()));
				captureObjectDefinition(objectId(tile.getDecorativeObject()));
				SceneTileSnapshot snapshot = new SceneTileSnapshot(viewIdentity(worldView), plane,
					worldView.getBaseX() + x, worldView.getBaseY() + y,
					heightAt(worldView, plane, x, y), color, flags, objectId(tile.getWallObject()),
					objectId(tile.getGroundObject()), objectId(tile.getDecorativeObject()), objectIds);
				String old = recordedTiles.put(snapshot.mapKey(), snapshot.contentKey());
				if (!snapshot.contentKey().equals(old))
				{
					changes.add(snapshot);
				}
			}
		}
		return changes;
	}

	private static int viewIdentity(WorldView view)
	{
		return view.isInstance() ? 31 * view.getId() + Arrays.deepHashCode(view.getInstanceTemplateChunks()) : view.getId();
	}

	private static int heightAt(WorldView view, int plane, int x, int y)
	{
		int[][][] heights = view.getTileHeights();
		return heights != null && plane < heights.length && x < heights[plane].length
			&& y < heights[plane][x].length ? heights[plane][x][y] : 0;
	}

	private static int objectId(TileObject object)
	{
		return object == null ? -1 : object.getId();
	}

	private List<ItemSnapshot> snapshotItems(ItemContainer container)
	{
		List<ItemSnapshot> snapshots = new ArrayList<>();
		if (container == null)
		{
			return null;
		}
		Item[] items = container.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				String name = client.getItemDefinition(item.getId()).getName();
				snapshots.add(new ItemSnapshot(slot, item.getId(), item.getQuantity(), name));
			}
		}
		return snapshots;
	}

	String keyFor(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}
		return actorKeys.computeIfAbsent(actor,
			ignored -> (actor instanceof NPC ? "npc-" : "player-") + nextActorId++);
	}

	private ActorSnapshot snapshotActor(Actor actor)
	{
		LocalPoint local = actor.getLocalLocation();
		WorldPoint world = actor.getWorldLocation();
		boolean npc = actor instanceof NPC;
		NPCComposition composition = npc ? ((NPC) actor).getTransformedComposition() : null;
		int npcId = composition == null ? npc ? ((NPC) actor).getId() : -1 : composition.getId();
		if (npc) recordNpcDefinition(composition);
		String label;
		if (npc)
		{
			label = composition != null && composition.getName() != null
				? composition.getName() : actor.getName();
		}
		else
		{
			label = actor.getName();
		}
		int tileSize = composition == null ? 1 : Math.max(1, composition.getSize());
		List<ItemSnapshot> visibleEquipment = npc ? new ArrayList<>() : snapshotVisibleEquipment((Player) actor);
		String overheadIcon = npc || ((Player) actor).getOverheadIcon() == null
			? null : ((Player) actor).getOverheadIcon().name();
		int combatLevel = composition == null
			? npc ? -1 : ((Player) actor).getCombatLevel() : composition.getCombatLevel();
		MovementAnimations movementAnimations = new MovementAnimations(actor.getIdlePoseAnimation(),
			actor.getIdleRotateLeft(), actor.getIdleRotateRight(), actor.getWalkAnimation(),
			actor.getWalkRotateLeft(), actor.getWalkRotateRight(), actor.getWalkRotate180(),
			actor.getRunAnimation());
		return new ActorSnapshot(keyFor(actor), npc ? "NPC" : "PLAYER", label,
			actor == client.getLocalPlayer(), npcId,
			world.getX(), world.getY(), local.getSceneX(), local.getSceneY(), local.getX(), local.getY(),
			world.getPlane(), viewIdentity(actor.getWorldView()),
			"view-" + viewIdentity(actor.getWorldView()), tileSize,
			actor.getCurrentOrientation(), actor.getAnimation(), actor.getPoseAnimation(),
			actor.getHealthRatio(), actor.getHealthScale(), keyFor(actor.getInteracting()), actor.isDead(),
			visibleEquipment, overheadIcon, combatLevel < 0 ? null : combatLevel,
			movementAnimations);
	}

	private void recordNpcDefinition(NPCComposition composition)
	{
		if (composition != null && !recording.npcDefinitions.containsKey(composition.getId()))
		{
			recording.recordNpcDefinition(composition.getId(), metadataResolver.npc(composition));
		}
	}

	private List<ItemSnapshot> snapshotVisibleEquipment(Player player)
	{
		List<ItemSnapshot> equipment = new ArrayList<>();
		if (player.getPlayerComposition() == null)
		{
			return null;
		}
		for (KitType slot : KitType.values())
		{
			int itemId = player.getPlayerComposition().getEquipmentId(slot);
			if (itemId >= 0)
			{
				equipment.add(new ItemSnapshot(slot.getIndex(), itemId, 1,
					client.getItemDefinition(itemId).getName()));
			}
		}
		return equipment;
	}
}
