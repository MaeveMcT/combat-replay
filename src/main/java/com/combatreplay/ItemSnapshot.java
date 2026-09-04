package com.combatreplay;

final class ItemSnapshot
{
	final int slot;
	final int itemId;
	final int quantity;
	final String name;

	ItemSnapshot(int slot, int itemId, int quantity)
	{
		this(slot, itemId, quantity, null);
	}

	ItemSnapshot(int slot, int itemId, int quantity, String name)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.quantity = quantity;
		this.name = name;
	}

	String displayName()
	{
		return name == null || name.isEmpty() ? "Item " + itemId : name;
	}
}
