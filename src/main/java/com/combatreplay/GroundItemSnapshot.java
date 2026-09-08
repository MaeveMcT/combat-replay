package com.combatreplay;

final class GroundItemSnapshot
{
	final String key;
	final int itemId;
	final String itemName;
	final int quantity;
	final String viewKey;
	final int plane;
	final int x;
	final int y;
	final int observedCycle;

	GroundItemSnapshot(String key, int itemId, String itemName, int quantity, String viewKey,
		int plane, int x, int y, int observedCycle)
	{
		this.key = key;
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantity = quantity;
		this.viewKey = viewKey;
		this.plane = plane;
		this.x = x;
		this.y = y;
		this.observedCycle = observedCycle;
	}
}
