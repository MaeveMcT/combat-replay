package com.combatreplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ContainerSnapshot
{
	final String container;
	final int gameCycle;
	final List<ItemSnapshot> items;

	ContainerSnapshot(String container, int gameCycle, List<ItemSnapshot> items)
	{
		this.container = container;
		this.gameCycle = gameCycle;
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
	}
}
