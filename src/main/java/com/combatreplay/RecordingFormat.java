package com.combatreplay;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compact on-disk representation. The viewer continues to consume fully reconstructed ticks. */
final class RecordingFormat
{
	private RecordingFormat()
	{
	}

	static FileRecording encode(CombatRecording recording)
	{
		FileRecording file = new FileRecording();
		file.version = CombatRecording.FORMAT_VERSION;
		file.started = recording.startedAtEpochMillis;
		file.ended = recording.endedAtEpochMillis;
		file.name = recording.name;
		file.itemNames = collectItemNames(recording);
		Map<String, ActorSnapshot> previousActors = new LinkedHashMap<>();
		RecordedTick previous = null;
		for (RecordedTick tick : recording.ticks)
		{
			FileTick encoded = encodeTick(tick, previous, previousActors);
			file.ticks.add(encoded);
			previous = tick;
			previousActors = actorsByKey(tick.actors);
		}
		return file;
	}

	static CombatRecording decode(FileRecording file)
	{
		if (file == null || file.version != CombatRecording.FORMAT_VERSION)
		{
			throw new IllegalArgumentException("Unsupported combat recording format");
		}
		List<RecordedTick> ticks = new ArrayList<>();
		Map<String, ActorSnapshot> actors = new LinkedHashMap<>();
		RecordedTick previous = null;
		for (int index = 0; index < file.ticks.size(); index++)
		{
			RecordedTick tick = decodeTick(file.ticks.get(index), index, previous, actors,
				file.itemNames == null ? Collections.emptyMap() : file.itemNames);
			ticks.add(tick);
			previous = tick;
			actors = actorsByKey(tick.actors);
		}
		return CombatRecording.restored(file.started, file.ended, file.name, ticks);
	}

	private static FileTick encodeTick(RecordedTick tick, RecordedTick previous,
		Map<String, ActorSnapshot> previousActors)
	{
		FileTick file = new FileTick();
		file.cycle = tick.gameCycle;
		file.baseX = changed(previous == null ? null : previous.baseX, tick.baseX);
		file.baseY = changed(previous == null ? null : previous.baseY, tick.baseY);
		file.instanced = previous == null || previous.instanced != tick.instanced ? tick.instanced : null;
		file.hitpoints = changed(previous == null ? null : previous.hitpoints, tick.hitpoints);
		file.maximumHitpoints = changed(previous == null ? null : previous.maximumHitpoints, tick.maximumHitpoints);
		file.prayer = changed(previous == null ? null : previous.prayer, tick.prayer);
		file.maximumPrayer = changed(previous == null ? null : previous.maximumPrayer, tick.maximumPrayer);

		Map<String, ActorSnapshot> currentActors = actorsByKey(tick.actors);
		for (ActorSnapshot actor : tick.actors)
		{
			ActorDelta delta = ActorDelta.between(previousActors.get(actor.key), actor);
			if (delta.hasChanges())
			{
				file.actors.add(delta);
			}
		}
		for (String key : previousActors.keySet())
		{
			if (!currentActors.containsKey(key))
			{
				file.removedActors.add(key);
			}
		}
		if (file.actors.isEmpty()) file.actors = null;
		if (file.removedActors.isEmpty()) file.removedActors = null;

		if (previous == null || !sameItems(previous.inventory, tick.inventory)) file.inventory = encodeItems(tick.inventory);
		if (previous == null || !sameItems(previous.equipment, tick.equipment)) file.equipment = encodeItems(tick.equipment);
		if (tick.containerChanges != null && !tick.containerChanges.isEmpty())
		{
			file.containers = new ArrayList<>();
			for (ContainerSnapshot container : tick.containerChanges)
			{
				FileContainer value = new FileContainer();
				value.type = container.container; value.cycle = container.gameCycle;
				value.items = encodeItems(container.items); file.containers.add(value);
			}
		}
		if (tick.events != null && !tick.events.isEmpty())
		{
			file.events = new ArrayList<>();
			for (RecordedEvent event : tick.events) file.events.add(FileEvent.from(event, tick.gameCycle));
		}
		if (tick.sceneTiles != null && !tick.sceneTiles.isEmpty()) file.scene = packScene(tick.sceneTiles);
		if (previous == null || !Objects.equals(previous.activePrayers, tick.activePrayers))
		{
			file.activePrayers = tick.activePrayers;
		}
		return file;
	}

	private static RecordedTick decodeTick(FileTick file, int index, RecordedTick previous,
		Map<String, ActorSnapshot> previousActors, Map<Integer, String> itemNames)
	{
		int baseX = value(file.baseX, previous == null ? 0 : previous.baseX);
		int baseY = value(file.baseY, previous == null ? 0 : previous.baseY);
		boolean instanced = file.instanced == null ? previous != null && previous.instanced : file.instanced;
		int hitpoints = value(file.hitpoints, previous == null ? 0 : previous.hitpoints);
		int maximumHitpoints = value(file.maximumHitpoints, previous == null ? 0 : previous.maximumHitpoints);
		int prayer = value(file.prayer, previous == null ? 0 : previous.prayer);
		int maximumPrayer = value(file.maximumPrayer, previous == null ? 0 : previous.maximumPrayer);

		Map<String, ActorSnapshot> actors = new LinkedHashMap<>(previousActors);
		if (file.removedActors != null) for (String key : file.removedActors) actors.remove(key);
		if (file.actors != null)
		{
			for (ActorDelta delta : file.actors)
				actors.put(delta.key, delta.apply(actors.get(delta.key), itemNames));
		}
		List<ItemSnapshot> inventory = file.inventory == null
			? previous.inventory : decodeItems(file.inventory, itemNames);
		List<ItemSnapshot> equipment = file.equipment == null
			? previous.equipment : decodeItems(file.equipment, itemNames);
		List<ContainerSnapshot> containers = new ArrayList<>();
		if (file.containers != null) for (FileContainer container : file.containers)
		{
			containers.add(new ContainerSnapshot(container.type, container.cycle,
				decodeItems(container.items, itemNames)));
		}
		List<RecordedEvent> events = new ArrayList<>();
		if (file.events != null) for (FileEvent event : file.events) events.add(event.toEvent(file.cycle));
		List<SceneTileSnapshot> scene = file.scene == null ? Collections.emptyList() : unpackScene(file.scene);
		List<String> prayers = file.activePrayers == null
			? previous.activePrayers : file.activePrayers;
		return new RecordedTick(index, file.cycle, baseX, baseY, instanced, hitpoints,
			maximumHitpoints, prayer, maximumPrayer, new ArrayList<>(actors.values()), inventory,
			equipment, containers, events, scene, prayers);
	}

	private static Map<Integer, String> collectItemNames(CombatRecording recording)
	{
		Map<Integer, String> names = new HashMap<>();
		for (RecordedTick tick : recording.ticks)
		{
			addNames(names, tick.inventory); addNames(names, tick.equipment);
			for (ActorSnapshot actor : tick.actors) addNames(names, actor.visibleEquipment);
			for (ContainerSnapshot container : tick.containerChanges) addNames(names, container.items);
		}
		return names;
	}

	private static void addNames(Map<Integer, String> names, List<ItemSnapshot> items)
	{
		if (items != null) for (ItemSnapshot item : items)
		{
			if (item.name != null && !item.name.isEmpty()) names.putIfAbsent(item.itemId, item.name);
		}
	}

	private static List<FileItem> encodeItems(List<ItemSnapshot> items)
	{
		List<FileItem> result = new ArrayList<>();
		if (items != null) for (ItemSnapshot item : items) result.add(new FileItem(item.slot, item.itemId, item.quantity));
		return result;
	}

	private static List<ItemSnapshot> decodeItems(List<FileItem> items, Map<Integer, String> names)
	{
		List<ItemSnapshot> result = new ArrayList<>();
		if (items != null) for (FileItem item : items)
			result.add(new ItemSnapshot(item.slot, item.id, item.quantity, names.get(item.id)));
		return result;
	}

	private static boolean sameItems(List<ItemSnapshot> left, List<ItemSnapshot> right)
	{
		if (left == right) return true;
		if (left == null || right == null || left.size() != right.size()) return false;
		for (int i = 0; i < left.size(); i++)
		{
			ItemSnapshot a = left.get(i), b = right.get(i);
			if (a.slot != b.slot || a.itemId != b.itemId || a.quantity != b.quantity) return false;
		}
		return true;
	}

	private static int[] packScene(List<SceneTileSnapshot> tiles)
	{
		int length = 0;
		for (SceneTileSnapshot tile : tiles) length += 11 + tile.gameObjectIds.length;
		int[] packed = new int[length]; int offset = 0;
		for (SceneTileSnapshot tile : tiles)
		{
			packed[offset++] = tile.worldViewId; packed[offset++] = tile.plane;
			packed[offset++] = tile.x; packed[offset++] = tile.y; packed[offset++] = tile.height;
			packed[offset++] = tile.terrainColor; packed[offset++] = tile.collisionFlags;
			packed[offset++] = tile.wallId; packed[offset++] = tile.groundObjectId;
			packed[offset++] = tile.decorativeObjectId; packed[offset++] = tile.gameObjectIds.length;
			for (int id : tile.gameObjectIds) packed[offset++] = id;
		}
		return packed;
	}

	private static List<SceneTileSnapshot> unpackScene(int[] packed)
	{
		List<SceneTileSnapshot> result = new ArrayList<>(); int offset = 0;
		while (offset < packed.length)
		{
			int view = packed[offset++], plane = packed[offset++], x = packed[offset++], y = packed[offset++];
			int height = packed[offset++], color = packed[offset++], collision = packed[offset++];
			int wall = packed[offset++], ground = packed[offset++], decoration = packed[offset++];
			int count = packed[offset++]; int[] objects = new int[count];
			for (int i = 0; i < count; i++) objects[i] = packed[offset++];
			result.add(new SceneTileSnapshot(view, plane, x, y, height, color, collision,
				wall, ground, decoration, objects));
		}
		return result;
	}

	private static Map<String, ActorSnapshot> actorsByKey(List<ActorSnapshot> actors)
	{
		Map<String, ActorSnapshot> result = new LinkedHashMap<>();
		for (ActorSnapshot actor : actors) result.put(actor.key, actor);
		return result;
	}

	private static Integer changed(Integer previous, int current) { return previous == null || previous != current ? current : null; }
	private static int value(Integer changed, int previous) { return changed == null ? previous : changed; }
	private static <T> T changed(T previous, T current) { return Objects.equals(previous, current) ? null : current; }

	static final class FileRecording
	{
		@SerializedName("v") int version;
		@SerializedName("s") long started;
		@SerializedName("e") long ended;
		@SerializedName("n") String name;
		@SerializedName("d") Map<Integer, String> itemNames = new HashMap<>();
		@SerializedName("t") List<FileTick> ticks = new ArrayList<>();
	}

	static final class FileTick
	{
		@SerializedName("c") int cycle;
		@SerializedName("x") Integer baseX;
		@SerializedName("y") Integer baseY;
		@SerializedName("z") Boolean instanced;
		@SerializedName("h") Integer hitpoints;
		@SerializedName("H") Integer maximumHitpoints;
		@SerializedName("p") Integer prayer;
		@SerializedName("P") Integer maximumPrayer;
		@SerializedName("a") List<ActorDelta> actors = new ArrayList<>();
		@SerializedName("r") List<String> removedActors = new ArrayList<>();
		@SerializedName("i") List<FileItem> inventory;
		@SerializedName("q") List<FileItem> equipment;
		@SerializedName("cC") List<FileContainer> containers;
		@SerializedName("eV") List<FileEvent> events;
		@SerializedName("s") int[] scene;
		@SerializedName("pA") List<String> activePrayers;
	}

	static final class FileItem
	{
		@SerializedName("s") int slot;
		@SerializedName("i") int id;
		@SerializedName("q") int quantity;
		FileItem() { }
		FileItem(int slot, int id, int quantity) { this.slot = slot; this.id = id; this.quantity = quantity; }
	}

	static final class FileContainer
	{
		@SerializedName("t") String type;
		@SerializedName("c") int cycle;
		@SerializedName("i") List<FileItem> items;
	}

	static final class FileEvent
	{
		@SerializedName("t") String type;
		@SerializedName("c") Integer cycle;
		@SerializedName("a") String actor;
		@SerializedName("g") String target;
		@SerializedName("i") Integer id;
		@SerializedName("v") Integer value;
		@SerializedName("x") Integer x;
		@SerializedName("y") Integer y;
		@SerializedName("d") String detail;

		static FileEvent from(RecordedEvent event, int tickCycle)
		{
			FileEvent value = new FileEvent(); value.type = event.type;
			value.cycle = event.gameCycle == tickCycle ? null : event.gameCycle;
			value.actor = event.actorKey; value.target = event.targetKey;
			value.id = event.id == 0 ? null : event.id; value.value = event.value == 0 ? null : event.value;
			value.x = event.sceneX == -1 ? null : event.sceneX; value.y = event.sceneY == -1 ? null : event.sceneY;
			value.detail = event.detail; return value;
		}

		RecordedEvent toEvent(int tickCycle)
		{
			return new RecordedEvent(type, cycle == null ? tickCycle : cycle, actor, target,
				id == null ? 0 : id, value == null ? 0 : value,
				x == null ? -1 : x, y == null ? -1 : y, detail);
		}
	}

	static final class ActorDelta
	{
		@SerializedName("k") String key;
		@SerializedName("t") String kind;
		@SerializedName("l") String label;
		@SerializedName("n") Integer npcId;
		@SerializedName("u") Integer worldX;
		@SerializedName("v") Integer worldY;
		@SerializedName("x") Integer sceneX;
		@SerializedName("y") Integer sceneY;
		@SerializedName("X") Integer localX;
		@SerializedName("Y") Integer localY;
		@SerializedName("p") Integer plane;
		@SerializedName("w") Integer worldViewId;
		@SerializedName("s") Integer size;
		@SerializedName("o") Integer orientation;
		@SerializedName("a") Integer animation;
		@SerializedName("f") Integer poseAnimation;
		@SerializedName("h") Integer healthRatio;
		@SerializedName("m") Integer healthScale;
		@SerializedName("g") String targetKey;
		@SerializedName("d") Boolean dead;
		@SerializedName("q") List<FileItem> visibleEquipment;
		@SerializedName("j") String overheadIcon;

		static ActorDelta between(ActorSnapshot old, ActorSnapshot actor)
		{
			ActorDelta delta = new ActorDelta(); delta.key = actor.key;
			delta.kind = old == null ? actor.kind : changed(old.kind, actor.kind);
			delta.label = old == null ? actor.label : changed(old.label, actor.label);
			delta.npcId = changed(old == null ? null : old.npcId, actor.npcId);
			delta.worldX = changed(old == null ? null : old.worldX, actor.worldX);
			delta.worldY = changed(old == null ? null : old.worldY, actor.worldY);
			delta.sceneX = changed(old == null ? null : old.sceneX, actor.sceneX);
			delta.sceneY = changed(old == null ? null : old.sceneY, actor.sceneY);
			delta.localX = changed(old == null ? null : old.localX, actor.localX);
			delta.localY = changed(old == null ? null : old.localY, actor.localY);
			delta.plane = changed(old == null ? null : old.plane, actor.plane);
			delta.worldViewId = changed(old == null ? null : old.worldViewId, actor.worldViewId);
			delta.size = changed(old == null ? null : old.size, actor.size);
			delta.orientation = changed(old == null ? null : old.orientation, actor.orientation);
			delta.animation = changed(old == null ? null : old.animation, actor.animation);
			delta.poseAnimation = changed(old == null ? null : old.poseAnimation, actor.poseAnimation);
			delta.healthRatio = changed(old == null ? null : old.healthRatio, actor.healthRatio);
			delta.healthScale = changed(old == null ? null : old.healthScale, actor.healthScale);
			if (old == null || !Objects.equals(old.targetKey, actor.targetKey)) delta.targetKey = actor.targetKey == null ? "" : actor.targetKey;
			delta.dead = old == null || old.dead != actor.dead ? actor.dead : null;
			if (old == null || !sameItems(old.visibleEquipment, actor.visibleEquipment))
				delta.visibleEquipment = encodeItems(actor.visibleEquipment);
			if (old == null || !Objects.equals(old.overheadIcon, actor.overheadIcon))
				delta.overheadIcon = actor.overheadIcon == null ? "" : actor.overheadIcon;
			return delta;
		}

		boolean hasChanges()
		{
			return kind != null || label != null || npcId != null || worldX != null || worldY != null
				|| sceneX != null || sceneY != null || localX != null || localY != null || plane != null
				|| worldViewId != null || size != null || orientation != null || animation != null
				|| poseAnimation != null || healthRatio != null || healthScale != null
				|| targetKey != null || dead != null || visibleEquipment != null || overheadIcon != null;
		}

		ActorSnapshot apply(ActorSnapshot old, Map<Integer, String> itemNames)
		{
			String target = targetKey == null ? old == null ? null : old.targetKey : targetKey.isEmpty() ? null : targetKey;
			List<ItemSnapshot> equipment = visibleEquipment == null
				? old == null ? Collections.emptyList() : old.visibleEquipment
				: decodeItems(visibleEquipment, itemNames);
			String icon = overheadIcon == null ? old == null ? null : old.overheadIcon
				: overheadIcon.isEmpty() ? null : overheadIcon;
			return new ActorSnapshot(key, kind == null ? old.kind : kind, label == null ? old.label : label,
				value(npcId, old == null ? -1 : old.npcId), value(worldX, old == null ? 0 : old.worldX),
				value(worldY, old == null ? 0 : old.worldY), value(sceneX, old == null ? 0 : old.sceneX),
				value(sceneY, old == null ? 0 : old.sceneY), value(localX, old == null ? 0 : old.localX),
				value(localY, old == null ? 0 : old.localY), value(plane, old == null ? 0 : old.plane),
				value(worldViewId, old == null ? 0 : old.worldViewId), value(size, old == null ? 1 : old.size),
				value(orientation, old == null ? 0 : old.orientation), value(animation, old == null ? -1 : old.animation),
				value(poseAnimation, old == null ? -1 : old.poseAnimation), value(healthRatio, old == null ? -1 : old.healthRatio),
				value(healthScale, old == null ? -1 : old.healthScale), target,
				dead == null ? old != null && old.dead : dead, equipment, icon);
		}
	}
}
