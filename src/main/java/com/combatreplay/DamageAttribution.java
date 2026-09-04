package com.combatreplay;

final class DamageAttribution
{
	private DamageAttribution()
	{
	}

	static boolean isLocalPlayer(String detail)
	{
		return "mine".equals(detail);
	}

	static String sourceDescription(String detail)
	{
		return isLocalPlayer(detail) ? "You (client-attributed)" : null;
	}
}
