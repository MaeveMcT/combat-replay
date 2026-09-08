package com.combatreplay;

import java.util.Objects;

/** Direct local-player combat values captured together at one tick. */
final class LocalCombatState
{
	final int attackCurrent, attackBase;
	final int strengthCurrent, strengthBase;
	final int defenceCurrent, defenceBase;
	final int rangedCurrent, rangedBase;
	final int magicCurrent, magicBase;
	final int runEnergyHundredths;
	final int specialAttackEnergyTenths;
	final boolean specialAttackEnabled;

	LocalCombatState(int attackCurrent, int attackBase, int strengthCurrent, int strengthBase,
		int defenceCurrent, int defenceBase, int rangedCurrent, int rangedBase,
		int magicCurrent, int magicBase, int runEnergyHundredths,
		int specialAttackEnergyTenths, boolean specialAttackEnabled)
	{
		this.attackCurrent = attackCurrent;
		this.attackBase = attackBase;
		this.strengthCurrent = strengthCurrent;
		this.strengthBase = strengthBase;
		this.defenceCurrent = defenceCurrent;
		this.defenceBase = defenceBase;
		this.rangedCurrent = rangedCurrent;
		this.rangedBase = rangedBase;
		this.magicCurrent = magicCurrent;
		this.magicBase = magicBase;
		this.runEnergyHundredths = runEnergyHundredths;
		this.specialAttackEnergyTenths = specialAttackEnergyTenths;
		this.specialAttackEnabled = specialAttackEnabled;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof LocalCombatState)) return false;
		LocalCombatState value = (LocalCombatState) other;
		return attackCurrent == value.attackCurrent && attackBase == value.attackBase
			&& strengthCurrent == value.strengthCurrent && strengthBase == value.strengthBase
			&& defenceCurrent == value.defenceCurrent && defenceBase == value.defenceBase
			&& rangedCurrent == value.rangedCurrent && rangedBase == value.rangedBase
			&& magicCurrent == value.magicCurrent && magicBase == value.magicBase
			&& runEnergyHundredths == value.runEnergyHundredths
			&& specialAttackEnergyTenths == value.specialAttackEnergyTenths
			&& specialAttackEnabled == value.specialAttackEnabled;
	}

	@Override public int hashCode()
	{
		return Objects.hash(attackCurrent, attackBase, strengthCurrent, strengthBase,
			defenceCurrent, defenceBase, rangedCurrent, rangedBase, magicCurrent, magicBase,
			runEnergyHundredths, specialAttackEnergyTenths, specialAttackEnabled);
	}
}
