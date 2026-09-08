package com.combatreplay;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;

@Singleton
final class RuneLiteDefinitionMetadataResolver implements DefinitionMetadataResolver
{
	private final Client client;

	@Inject
	RuneLiteDefinitionMetadataResolver(Client client)
	{
		this.client = client;
	}

	@Override
	public NpcDefinitionMetadata npc(NPCComposition composition)
	{
		if (composition == null) return null;
		return new NpcDefinitionMetadata(availableName(composition.getName()),
			composition.getCombatLevel() < 0 ? null : composition.getCombatLevel(),
			Math.max(1, composition.getSize()));
	}

	@Override
	public ResolvedObjectDefinition object(int definitionId)
	{
		if (definitionId < 0) return null;
		ObjectComposition definition = client.getObjectDefinition(definitionId);
		if (definition == null) return null;
		ObjectComposition effective = definition.getImpostorIds() == null ? definition : definition.getImpostor();
		if (effective == null) effective = definition;
		return new ResolvedObjectDefinition(effective.getId(), new ObjectDefinitionMetadata(
			availableName(effective.getName()), effective.getId(),
			Math.max(1, effective.getSizeX()),
			Math.max(1, effective.getSizeY()), availableId(effective.getMapIconId()),
			availableId(effective.getMapSceneId())));
	}

	private static String availableName(String name)
	{
		if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name.trim())) return null;
		return name;
	}

	private static Integer availableId(int id)
	{
		return id < 0 ? null : id;
	}
}
