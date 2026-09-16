package com.sepulchre.handler;

import com.sepulchre.util.SepulchreConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Turns the game's own Sepulchre vars into per-tick edges.
 * <p>
 * The plugin's existing detection is driven by chat messages, which are easy to miss - they can
 * be filtered, lost in spam, or simply never seen because the player relogged. These vars carry
 * the same information as durable server state that can be re-read at any time, so they are used
 * to corroborate the chat triggers rather than replace them.
 * <p>
 * Note what these vars do <em>not</em> tell us: the loot vars say a coffin on this floor was
 * looted, not which one, so the existing proximity-based attribution is still required.
 */
@Slf4j
public class SepulchreVarTracker
{
	private static final int UNINITIALISED = -1;

	private final Client client;

	private int previousBlockClimbover = UNINITIALISED;
	private int previousFloorLooted = UNINITIALISED;
	private int previousFloorLootedB = UNINITIALISED;
	private int previousFloor5Grapple = UNINITIALISED;
	private int previousFloor5Portal = UNINITIALISED;
	private int previousFloor5Bridge = UNINITIALISED;
	private int previousFinalFloorLooted = UNINITIALISED;
	private int previousAshesOffered = UNINITIALISED;

	/** A floor boundary was crossed this tick. Also true when leaving the Sepulchre. */
	@Getter
	private boolean floorBoundaryCrossed;

	/** A coffin on the current floor was looted this tick. */
	@Getter
	private boolean coffinLooted;

	/** Ashes were offered at a brazier this tick. */
	@Getter
	private boolean brazierSacrificed;

	public SepulchreVarTracker(Client client)
	{
		this.client = client;
	}

	public void reset()
	{
		previousBlockClimbover = UNINITIALISED;
		previousFloorLooted = UNINITIALISED;
		previousFloorLootedB = UNINITIALISED;
		previousFloor5Grapple = UNINITIALISED;
		previousFloor5Portal = UNINITIALISED;
		previousFloor5Bridge = UNINITIALISED;
		previousFinalFloorLooted = UNINITIALISED;
		previousAshesOffered = UNINITIALISED;
		clearEdges();
	}

	private void clearEdges()
	{
		floorBoundaryCrossed = false;
		coffinLooted = false;
		brazierSacrificed = false;
	}

	public void onGameTick()
	{
		clearEdges();

		int blockClimbover = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_BLOCK_CLIMBOVER);
		int floorLooted = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED);
		int floorLootedB = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED_B);
		int floor5Grapple = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_FLOOR5_GRAPPLE_LOOTED);
		int floor5Portal = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_FLOOR5_PORTAL_LOOTED);
		int floor5Bridge = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_FLOOR5_BRIDGE_LOOTED);
		int finalFloorLooted = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_FINAL_FLOOR_LOOTED);
		int ashesOffered = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_PRAYER_ASHES_OFFERED);

		// Climbing sets the var and arriving clears it, so the falling edge is the arrival.
		if (fell(previousBlockClimbover, blockClimbover))
		{
			floorBoundaryCrossed = true;
			log.debug("Floor boundary crossed (BLOCK_CLIMBOVER fell)");
		}

		// Each of these resets to 0 on floor entry; only the rising edge is a loot event.
		if (rose(previousFloorLooted, floorLooted)
			|| rose(previousFloorLootedB, floorLootedB)
			|| rose(previousFloor5Grapple, floor5Grapple)
			|| rose(previousFloor5Portal, floor5Portal)
			|| rose(previousFloor5Bridge, floor5Bridge)
			|| rose(previousFinalFloorLooted, finalFloorLooted))
		{
			coffinLooted = true;
			log.debug("Coffin looted (loot varbit rose)");
		}

		if (rose(previousAshesOffered, ashesOffered))
		{
			brazierSacrificed = true;
			log.debug("Brazier sacrificed (PRAYER_ASHES_OFFERED rose)");
		}

		previousBlockClimbover = blockClimbover;
		previousFloorLooted = floorLooted;
		previousFloorLootedB = floorLootedB;
		previousFloor5Grapple = floor5Grapple;
		previousFloor5Portal = floor5Portal;
		previousFloor5Bridge = floor5Bridge;
		previousFinalFloorLooted = finalFloorLooted;
		previousAshesOffered = ashesOffered;
	}

	/**
	 * True if the current floor's coffin has been looted, regardless of when. Unlike the chat
	 * message this survives relogging, because it is read from server state every tick.
	 * <p>
	 * Must be called on the client thread; reading vars asserts it.
	 */
	public boolean isCurrentFloorCoffinLooted()
	{
		return client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED) > 0
			|| client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_CURRENT_FLOOR_LOOTED_B) > 0;
	}

	/**
	 * Whether the given per-skill state varbit reports its obstacle as completed this floor.
	 * Must be called on the client thread; reading vars asserts it.
	 */
	public boolean isSkillObstacleCompleted(int stateVarbit)
	{
		return client.getVarbitValue(stateVarbit) == SepulchreConstants.SKILL_OBSTACLE_COMPLETED;
	}

	/**
	 * An edge is only meaningful once a previous value has been seen; the first sample after a
	 * reset establishes the baseline instead of firing.
	 */
	private static boolean rose(int previous, int current)
	{
		return previous != UNINITIALISED && current > previous;
	}

	private static boolean fell(int previous, int current)
	{
		return previous != UNINITIALISED && current < previous;
	}
}
