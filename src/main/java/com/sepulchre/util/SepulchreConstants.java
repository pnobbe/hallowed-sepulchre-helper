package com.sepulchre.util;

import com.sepulchre.model.SepulchreRoute;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SepulchreConstants
{
	private SepulchreConstants()
	{
	}

	public static final int TIMER_WIDGET_GROUP = 668;
	public static final int TIMER_WIDGET_CHILD = 4;

	public static final int VARBIT_HALLOWED_TIME_SPENT = VarbitID.HALLOWED_TIME_SPENT;
	public static final int VARBIT_HALLOWED_CURRENT_FLOOR_TIME_SPENT = VarbitID.HALLOWED_CURRENT_FLOOR_TIME_SPENT;

	/**
	 * Ticks left before the current floor's door shuts. This is {@code deadline - TIME_SPENT},
	 * where each floor adds 200 ticks to the deadline, so it is not a plain countdown:
	 * <ul>
	 *   <li>{@code 0} - no run in progress (lobby).</li>
	 *   <li>{@code 1} - the run clock has stopped; the door has closed.</li>
	 *   <li>{@code >1} - ticks actually remaining.</li>
	 * </ul>
	 */
	public static final int VARBIT_HALLOWED_TIME_REMAINING = VarbitID.HALLOWED_TIME_REMAINING;

	/** Value of {@link #VARBIT_HALLOWED_TIME_REMAINING} once the floor's time has run out. */
	public static final int TIME_REMAINING_EXPIRED = 1;

	/**
	 * Set while climbing between floors and cleared on arrival, so its falling edge marks a
	 * floor boundary being crossed. Note it also fires when leaving the Sepulchre entirely,
	 * so it means "boundary crossed", not "advanced a floor".
	 */
	public static final int VARBIT_HALLOWED_BLOCK_CLIMBOVER = VarbitID.HALLOWED_BLOCK_CLIMBOVER;

	/**
	 * Whether the current floor's coffin has been looted. The game alternates between these two
	 * varbits on consecutive floors and resets them on floor entry, so the current floor's state
	 * is the union of the pair. Neither identifies <em>which</em> coffin was looted.
	 */
	public static final int VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED = VarbitID.HALLOWED_CURRENT_FLOOR_LOOTED;
	public static final int VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED_B = VarbitID.HALLOWED_CURRENT_FLOOR_LOOTED_B;

	/** Floor 5 tracks its coffins individually rather than through the alternating pair above. */
	public static final int VARBIT_HALLOWED_FLOOR5_GRAPPLE_LOOTED = VarbitID.HALLOWED_FLOOR5_GRAPPLE_LOOTED;
	public static final int VARBIT_HALLOWED_FLOOR5_PORTAL_LOOTED = VarbitID.HALLOWED_FLOOR5_PORTAL_LOOTED;
	public static final int VARBIT_HALLOWED_FLOOR5_BRIDGE_LOOTED = VarbitID.HALLOWED_FLOOR5_BRIDGE_LOOTED;

	/** Rises when the Grand Coffin is looted; the floor-level loot varbits do not cover it. */
	public static final int VARBIT_HALLOWED_FINAL_FLOOR_LOOTED = VarbitID.HALLOWED_FINAL_FLOOR_LOOTED;

	/** Rises when ashes are offered at a brazier. */
	public static final int VARBIT_HALLOWED_PRAYER_ASHES_OFFERED = VarbitID.HALLOWED_PRAYER_ASHES_OFFERED;

	/**
	 * Per-floor skill obstacle progress, reset on floor entry. Observed values: 1 once the
	 * obstacle is available and 2 once it has been completed. A value of 0 appears to mean the
	 * obstacle is not on this floor, but that reading is from a single run and is not relied on.
	 */
	public static final int VARBIT_HALLOWED_RANGED_STATE = VarbitID.HALLOWED_RANGED_STATE;
	public static final int VARBIT_HALLOWED_PRAYER_STATE = VarbitID.HALLOWED_PRAYER_STATE;
	public static final int VARBIT_HALLOWED_CONSTRUCTION_STATE = VarbitID.HALLOWED_CONSTRUCTION_STATE;
	public static final int VARBIT_HALLOWED_MAGIC_STATE = VarbitID.HALLOWED_MAGIC_STATE;

	/** Value of the per-skill state varbits once that obstacle has been completed. */
	public static final int SKILL_OBSTACLE_COMPLETED = 2;

	public static final int[] FLOOR_AGILITY_REQUIREMENTS = {52, 62, 72, 82, 92};

	private static final Map<Integer, Map<WorldPoint, SepulchreRoute>> SPAWN_TILES_BY_FLOOR = new HashMap<>();
	static
	{
		Map<WorldPoint, SepulchreRoute> floor1 = new HashMap<>();
		floor1.put(new WorldPoint(2253, 6018, 2), SepulchreRoute.FLOOR_1_NORTHWEST);
		floor1.put(new WorldPoint(2309, 6011, 2), SepulchreRoute.FLOOR_1_NORTHEAST);
		floor1.put(new WorldPoint(2293, 5949, 2), SepulchreRoute.FLOOR_1_SOUTHEAST);
		floor1.put(new WorldPoint(2234, 5960, 2), SepulchreRoute.FLOOR_1_SOUTHWEST);
		SPAWN_TILES_BY_FLOOR.put(1, floor1);

		Map<WorldPoint, SepulchreRoute> floor2 = new HashMap<>();
		floor2.put(new WorldPoint(2528, 5988, 2), SepulchreRoute.FLOOR_2_NORTH);
		floor2.put(new WorldPoint(2532, 5984, 2), SepulchreRoute.FLOOR_2_EAST);
		floor2.put(new WorldPoint(2528, 5980, 2), SepulchreRoute.FLOOR_2_SOUTH);
		floor2.put(new WorldPoint(2524, 5984, 2), SepulchreRoute.FLOOR_2_WEST);
		SPAWN_TILES_BY_FLOOR.put(2, floor2);

		Map<WorldPoint, SepulchreRoute> floor3 = new HashMap<>();
		floor3.put(new WorldPoint(2404, 5856, 2), SepulchreRoute.FLOOR_3_EAST);
		floor3.put(new WorldPoint(2396, 5856, 2), SepulchreRoute.FLOOR_3_WEST);
		SPAWN_TILES_BY_FLOOR.put(3, floor3);

		Map<WorldPoint, SepulchreRoute> floor4 = new HashMap<>();
		floor4.put(new WorldPoint(2528, 5860, 2), SepulchreRoute.FLOOR_4_NORTH);
		floor4.put(new WorldPoint(2528, 5852, 2), SepulchreRoute.FLOOR_4_SOUTH);
		SPAWN_TILES_BY_FLOOR.put(4, floor4);

		SPAWN_TILES_BY_FLOOR.put(5, new HashMap<>());
	}

	public static Map<WorldPoint, SepulchreRoute> getSpawnTilesForFloor(int floor)
	{
		return SPAWN_TILES_BY_FLOOR.get(floor);
	}

	public static final String FLOOR_1_MESSAGE = "You venture down into the Hallowed Sepulchre";
	public static final String FLOOR_CHANGE_MESSAGE = "You venture further down into the Hallowed Sepulchre";
	public static final String BRIDGE_BUILT_MESSAGE = "You repair the broken bridge";
	public static final String BRIDGE_CROSSED_MESSAGE = "You rapidly make your way across the bridge";
	public static final String GRAPPLE_USED_MESSAGE = "and swing safely to the other side";
	public static final String PORTAL_USED_MESSAGE = "You pass through the portal and end up on the other side";
	public static final String BRAZIER_SACRIFICED_MESSAGE = "You see the flame change as your offerings are accepted";
	public static final String COFFIN_LOOTED_MESSAGE = "You push the coffin lid aside";

	public static final Set<Integer> CROSSBOW_STATUE_IDS = Set.of(38444, 38445, 38446);
	public static final Set<Integer> CROSSBOW_DANGER_ANIMS = Set.of(8682, 8683, 8684, 8685);
	public static final Set<Integer> BOLT_NULL_NPC_IDS = Set.of(9672, 9673, 9674);

	public static final Set<Integer> WIZARD_FLAME_OBJECT_IDS = Set.of(
		38409, 38410, 38411, 38412, 38413, 38414, 38415,
		38416, 38417, 38418, 38419, 38420,
		38421, 38422, 38423, 38424, 38425
	);
	public static final int WIZARD_ANIM_FIRE = 8658;
	public static final int WIZARD_ANIM_WARNING = 8657;
	public static final int WIZARD_ANIM_PRE_WARNING = 8656;

	public static final Set<Integer> SWORD_STATUE_IDS = Set.of(
		38428, 38429, 38430, 38431, 38432, 38433, 38434, 38435,
		38436, 38437, 38438, 38439, 38440, 38441, 38442, 38443
	);
	/*
	 * Observed cycle, 20 ticks, from logging a statue over several swings:
	 *   8665 (1)  8667 (1)  8668 (1)  8669 (15)  8670 (1)  no animation (1)
	 * so 8669 is the long rest, not the swing, and 8668 is the strike. The recovery tail
	 * sometimes runs 8670 straight into the next windup without the idle tick, so the safe
	 * window is 16 or 17 ticks rather than a fixed number.
	 */
	public static final int SWORD_STATUE_ANIM_WINDUP_1 = 8665;
	public static final int SWORD_STATUE_ANIM_WINDUP_1B = 8666;
	public static final int SWORD_STATUE_ANIM_WINDUP_2 = 8667;

	/** The tick the sword actually strikes. */
	public static final int SWORD_STATUE_ANIM_STRIKE = 8668;

	/** Long rest animation between swings; previously mistaken for the strike. */
	public static final int SWORD_STATUE_ANIM_REST = 8669;

	/** Short recovery before the next windup. */
	public static final int SWORD_STATUE_ANIM_RECOVER = 8670;
	public static final Set<Integer> SWORD_STATUE_DANGER_ANIMS = Set.of(
		8670,
		SWORD_STATUE_ANIM_WINDUP_1,
		SWORD_STATUE_ANIM_WINDUP_1B,
		SWORD_STATUE_ANIM_WINDUP_2
	);
	public static final Set<Integer> SWORD_NULL_NPC_IDS = Set.of(9669, 9670, 9671);
	public static final int LIGHTNING_GRAPHICS_ID = 1796;
	public static final Set<Integer> BLUE_PORTAL_GRAPHICS_IDS = Set.of(1799, 1815);
	public static final Set<Integer> YELLOW_PORTAL_GRAPHICS_IDS = Set.of(1800, 1816);
	public static final int BLUE_PORTAL_TELEPORT_GRAPHICS_ID = 1803;
	public static final int YELLOW_PORTAL_TELEPORT_GRAPHICS_ID = 1804;

	public static final Set<Integer> COFFIN_OBJECT_IDS = Set.of(39544, 39545, 39536, 39537, 39538);
	public static final Set<Integer> COFFIN_MORPH_OPEN_IDS = Set.of(38831, 38833, 38835, 38837);
	public static final int GRAND_COFFIN_OBJECT_ID = 39539;
	public static final int GRAND_COFFIN_MORPH_OPEN = 38839;
	public static final Set<Integer> BRIDGE_OBJECT_IDS = Set.of(39527, 39528);
	public static final int GRAPPLE_OBJECT_ID = 39524;
	public static final int PORTAL_FRAME_OBJECT_ID = 39533;
	public static final Set<Integer> BRAZIER_OBJECT_IDS = Set.of(39525, 39526);
	public static final Set<Integer> BRAZIER_UNSACRIFICED_MORPHS = Set.of(38798, 38801);
	public static final int HOLY_BARRIER_OBJECT_ID = 39534;
	public static final int FLOOR_5_BARRIER_OBJECT_ID = 39540;
	public static final int MAGICAL_OBELISK_ID = 38451;

	public static final Set<Integer> STAIRS_IDS = Set.of(38453, 38454, 38462, 38463, 38464, 38465, 38466, 38467, 38468, 38469, 38471, 38472, 38473, 38474, 38475, 38476, 39622, 39623, 39624, 39625);
	public static final Set<Integer> END_FLOOR_STAIRS_IDS = Set.of(39622, 39623, 39624, 39625);

	public static final Set<Integer> ALL_PLATFORM_IDS = Set.of(38455, 38456, 38457, 38458, 38459, 38470, 38477);

	public static final int GATE_WALL_OBJECT_ID = 38460;
}
