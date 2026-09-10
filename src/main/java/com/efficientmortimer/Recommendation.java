package com.efficientmortimer;

final class Recommendation
{
	enum Action
	{
		TAKE,
		SKIP,
		UNAVAILABLE
	}

	private final Action action;
	private final int slot;

	Recommendation(Action action, int slot)
	{
		this.action = action;
		this.slot = slot;
	}

	static Recommendation unavailable()
	{
		return new Recommendation(Action.UNAVAILABLE, -1);
	}

	Action getAction()
	{
		return action;
	}

	int getSlot()
	{
		return slot;
	}
}
