package com.combatreplay;

/** Whether the recorder could observe the top-level world view at this point. */
final class ObservationCoverage
{
	final boolean available;
	final String observation;

	ObservationCoverage(boolean available, String observation)
	{
		this.available = available;
		this.observation = observation;
	}
}
