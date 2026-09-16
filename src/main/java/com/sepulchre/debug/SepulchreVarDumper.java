package com.sepulchre.debug;

import com.sepulchre.handler.ObstacleHandler;
import com.sepulchre.model.SwordStatue;
import net.runelite.api.NPC;
import com.sepulchre.util.GameObjectUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLite;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Development aid: records every change to the game's own Hallowed Sepulchre vars, so the values
 * the server actually tracks can be mapped to their in-game meaning.
 * <p>
 * This is a diagnostic tool, not a gameplay feature. It only observes state the client has already
 * been sent, and writes a tab-separated file to {@code ~/.runelite/sepulchre-vardump.tsv}.
 * Disabled unless the "Dump Sepulchre vars" config option is enabled.
 */
@Slf4j
public class SepulchreVarDumper
{
	public static final File DUMP_FILE = new File(RuneLite.RUNELITE_DIR, "sepulchre-vardump.tsv");

	/**
	 * The vars RuneLite already names for this minigame. Ordered roughly by how useful they look
	 * for replacing the plugin's own inference.
	 */
	private static final Map<String, Integer> WATCHED_VARBITS = new LinkedHashMap<>();

	static
	{
		// Run / floor progress
		WATCHED_VARBITS.put("CURRENT_FLOOR", VarbitID.HALLOWED_CURRENT_FLOOR);
		WATCHED_VARBITS.put("TIME_SPENT", VarbitID.HALLOWED_TIME_SPENT);
		WATCHED_VARBITS.put("CURRENT_FLOOR_TIME_SPENT", VarbitID.HALLOWED_CURRENT_FLOOR_TIME_SPENT);
		WATCHED_VARBITS.put("TIME_REMAINING", VarbitID.HALLOWED_TIME_REMAINING);
		WATCHED_VARBITS.put("ENTRY_CHECK", VarbitID.HALLOWED_ENTRY_CHECK);
		WATCHED_VARBITS.put("ALTERNATE_SAFESPOT", VarbitID.HALLOWED_ALTERNATE_SAFESPOT);
		WATCHED_VARBITS.put("BLOCK_CLIMBOVER", VarbitID.HALLOWED_BLOCK_CLIMBOVER);
		WATCHED_VARBITS.put("PRIVATE_INSTANCES", VarbitID.HALLOWED_PRIVATE_INSTANCES);

		// Looting state
		WATCHED_VARBITS.put("CURRENT_FLOOR_LOOTED", VarbitID.HALLOWED_CURRENT_FLOOR_LOOTED);
		WATCHED_VARBITS.put("CURRENT_FLOOR_LOOTED_B", VarbitID.HALLOWED_CURRENT_FLOOR_LOOTED_B);
		WATCHED_VARBITS.put("FINAL_FLOOR_LOOTED", VarbitID.HALLOWED_FINAL_FLOOR_LOOTED);
		WATCHED_VARBITS.put("NORMAL_CHEST_COUNT", VarbitID.HALLOWED_NORMAL_CHEST_COUNT);
		WATCHED_VARBITS.put("FINAL_CHEST_COUNT", VarbitID.HALLOWED_FINAL_CHEST_COUNT);
		WATCHED_VARBITS.put("FLOOR5_GRAPPLE_LOOTED", VarbitID.HALLOWED_FLOOR5_GRAPPLE_LOOTED);
		WATCHED_VARBITS.put("FLOOR5_PORTAL_LOOTED", VarbitID.HALLOWED_FLOOR5_PORTAL_LOOTED);
		WATCHED_VARBITS.put("FLOOR5_BRIDGE_LOOTED", VarbitID.HALLOWED_FLOOR5_BRIDGE_LOOTED);

		// Skill obstacle state
		WATCHED_VARBITS.put("RANGED_STATE", VarbitID.HALLOWED_RANGED_STATE);
		WATCHED_VARBITS.put("PRAYER_STATE", VarbitID.HALLOWED_PRAYER_STATE);
		WATCHED_VARBITS.put("CONSTRUCTION_STATE", VarbitID.HALLOWED_CONSTRUCTION_STATE);
		WATCHED_VARBITS.put("MAGIC_STATE", VarbitID.HALLOWED_MAGIC_STATE);
		WATCHED_VARBITS.put("PRAYER_ASHES_OFFERED", VarbitID.HALLOWED_PRAYER_ASHES_OFFERED);
		WATCHED_VARBITS.put("MAGIC_SPELL_FAILED", VarbitID.HALLOWED_MAGIC_SPELL_FAILED);
		WATCHED_VARBITS.put("GRAPPLE_TYPE", VarbitID.HALLOWED_GRAPPLE_TYPE);
	}

	/** Raw varps backing the above; logged so nothing is missed if a bit field is unnamed. */
	private static final Map<String, Integer> WATCHED_VARPS = new LinkedHashMap<>();

	static
	{
		WATCHED_VARPS.put("GENERAL", VarPlayerID.HALLOWED_GENERAL);
		WATCHED_VARPS.put("GENERAL_2", VarPlayerID.HALLOWED_GENERAL_2);
		WATCHED_VARPS.put("GENERAL_3", VarPlayerID.HALLOWED_GENERAL_3);
		WATCHED_VARPS.put("GENERAL_4", VarPlayerID.HALLOWED_GENERAL_4);
		WATCHED_VARPS.put("GENERAL_5", VarPlayerID.HALLOWED_GENERAL_5);
		WATCHED_VARPS.put("GENERAL_6", VarPlayerID.HALLOWED_GENERAL_6);
		WATCHED_VARPS.put("TREASURE", VarPlayerID.HALLOWED_TREASURE);
		WATCHED_VARPS.put("TOOLS", VarPlayerID.HALLOWED_TOOLS);
	}

	private final Client client;
	private final ObstacleHandler obstacleHandler;

	private final Map<String, Integer> previousValues = new LinkedHashMap<>();

	/** Per sword NPC: where it was last tick, and how long it has sat there. */
	private final Map<Integer, WorldPoint> swordNpcPositions = new HashMap<>();
	private final Map<Integer, Integer> swordNpcStillTicks = new HashMap<>();
	private BufferedWriter writer;
	private boolean active;
	private boolean baselinePending;
	private int tick;

	public SepulchreVarDumper(Client client, ObstacleHandler obstacleHandler)
	{
		this.client = client;
		this.obstacleHandler = obstacleHandler;
	}

	public void start()
	{
		if (active)
		{
			return;
		}

		try
		{
			writer = new BufferedWriter(new FileWriter(DUMP_FILE, true));
			writer.write("# session start " + System.currentTimeMillis() + System.lineSeparator());
			writer.write("tick\tvar\tid\told\tnew\tplane\tx\ty\tgameFloor\tpluginFloor\tpluginRoute" + System.lineSeparator());
			writer.flush();
			active = true;
			previousValues.clear();
			tick = 0;
			log.info("Sepulchre var dump started: {}", DUMP_FILE);
			// start() is called from the EDT (plugin startUp and config changes), but reading
			// vars asserts the client thread, so the opening baseline is deferred to the first
			// game tick rather than taken here.
			baselinePending = true;
		}
		catch (IOException e)
		{
			log.warn("Could not open Sepulchre var dump file", e);
			writer = null;
		}
	}

	public void stop()
	{
		active = false;
		baselinePending = false;
		previousValues.clear();
		if (writer != null)
		{
			try
			{
				writer.flush();
				writer.close();
			}
			catch (IOException e)
			{
				log.warn("Could not close Sepulchre var dump file", e);
			}
			writer = null;
		}
	}

	public void onGameTick()
	{
		if (!active)
		{
			return;
		}
		tick++;
		if (baselinePending)
		{
			baselinePending = false;
			sample(true);
			return;
		}
		sample(false);
	}

	private void sample(boolean baseline)
	{
		for (Map.Entry<String, Integer> entry : WATCHED_VARBITS.entrySet())
		{
			record("varbit:" + entry.getKey(), entry.getValue(), client.getVarbitValue(entry.getValue()), baseline);
		}
		for (Map.Entry<String, Integer> entry : WATCHED_VARPS.entrySet())
		{
			record("varp:" + entry.getKey(), entry.getValue(), client.getVarpValue(entry.getValue()), baseline);
		}
		sampleSwordStatues(baseline);
		sampleSwordNpcs();
		flush();
	}

	/**
	 * Knight statue animation ids and the states derived from them, per statue. Their cycle is
	 * driven entirely by animations rather than vars, so this is the only way to see it.
	 */
	private void sampleSwordStatues(boolean baseline)
	{
		for (SwordStatue statue : obstacleHandler.getSwordStatues())
		{
			WorldPoint at = statue.getLocation();
			if (at == null)
			{
				continue;
			}

			String key = at.getX() + "," + at.getY() + "," + at.getPlane();
			record("swordAnim:" + key, 0, GameObjectUtil.getAnimationId(statue.getGameObject()), baseline);
			record("swordState:" + key, 0, stateCode(statue), baseline);
			// Measured cycle lengths, so statues with differing timings can be compared.
			record("swordSafeWindow:" + key, 0, obstacleHandler.getSwordStatueTimings().getSafeWindow(at), baseline);
			record("swordWindup:" + key, 0, obstacleHandler.getSwordStatueTimings().getWindup(at), baseline);
		}
	}

	/**
	 * Thrown swords, which are the actual hazard - the statue only launches them. Written every
	 * tick rather than only on change, because the interesting part is how long a sword sits
	 * still at the end of its throw before coming back, and a dwell produces no change at all.
	 * <p>
	 * The value column is the number of consecutive ticks the sword has been stationary, and the
	 * plane/x/y columns carry the sword's own position rather than the player's.
	 */
	private void sampleSwordNpcs()
	{
		Set<Integer> seen = new HashSet<>();

		for (NPC npc : obstacleHandler.getSwordNpcs())
		{
			if (npc == null)
			{
				continue;
			}

			int index = npc.getIndex();
			seen.add(index);

			WorldPoint at = npc.getWorldLocation();
			if (at == null)
			{
				continue;
			}

			WorldPoint previous = swordNpcPositions.get(index);
			int stillTicks = at.equals(previous) ? swordNpcStillTicks.getOrDefault(index, 0) + 1 : 0;

			if (stillTicks == 0 && previous != null)
			{
				int dwell = swordNpcStillTicks.getOrDefault(index, 0);
				if (dwell > 0)
				{
					log.debug("Sword {} moved again after holding {} ticks at {},{},{}",
						index, dwell, previous.getX(), previous.getY(), previous.getPlane());
				}
			}

			swordNpcPositions.put(index, at);
			swordNpcStillTicks.put(index, stillTicks);

			write("swordNpc:" + index, npc.getId(), null, stillTicks, at);
		}

		swordNpcPositions.keySet().retainAll(seen);
		swordNpcStillTicks.keySet().retainAll(seen);
	}

	/** 0 safe, 1 warning, 2 imminent, 3 swinging. */
	private static int stateCode(SwordStatue statue)
	{
		if (statue.isSwinging())
		{
			return 3;
		}
		if (statue.isInImminentDangerState())
		{
			return 2;
		}
		return statue.isInDangerousState() ? 1 : 0;
	}

	private void record(String name, int id, int value, boolean baseline)
	{
		Integer previous = previousValues.put(name, value);
		if (!baseline && previous != null && previous == value)
		{
			return;
		}
		write(name, id, baseline ? null : previous, value, null);
	}

	/**
	 * @param locationOverride position to record instead of the player's, for rows that describe
	 *                         something with a position of its own
	 */
	private void write(String name, int id, Integer oldValue, int newValue, WorldPoint locationOverride)
	{
		if (writer == null)
		{
			return;
		}

		WorldPoint location = locationOverride;
		if (location == null)
		{
			Player player = client.getLocalPlayer();
			if (player != null)
			{
				location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
			}
		}

		try
		{
			writer.write(tick
				+ "\t" + name
				+ "\t" + id
				+ "\t" + (oldValue == null ? "-" : oldValue)
				+ "\t" + newValue
				+ "\t" + (location == null ? "-" : location.getPlane())
				+ "\t" + (location == null ? "-" : location.getX())
				+ "\t" + (location == null ? "-" : location.getY())
				// The game's own floor number, on every row, so each event is anchored to the
				// real floor and can be compared against what the plugin inferred.
				+ "\t" + client.getVarbitValue(VarbitID.HALLOWED_CURRENT_FLOOR)
				+ "\t" + obstacleHandler.getCurrentFloor()
				+ "\t" + obstacleHandler.getCurrentRoute()
				+ System.lineSeparator());
		}
		catch (IOException e)
		{
			log.warn("Could not write Sepulchre var dump", e);
		}
	}

	private void flush()
	{
		if (writer == null)
		{
			return;
		}
		try
		{
			writer.flush();
		}
		catch (IOException e)
		{
			log.warn("Could not flush Sepulchre var dump", e);
		}
	}
}
