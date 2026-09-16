package com.sepulchre.model;

import com.sepulchre.util.GameObjectUtil;
import com.sepulchre.util.SepulchreConstants;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

import java.util.HashSet;
import java.util.Set;

@Getter
public class WizardStatue
{
	private static final int ACTIVATION_CHECK_TICKS = 10;

	private final GameObject gameObject;
	private final WorldPoint location;
	private final Set<WorldPoint> fireTiles = new HashSet<>();

	@Setter
	private boolean hasEverFired = false;

	@Setter
	private int ticksSinceSpawn = 0;

	@Setter
	private int tickCounter = -1;

	@Setter
	private int firePhaseTicks = 2;

	@Setter
	private int safePhaseTicks = 1;

	@Setter
	private int warningPhaseTicks = 2;

	private boolean wasFiringLastTick = false;
	private boolean wasWarningLastTick = false;
	private boolean isFirstTick = true;

	private int cachedAnimationId = -1;

	public WizardStatue(GameObject gameObject)
	{
		this.gameObject = gameObject;
		this.location = gameObject.getWorldLocation();
	}

	public boolean isConfirmedActiveOrUnknown()
	{
		return hasEverFired || ticksSinceSpawn < ACTIVATION_CHECK_TICKS;
	}

	public boolean isFiring()
	{
		return cachedAnimationId == SepulchreConstants.WIZARD_ANIM_FIRE;
	}

	public boolean isWarning()
	{
		return cachedAnimationId == SepulchreConstants.WIZARD_ANIM_WARNING
			|| cachedAnimationId == SepulchreConstants.WIZARD_ANIM_PRE_WARNING;
	}

	public boolean isSafe()
	{
		return cachedAnimationId != SepulchreConstants.WIZARD_ANIM_FIRE
			&& cachedAnimationId != SepulchreConstants.WIZARD_ANIM_WARNING
			&& cachedAnimationId != SepulchreConstants.WIZARD_ANIM_PRE_WARNING;
	}

	public void addFireTile(WorldPoint tile)
	{
		fireTiles.add(tile);
	}

	public void onGameTick()
	{
		cachedAnimationId = GameObjectUtil.getAnimationId(gameObject);
		boolean currentlyFiring = isFiring();
		boolean currentlyWarning = isWarning();

		if (isFirstTick)
		{
			isFirstTick = false;
		}
		else
		{
			if (currentlyFiring && !wasFiringLastTick)
			{
				tickCounter = firePhaseTicks;
			}
			else if (currentlyWarning && !wasWarningLastTick && !currentlyFiring)
			{
				tickCounter = warningPhaseTicks;
			}
			else if (!currentlyFiring && wasFiringLastTick)
			{
				tickCounter = safePhaseTicks;
			}
			else if (tickCounter > 0)
			{
				tickCounter--;
			}
		}

		if (currentlyFiring || currentlyWarning)
		{
			hasEverFired = true;
		}

		wasFiringLastTick = currentlyFiring;
		wasWarningLastTick = currentlyWarning;
		ticksSinceSpawn++;
	}

	/**
	 * How many ticks the fire phase actually lasts.
	 * <p>
	 * {@link #firePhaseTicks} is the first value displayed, not a duration: the counter is set to
	 * it and then decrements to 0 inclusive, so the phase is one tick longer than the field
	 * suggests. Adding the field rather than the duration leaves a one-tick stall at the
	 * warning-to-fire handover.
	 */
	private int firePhaseDuration()
	{
		return firePhaseTicks + 1;
	}

	/**
	 * Ticks until this pillar is safe to walk through, counted continuously across the warning
	 * and fire phases. 0 is the final dangerous tick, matching the knight statues.
	 * <p>
	 * {@link #tickCounter} restarts at every animation change, so on its own it counts down to
	 * the start of the fire phase rather than to the tile clearing - reaching 0 during the
	 * warning means fire begins next tick, not that it is safe. Folding the fire phase's
	 * duration into the warning phase gives a single countdown that always answers
	 * "when can I run?".
	 */
	public int getTicksUntilSafe()
	{
		if (tickCounter < 0)
		{
			return -1;
		}

		// Once firing, the counter is already counting down to the tile clearing.
		if (isFiring())
		{
			return tickCounter;
		}

		// firePhaseTicks varies by floor, so read it rather than assuming a fixed length.
		if (isWarning())
		{
			return tickCounter + firePhaseDuration();
		}

		return tickCounter;
	}

	public String getDisplayTicks()
	{
		int ticksUntilSafe = getTicksUntilSafe();
		if (ticksUntilSafe < 0)
		{
			return "?";
		}
		return String.valueOf(ticksUntilSafe);
	}
}
