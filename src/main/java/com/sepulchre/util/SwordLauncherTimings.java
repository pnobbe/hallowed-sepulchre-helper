package com.sepulchre.util;

import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * Known cycle timings for thrown swords, so a countdown can be shown without first watching a
 * full throw, plus a readable name per corridor.
 * <p>
 * Neither timing can be derived from the sword itself. Across many throws a sword's speed is fixed
 * by its NPC id, but the hold and the gap are properties of the individual launcher, unrelated to
 * the sword type or to how far it travels - the same launcher produced holds of 3 and of 8 from an
 * identical outbound distance.
 * <p>
 * They are not independent of each other, however: on all 16 corridors measured,
 * {@code gap == hold + 2}. See {@link #GAP_MINUS_HOLD}.
 * <p>
 * Three tiles per corridor, because each value belongs to a different part of it:
 * <ul>
 *   <li>the name is keyed by the sword statue itself;</li>
 *   <li>the gap tile is where the sword is thrown from, three tiles out from the statue;</li>
 *   <li>the hold tile is where it stops at the far end.</li>
 * </ul>
 * Positions are canonical, as produced by WorldPoint.fromLocalInstance, so they survive the
 * Sepulchre being instanced. Names come from matching each corridor to the nearest sword statue in
 * {@link RouteObstacles} along the corridor's own axis. Anything missing is still measured at
 * runtime, and a runtime measurement wins over this table.
 */
public final class SwordLauncherTimings
{
	private SwordLauncherTimings()
	{
	}

	/**
	 * Observed difference between a corridor's gap and its hold, identical on all 16 corridors
	 * measured. Lets either value stand in for the other when only one has been seen.
	 */
	public static final int GAP_MINUS_HOLD = 2;

	/** Ticks a sword hangs at its furthest point, keyed by that far tile. */
	private static final Map<WorldPoint, Integer> HOLD = new HashMap<>();

	/** Ticks from a sword finishing its return to the next being thrown, keyed by the throw tile. */
	private static final Map<WorldPoint, Integer> GAP = new HashMap<>();

	/** Route name, keyed by the sword statue's own tile. */
	private static final Map<WorldPoint, String> NAME = new HashMap<>();

	private static void addHold(int x, int y, int plane, int ticks)
	{
		HOLD.put(new WorldPoint(x, y, plane), ticks);
	}

	private static void addGap(int x, int y, int plane, int ticks)
	{
		GAP.put(new WorldPoint(x, y, plane), ticks);
	}

	private static void addName(int x, int y, int plane, String name)
	{
		NAME.put(new WorldPoint(x, y, plane), name);
	}

	static
	{
		// Floor 1 south-east sword 1
		addName(2284, 5953, 2, "Floor 1 south-east sword 1");
		addGap(2281, 5953, 2, 5);
		addHold(2260, 5953, 2, 3);
		// Floor 1 south-west sword 1
		addName(2238, 5993, 2, "Floor 1 south-west sword 1");
		addGap(2238, 5996, 2, 5);
		addHold(2238, 6008, 2, 3);
		// Floor 2 north sword 1
		addName(2532, 6013, 2, "Floor 2 north sword 1");
		addGap(2535, 6013, 2, 5);
		addHold(2550, 6013, 2, 3);
		// Floor 2 south sword 1
		addName(2506, 5963, 2, "Floor 2 south sword 1");
		addGap(2503, 5963, 2, 5);
		addHold(2488, 5963, 2, 3);
		// Floor 2 west sword 1
		addName(2493, 5984, 1, "Floor 2 west sword 1");
		addGap(2496, 5984, 1, 5);
		addHold(2511, 5984, 1, 3);
		// Floor 2 west sword 2
		addName(2511, 5984, 2, "Floor 2 west sword 2");
		addGap(2511, 5987, 2, 6);
		addHold(2511, 5999, 2, 4);
		// Floor 3 east sword 1
		addName(2412, 5880, 2, "Floor 3 east sword 1");
		addGap(2415, 5880, 2, 7);
		addHold(2433, 5880, 2, 5);
		// Floor 3 east sword 2
		addName(2419, 5836, 2, "Floor 3 east sword 2");
		addGap(2419, 5839, 2, 7);
		addHold(2419, 5851, 2, 5);
		// Floor 3 west sword 1
		addName(2370, 5837, 2, "Floor 3 west sword 1");
		addGap(2373, 5837, 2, 6);
		addHold(2391, 5837, 2, 4);
		// Floor 3 west sword 2
		addName(2370, 5885, 2, "Floor 3 west sword 2");
		addGap(2370, 5882, 2, 7);
		addHold(2370, 5867, 2, 5);
		// Floor 4 north sword 1
		addName(2553, 5866, 1, "Floor 4 north sword 1");
		addGap(2553, 5863, 1, 12);
		addHold(2553, 5842, 1, 10);
		// Floor 4 south sword 1
		addName(2508, 5839, 2, "Floor 4 south sword 1");
		addGap(2508, 5836, 2, 10);
		// Floor 4 south sword 2
		addName(2514, 5839, 1, "Floor 4 south sword 2");
		addGap(2514, 5842, 1, 10);
		addHold(2514, 5872, 1, 8);
		// Floor 5 sword 1
		addName(2244, 5881, 2, "Floor 5 sword 1");
		addGap(2244, 5878, 2, 7);
		addHold(2244, 5846, 2, 5);
		// Floor 5 sword 2
		addName(2300, 5882, 1, "Floor 5 sword 2");
		addGap(2300, 5879, 1, 6);
		addHold(2300, 5846, 1, 4);
		// Unnamed corridor: no sword statue recorded for it in RouteObstacles, but both ends were
		// measured and they obey the same gap == hold + 2 relationship as every named corridor.
		addGap(2504, 5865, 2, 6);
		addHold(2504, 5886, 2, 4);
	}

	/** @return known hold in ticks, or -1 if this tile has not been recorded */
	public static int getHold(WorldPoint farTile)
	{
		return HOLD.getOrDefault(farTile, -1);
	}

	/** @return known gap in ticks, or -1 if this tile has not been recorded */
	public static int getGap(WorldPoint throwTile)
	{
		return GAP.getOrDefault(throwTile, -1);
	}

	/** @return route name for the statue on this tile, or null if it is not recorded */
	public static String getName(WorldPoint statue)
	{
		return NAME.get(statue);
	}
}
