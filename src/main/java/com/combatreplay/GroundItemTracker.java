package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.TileItem;

final class GroundItemTracker
{
	private final Map<TileItem, String> keys = new IdentityHashMap<>();
	private final Map<String, GroundItemSnapshot> pendingUpserts = new LinkedHashMap<>();
	private final List<String> pendingRemovals = new ArrayList<>();
	private int nextId = 1;

	String upsert(TileItem item, int itemId, String itemName, int quantity, String viewKey,
		int plane, int x, int y, int gameCycle)
	{
		String key = keys.computeIfAbsent(item, ignored -> "ground-item-" + nextId++);
		pendingUpserts.put(key, new GroundItemSnapshot(key, itemId, itemName, quantity,
			viewKey, plane, x, y, gameCycle));
		return key;
	}

	void remove(TileItem item)
	{
		String key = keys.remove(item);
		if (key != null)
		{
			pendingUpserts.remove(key);
			pendingRemovals.add(key);
		}
	}

	Delta drain()
	{
		Delta delta = new Delta(new ArrayList<>(pendingUpserts.values()), new ArrayList<>(pendingRemovals));
		pendingUpserts.clear();
		pendingRemovals.clear();
		return delta;
	}

	void reset()
	{
		keys.clear(); pendingUpserts.clear(); pendingRemovals.clear(); nextId = 1;
	}

	static final class Delta
	{
		final List<GroundItemSnapshot> upserts;
		final List<String> removals;
		Delta(List<GroundItemSnapshot> upserts, List<String> removals)
		{
			this.upserts = Collections.unmodifiableList(upserts);
			this.removals = Collections.unmodifiableList(removals);
		}
	}
}
