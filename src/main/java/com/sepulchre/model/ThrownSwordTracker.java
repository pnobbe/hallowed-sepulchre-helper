package com.sepulchre.model;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import com.sepulchre.util.SwordLauncherTimings;
import net.runelite.api.coords.WorldPoint;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks thrown swords so the pause at the end of a throw can be counted down.
 * <p>
 * A sword travels out at a fixed speed, holds still at its furthest tile, then returns along the
 * same line. Observation across many throws showed the hold is a property of the launcher rather
 * than of the sword: every throw that ended on a given tile held there for the same number of
 * ticks, while different tiles ranged from 3 to 10. So the hold is measured per tile and reused
 * for every later sword that stops there, including swords that did not exist when it was learnt.
 * <p>
 * Measurements are keyed by canonical position, so they stay valid across floors and runs and can
 * be written down as constants; see {@link com.sepulchre.util.SwordLauncherTimings}. Drawing needs
 * the instanced tile instead, which is tracked alongside.
 */
@Slf4j
public class ThrownSwordTracker
{
	/**
	 * The statue's windup, measured at a consistent 2 ticks. The statue shows its own red
	 * countdown over those ticks, so the throw countdown stops beforehand rather than drawing a
	 * second number saying the same thing in the same place.
	 */
	private static final int WINDUP_TICKS = 2;

	private final Client client;

	public ThrownSwordTracker(Client client)
	{
		this.client = client;
	}

	/** Where each live sword was last tick, keyed by NPC index. */
	private final Map<Integer, WorldPoint> lastPosition = new HashMap<>();

	/** How many consecutive ticks each live sword has been stationary. */
	private final Map<Integer, Integer> dwellTicks = new HashMap<>();

	/** Measured hold length per tile a sword has been seen to stop on and then leave. */
	private final Map<WorldPoint, Integer> measuredHold = new HashMap<>();

	/*
	 * The quiet gap between a sword finishing its return and the next one being thrown. Unlike
	 * the hold, which varies by a tick or two on some launchers, this was identical on every
	 * observed launcher across repeated throws, so it can be counted down confidently.
	 */
	private int tick;
	private final Map<Integer, WorldPoint> launcherOf = new HashMap<>();
	private final Map<Integer, Integer> lastSeenTick = new HashMap<>();
	private final Map<WorldPoint, Integer> lastDespawnTick = new HashMap<>();
	private final Map<WorldPoint, Integer> measuredGap = new HashMap<>();

	/**
	 * Canonical launcher tile to where it currently sits in the loaded instance. Measurements are
	 * keyed canonically so they survive across runs, but drawing needs the instanced tile, which
	 * is the only one the scene can turn into a screen position.
	 */
	private final Map<WorldPoint, WorldPoint> launcherInstanceTile = new HashMap<>();

	/**
	 * Swords seen to move at least once, and therefore seen to arrive on the tile they now sit
	 * on. A sword first sighted already stationary has an unknown amount of hold behind it, so
	 * its hold must not be recorded - kept shortest, one such reading would poison the tile.
	 */
	private final Set<Integer> arrivalObserved = new HashSet<>();

	/** Launchers already reported as counting down, so each quiet gap logs once. */
	private final Set<WorldPoint> reportedQuiet = new HashSet<>();

	public void reset()
	{
		lastPosition.clear();
		dwellTicks.clear();
		measuredHold.clear();
		launcherOf.clear();
		lastSeenTick.clear();
		lastDespawnTick.clear();
		measuredGap.clear();
		launcherInstanceTile.clear();
		arrivalObserved.clear();
		reportedQuiet.clear();
		tick = 0;
	}

	public void onGameTick(Collection<NPC> swords)
	{
		tick++;
		Set<Integer> alive = new HashSet<>();

		for (NPC sword : swords)
		{
			if (sword == null)
			{
				continue;
			}

			// Canonical rather than instanced coordinates: the instance ones differ every run, so
			// anything keyed by them can never be reused, let alone written down as a constant.
			WorldPoint at = WorldPoint.fromLocalInstance(client, sword.getLocalLocation());
			if (at == null)
			{
				continue;
			}

			int index = sword.getIndex();
			alive.add(index);

			if (!launcherOf.containsKey(index))
			{
				// First sighting: this is where the launcher throws from.
				launcherOf.put(index, at);
				launcherInstanceTile.put(at, sword.getWorldLocation());
				Integer despawned = lastDespawnTick.get(at);
				if (despawned != null)
				{
					// Keep the shortest gap seen. Walking away and coming back measures an
					// interval spanning the absence, which would otherwise leave the countdown
					// reading high at the moment the next sword appears.
					int gap = tick - despawned;
					Integer known = measuredGap.get(at);
					if (known == null || gap < known)
					{
						measuredGap.put(at, gap);
						log.debug("Sword gap at {},{},{}: {} ticks{}", at.getX(), at.getY(), at.getPlane(),
							gap, known == null ? "" : " (shorter than " + known + ")");
					}
				}
			}
			lastSeenTick.put(index, tick);

			WorldPoint previous = lastPosition.get(index);
			int dwell = dwellTicks.getOrDefault(index, 0);

			if (at.equals(previous))
			{
				dwell++;
			}
			else
			{
				// The sword moved off a tile it had been sitting on, so that hold is now known.
				// A sword that despawns without moving never reaches here, which is what keeps
				// the one-tick pause at the end of a throw from being recorded as a hold.
				if (previous != null && dwell > 0 && arrivalObserved.contains(index))
				{
					Integer known = measuredHold.get(previous);
					// Keep the shortest hold ever seen here. Most launchers are consistent, but a
					// few vary by a tick or two, and the two errors are not equally bad: too long
					// and the countdown still reads above zero as the sword starts moving, which
					// is the reading that gets you hit. Too short and it sits at zero a moment
					// early, which only costs a tick of waiting.
					if (known == null || dwell < known)
					{
						measuredHold.put(previous, dwell);
						log.debug("Sword hold at {},{},{}: {} ticks{}",
							previous.getX(), previous.getY(), previous.getPlane(), dwell,
							known == null ? "" : " (shorter than " + known + ")");
					}
					else if (dwell > known)
					{
						log.debug("Sword hold at {},{},{}: saw {} ticks, keeping shorter {}",
							previous.getX(), previous.getY(), previous.getPlane(), dwell, known);
					}
				}
				if (previous != null)
				{
					// We watched it step onto this tile, so the next hold here is measurable.
					arrivalObserved.add(index);
				}
				dwell = 0;
			}

			if (dwell == 1)
			{
				Integer known = measuredHold.get(at);
				log.debug("Sword {} holding at {},{},{}: {}", index, at.getX(), at.getY(), at.getPlane(),
					known == null ? "hold not yet known, no countdown" : "counting down from " + (known - 1));
			}

			lastPosition.put(index, at);
			dwellTicks.put(index, dwell);
		}

		// A sword that has gone is one that finished its return; note when its launcher went quiet.
		for (Map.Entry<Integer, WorldPoint> entry : launcherOf.entrySet())
		{
			if (!alive.contains(entry.getKey()))
			{
				Integer seen = lastSeenTick.get(entry.getKey());
				if (seen != null)
				{
					lastDespawnTick.put(entry.getValue(), seen);
				}
			}
		}

		lastPosition.keySet().retainAll(alive);
		dwellTicks.keySet().retainAll(alive);
		launcherOf.keySet().retainAll(alive);
		lastSeenTick.keySet().retainAll(alive);
		arrivalObserved.retainAll(alive);

		Map<WorldPoint, Integer> pending = getThrowCountdowns();
		reportedQuiet.retainAll(pending.keySet());
		for (Map.Entry<WorldPoint, Integer> entry : pending.entrySet())
		{
			if (reportedQuiet.add(entry.getKey()))
			{
				WorldPoint at = entry.getKey();
				log.debug("Throw countdown active at {},{},{}: starting from {}",
					at.getX(), at.getY(), at.getPlane(), entry.getValue());
			}
		}
	}

	/**
	 * Launchers that are currently between throws, mapped to the ticks left before the next
	 * sword appears. 0 is the last quiet tick.
	 * <p>
	 * This starts the moment the previous sword finishes its return, rather than waiting for the
	 * statue's windup animation, which is the part of the cycle the statue itself cannot show.
	 */
	public Map<WorldPoint, Integer> getThrowCountdowns()
	{
		if (lastDespawnTick.isEmpty())
		{
			return Collections.emptyMap();
		}

		Set<WorldPoint> busy = new HashSet<>(launcherOf.values());
		Map<WorldPoint, Integer> pending = new HashMap<>();

		// Driven by launchers that have gone quiet rather than by measurements, so a launcher
		// whose gap is already known from the table counts down without watching a cycle first.
		for (WorldPoint launcher : lastDespawnTick.keySet())
		{
			if (busy.contains(launcher))
			{
				// A sword is in flight from here; the sword's own indicators apply instead.
				continue;
			}

			Integer despawned = lastDespawnTick.get(launcher);
			if (despawned == null)
			{
				continue;
			}

			int gap = resolveGap(launcher);
			if (gap <= 0)
			{
				continue;
			}

			int remaining = gap - 1 - (tick - despawned);
			if (remaining >= WINDUP_TICKS)
			{
				WorldPoint drawAt = launcherInstanceTile.get(launcher);
				if (drawAt != null)
				{
					pending.put(drawAt, remaining);
				}
			}
		}

		return pending;
	}

	/**
	 * Runtime measurement wins over the table: it describes this launcher, here, now. Failing
	 * both, the hold is derived from the launcher's gap, which on every corridor measured runs
	 * exactly two ticks longer - so one end of a corridor being known is enough for both.
	 */
	private int resolveHold(WorldPoint tile, int swordIndex)
	{
		Integer measured = measuredHold.get(tile);
		if (measured != null)
		{
			return measured;
		}

		int known = SwordLauncherTimings.getHold(tile);
		if (known > 0)
		{
			return known;
		}

		WorldPoint launcher = launcherOf.get(swordIndex);
		if (launcher != null)
		{
			int gap = resolveGap(launcher);
			if (gap > SwordLauncherTimings.GAP_MINUS_HOLD)
			{
				return gap - SwordLauncherTimings.GAP_MINUS_HOLD;
			}
		}

		return -1;
	}

	private int resolveGap(WorldPoint launcher)
	{
		Integer measured = measuredGap.get(launcher);
		return measured != null ? measured : SwordLauncherTimings.getGap(launcher);
	}

	/**
	 * Ticks until this sword starts moving back, or -1 if it is travelling, or is holding on a
	 * tile whose hold length has not been observed yet. 0 is the last tick before it moves.
	 */
	public int getTicksUntilReturn(NPC sword)
	{
		if (sword == null)
		{
			return -1;
		}

		int dwell = dwellTicks.getOrDefault(sword.getIndex(), 0);
		if (dwell <= 0)
		{
			return -1;
		}

		WorldPoint at = lastPosition.get(sword.getIndex());
		if (at == null)
		{
			return -1;
		}

		int hold = resolveHold(at, sword.getIndex());
		if (hold <= 0)
		{
			return -1;
		}

		return Math.max(0, hold - dwell);
	}
}
