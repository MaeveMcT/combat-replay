package com.combatreplay;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.kit.KitType;
import net.runelite.api.coords.WorldPoint;

@Singleton
final class CombatRecorder
{
	private final Client client;
	private final Map<Actor, String> actorKeys = new IdentityHashMap<>();
	private final Map<Player, String> playerLabels = new IdentityHashMap<>();
	private final List<RecordedEvent> pendingEvents = new ArrayList<>();
	private final List<ContainerSnapshot> pendingContainerChanges = new ArrayList<>();
	private final Map<String, String> recordedTiles = new HashMap<>();
	private final Set<String> previousPrayers = new HashSet<>();
	private CombatRecording recording;
	private int nextActorId;
	private int nextPlayerLabel;
	private int previousHitpoints = -1;
	private int previousPrayer = -1;

	@Inject
	CombatRecorder(Client client)
	{
		this.client = client;
	}

	void start()
	{
		recording = new CombatRecording(System.currentTimeMillis());
		actorKeys.clear();
		playerLabels.clear();
		pendingEvents.clear();
		pendingContainerChanges.clear();
		recordedTiles.clear();
		previousPrayers.clear();
		nextActorId = 1;
		nextPlayerLabel = 1;
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
		pendingEvents.add(new RecordedEvent(type, client.getGameCycle(), keyFor(actor), keyFor(target),
			id, value, location == null ? -1 : location.getSceneX(),
			location == null ? -1 : location.getSceneY(), detail));
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

		recording.add(new RecordedTick(recording.ticks.size(), client.getGameCycle(),
			worldView.getBaseX(), worldView.getBaseY(), worldView.isInstance(),
			hitpoints, client.getRealSkillLevel(Skill.HITPOINTS),
			prayer, client.getRealSkillLevel(Skill.PRAYER), actors,
			snapshotItems(client.getItemContainer(InventoryID.INV)),
			snapshotItems(client.getItemContainer(InventoryID.WORN)),
			pendingContainerChanges, pendingEvents, captureScene(worldView), activePrayers));
		pendingEvents.clear();
		pendingContainerChanges.clear();
	}

	private void recordResourceChange(String resource, int current, int previous)
	{
		if (previous >= 0 && current != previous)
		{
			pendingEvents.add(new RecordedEvent("RESOURCE_CHANGE", client.getGameCycle(),
				keyFor(client.getLocalPlayer()), null, 0, current - previous, -1, -1, resource));
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
		for (String prayer : current)
		{
			if (!previousPrayers.contains(prayer))
			{
				pendingEvents.add(new RecordedEvent("PRAYER_CHANGE", client.getGameCycle(),
					keyFor(client.getLocalPlayer()), null, 0, 1, -1, -1, prayer));
			}
		}
		for (String prayer : previousPrayers)
		{
			if (!current.contains(prayer))
			{
				pendingEvents.add(new RecordedEvent("PRAYER_CHANGE", client.getGameCycle(),
					keyFor(client.getLocalPlayer()), null, 0, 0, -1, -1, prayer));
			}
		}
		previousPrayers.clear();
		previousPrayers.addAll(current);
		return active;
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
						}
					}
				}
				int[] objectIds = ids.stream().mapToInt(Integer::intValue).toArray();
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
			return snapshots;
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
		int npcId = npc ? ((NPC) actor).getId() : -1;
		String label;
		if (npc)
		{
			label = composition != null && composition.getName() != null
				? composition.getName() : actor.getName();
		}
		else if (actor == client.getLocalPlayer())
		{
			label = "You";
		}
		else
		{
			label = playerLabels.computeIfAbsent((Player) actor,
				ignored -> "Player " + nextPlayerLabel++);
		}
		int tileSize = composition == null ? 1 : Math.max(1, composition.getSize());
		List<ItemSnapshot> visibleEquipment = npc ? new ArrayList<>() : snapshotVisibleEquipment((Player) actor);
		String overheadIcon = npc || ((Player) actor).getOverheadIcon() == null
			? null : ((Player) actor).getOverheadIcon().name();
		return new ActorSnapshot(keyFor(actor), npc ? "NPC" : "PLAYER",
			label == null ? (npc ? "NPC" : "Player") : label, npcId,
			world.getX(), world.getY(), local.getSceneX(), local.getSceneY(), local.getX(), local.getY(),
			world.getPlane(), viewIdentity(actor.getWorldView()), tileSize,
			actor.getCurrentOrientation(), actor.getAnimation(), actor.getPoseAnimation(),
			actor.getHealthRatio(), actor.getHealthScale(), keyFor(actor.getInteracting()), actor.isDead(),
			visibleEquipment, overheadIcon);
	}

	private List<ItemSnapshot> snapshotVisibleEquipment(Player player)
	{
		List<ItemSnapshot> equipment = new ArrayList<>();
		if (player.getPlayerComposition() == null)
		{
			return equipment;
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
