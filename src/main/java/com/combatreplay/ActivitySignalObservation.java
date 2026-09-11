package com.combatreplay;

/** A raw, uninterpreted observation of an allowlisted activity signal. */
final class ActivitySignalObservation
{
	final int varbitId;
	final Integer value;
	final String observation;

	ActivitySignalObservation(int varbitId, Integer value, String observation)
	{
		this.varbitId = varbitId;
		this.value = value;
		this.observation = observation;
	}
}
