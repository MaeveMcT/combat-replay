package com.combatreplay;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;

/** Versioned allowlist of raw activity signals recorded without boundary interpretation. */
final class ActivitySignalRegistry
{
	static final List<Integer> CANDIDATE_VARBITS_IN_ORDER = Collections.unmodifiableList(Arrays.asList(
		VarbitID.PLAYER_IN_GAUNTLET,
		VarbitID.GAUNTLET_BOSS_STARTED,
		VarbitID.GAUNTLET_CORRUPTED));
	static final Set<Integer> CANDIDATE_VARBITS = Collections.unmodifiableSet(
		new HashSet<>(CANDIDATE_VARBITS_IN_ORDER));

	private ActivitySignalRegistry()
	{
	}
}
