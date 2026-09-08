package com.combatreplay;

import net.runelite.api.NPCComposition;

/** Narrow cache boundary for metadata persisted with a recording. */
interface DefinitionMetadataResolver
{
	NpcDefinitionMetadata npc(NPCComposition composition);

	ResolvedObjectDefinition object(int definitionId);

	final class ResolvedObjectDefinition
	{
		final int effectiveDefinitionId;
		final ObjectDefinitionMetadata metadata;

		ResolvedObjectDefinition(int effectiveDefinitionId, ObjectDefinitionMetadata metadata)
		{
			this.effectiveDefinitionId = effectiveDefinitionId;
			this.metadata = metadata;
		}
	}
}
