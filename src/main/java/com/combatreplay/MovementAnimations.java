package com.combatreplay;

final class MovementAnimations
{
	final int idle;
	final int idleRotateLeft;
	final int idleRotateRight;
	final int walk;
	final int walkRotateLeft;
	final int walkRotateRight;
	final int walkRotate180;
	final int run;

	MovementAnimations(int idle, int idleRotateLeft, int idleRotateRight, int walk,
		int walkRotateLeft, int walkRotateRight, int walkRotate180, int run)
	{
		this.idle = idle;
		this.idleRotateLeft = idleRotateLeft;
		this.idleRotateRight = idleRotateRight;
		this.walk = walk;
		this.walkRotateLeft = walkRotateLeft;
		this.walkRotateRight = walkRotateRight;
		this.walkRotate180 = walkRotate180;
		this.run = run;
	}
}
