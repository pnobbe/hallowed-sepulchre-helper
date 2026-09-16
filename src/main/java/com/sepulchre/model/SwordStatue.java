package com.sepulchre.model;

import com.sepulchre.util.GameObjectUtil;
import com.sepulchre.util.SepulchreConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

import java.util.Set;


@Slf4j
@Getter
public class SwordStatue
{
	private final GameObject gameObject;
	private final WorldPoint location;

	/**
	 * Which windup animation this statue uses. The other variant is a "ready" pose it can hold
	 * for an arbitrary number of ticks while waiting, and is not dangerous.
	 */
	private final int windup1Anim;

	private static final Set<Integer> SPECIAL_STATUE_IDS = Set.of(38428, 38432, 38433, 38434, 38435, 38436, 38440, 38441, 38442, 38443);

	private boolean wasInDangerousState = false;
	private int cachedAnimationId = -1;

	/*
	 * Unlike the wizard statues there is no known length for the orange warning phase, and the
	 * statues cycle continuously, so rather than hardcode a guess it is measured from the first
	 * completed cycle and used to drive the countdown on every cycle after that.
	 */
	/*
	 * The gap between the sword finishing its swing and becoming dangerous again - the window
	 * you can cross in on the return leg. Measured the same way as the warning phase, since its
	 * length is not known up front either.
	 */
	private int idleTicks = 0;
	private boolean wasDangerousLastTick = false;

	/*
	 * Windup is measured as well as the safe window, because statues are not assumed to share a
	 * cycle. Both are reported against the cycle captured from a floor 1 statue so any statue
	 * that differs can be identified and corrected individually.
	 */
	private int windupTicks = 0;
	private boolean sawSafeStart = false;
	private boolean sawWindupStart = false;

	/** Shared across statue instances so a measurement outlives a scene reload. */
	private final SwordStatueTimings timings;

	/** Safe window of the reference cycle: 8669 rest (15) + 8670 recover (1) + idle (1). */
	private static final int REFERENCE_SAFE_WINDOW = 17;

	/** Windup of the reference cycle: 8665 then 8667. */
	private static final int REFERENCE_WINDUP = 2;

	public SwordStatue(GameObject gameObject, SwordStatueTimings timings)
	{
		this.timings = timings;
		this.gameObject = gameObject;
		this.location = gameObject.getWorldLocation();
		this.windup1Anim = SPECIAL_STATUE_IDS.contains(gameObject.getId())
			? SepulchreConstants.SWORD_STATUE_ANIM_WINDUP_1B
			: SepulchreConstants.SWORD_STATUE_ANIM_WINDUP_1;

		cachedAnimationId = GameObjectUtil.getAnimationId(gameObject);
		if (SepulchreConstants.SWORD_STATUE_DANGER_ANIMS.contains(cachedAnimationId))
		{
			wasInDangerousState = true;
		}
	}

	public WorldPoint getDangerZoneCenter()
	{
		int orientation = gameObject.getOrientation();
		int dx = 0;
		int dy = 0;

		if (orientation >= 256 && orientation < 768)
		{
			dx = -2;
		}
		else if (orientation >= 768 && orientation < 1280)
		{
			dy = 2;
		}
		else if (orientation >= 1280 && orientation < 1792)
		{
			dx = 2;
		}
		else
		{
			dy = -2;
		}

		return new WorldPoint(
			location.getX() + dx,
			location.getY() + dy,
			location.getPlane()
		);
	}

	public void onGameTick()
	{
		cachedAnimationId = GameObjectUtil.getAnimationId(gameObject);

		if (cachedAnimationId == SepulchreConstants.SWORD_STATUE_ANIM_STRIKE)
		{
			wasInDangerousState = false;
		}
		else if (cachedAnimationId != -1)
		{
			wasInDangerousState = isDangerAnimation(cachedAnimationId);
		}
		// An id of -1 keeps the previous state: the statue reports no animation on some ticks of
		// the warning phase, which must not be mistaken for the danger ending.

		updateWarningTiming();
	}

	/** True on the tick the sword actually swings, which is neither a warning nor safe. */
	public boolean isSwinging()
	{
		return cachedAnimationId == SepulchreConstants.SWORD_STATUE_ANIM_STRIKE;
	}

	/**
	 * Measures the two phase lengths, recording only phases whose <em>start</em> was observed.
	 * <p>
	 * A statue is rebuilt whenever its game object respawns, so it frequently begins life partway
	 * through a phase. Counting from that moment produces a value far short of the real one, and
	 * since the shortest measurement is the one kept, a single partial reading would poison the
	 * countdown for the rest of the session. Only a phase seen from its opening transition counts.
	 */
	private void updateWarningTiming()
	{
		boolean dangerous = isInDangerousState();

		if (dangerous && !wasDangerousLastTick)
		{
			if (sawSafeStart && idleTicks > 0)
			{
				timings.recordSafeWindow(location, idleTicks, REFERENCE_SAFE_WINDOW);
			}
			sawSafeStart = false;
			sawWindupStart = true;
			windupTicks = 0;
		}
		else if (!dangerous && wasDangerousLastTick)
		{
			if (sawWindupStart && windupTicks > 0)
			{
				timings.recordWindup(location, windupTicks, REFERENCE_WINDUP);
			}
			sawWindupStart = false;
			sawSafeStart = true;
			idleTicks = 0;
		}

		if (dangerous)
		{
			windupTicks++;
		}
		else if (!isSwinging())
		{
			// The swing is neither safe nor a windup, so it belongs to neither measurement.
			idleTicks++;
		}

		wasDangerousLastTick = dangerous;
	}

	/**
	 * Ticks left before this statue becomes dangerous again, or null while safe until a full
	 * cycle has been observed. 0 is the last safe tick.
	 */
	public String getSafeWindowDisplayTicks()
	{
		int safeWindow = timings.getSafeWindow(location);
		if (isInDangerousState() || isSwinging() || safeWindow <= 0)
		{
			return null;
		}
		return String.valueOf(Math.max(0, safeWindow - idleTicks));
	}

	public boolean isInDangerousState()
	{
		if (cachedAnimationId == SepulchreConstants.SWORD_STATUE_ANIM_STRIKE)
		{
			return false;
		}

		return wasInDangerousState;
	}

	/**
	 * Every windup tick is imminent. Previously only the variant matching this statue's id
	 * counted, so a statue playing the other variant showed as a warning with no countdown.
	 */
	public boolean isInImminentDangerState()
	{
		return isDangerAnimation(cachedAnimationId);
	}

	/**
	 * Only this statue's own windup pair is dangerous. The other variant's windup id is the
	 * ready pose, which it can sit in indefinitely without the sword moving.
	 */
	private boolean isDangerAnimation(int animationId)
	{
		return animationId == windup1Anim
			|| animationId == SepulchreConstants.SWORD_STATUE_ANIM_WINDUP_2;
	}

	/**
	 * Ticks until this statue swings, counted continuously through the warning and imminent
	 * phases so the number never restarts mid-danger. 0 is the final tick before the swing.
	 * <p>
	 * Returns null during the warning phase until a full cycle has been observed, so that no
	 * number is shown rather than a guessed one.
	 */
	public String getDisplayTicks()
	{
		if (cachedAnimationId == windup1Anim)
		{
			return "1";
		}
		if (cachedAnimationId == SepulchreConstants.SWORD_STATUE_ANIM_WINDUP_2)
		{
			return "0";
		}

		return null;
	}
}
