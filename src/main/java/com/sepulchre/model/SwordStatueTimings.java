package com.sepulchre.model;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * Cycle lengths measured per knight statue, kept outside the statues themselves.
 * <p>
 * A {@link SwordStatue} is rebuilt from scratch every time its game object respawns, which happens
 * on every scene reload - including loading lines partway through a floor. Holding the measurement
 * on the statue meant it was thrown away and had to be relearned constantly, so a countdown rarely
 * survived long enough to be shown. Keying it by the statue's tile instead lets a measurement
 * outlive the object it came from.
 * <p>
 * The shortest value seen at a tile wins. A statue observed only partway through a phase reports
 * less than the real length, and of the two possible errors that is the safe one: too short leaves
 * the countdown sitting at zero while the statue is still safe, whereas too long would leave it
 * reading above zero at the moment the sword swings.
 */
@Slf4j
public class SwordStatueTimings
{
	private final Map<WorldPoint, Integer> safeWindows = new HashMap<>();
	private final Map<WorldPoint, Integer> windups = new HashMap<>();

	public void reset()
	{
		safeWindows.clear();
		windups.clear();
	}

	public int getSafeWindow(WorldPoint statue)
	{
		return safeWindows.getOrDefault(statue, -1);
	}

	public int getWindup(WorldPoint statue)
	{
		return windups.getOrDefault(statue, -1);
	}

	public void recordSafeWindow(WorldPoint statue, int ticks, int reference)
	{
		record(safeWindows, statue, ticks, reference, "safe window");
	}

	public void recordWindup(WorldPoint statue, int ticks, int reference)
	{
		record(windups, statue, ticks, reference, "windup");
	}

	private void record(Map<WorldPoint, Integer> into, WorldPoint statue, int ticks, int reference, String phase)
	{
		Integer known = into.get(statue);
		if (known != null && ticks >= known)
		{
			return;
		}

		into.put(statue, ticks);
		log.debug("Knight statue {},{},{} {}: measured {} ticks, reference {}, delta {}{}",
			statue.getX(), statue.getY(), statue.getPlane(), phase, ticks, reference, ticks - reference,
			known == null ? "" : " (shorter than " + known + ")");
	}
}
