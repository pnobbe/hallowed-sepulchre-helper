package com.sepulchre.handler;

import com.sepulchre.config.SepulchreConfig;
import com.sepulchre.model.SepulchreRoute;
import com.sepulchre.util.RouteObstacles;
import com.sepulchre.util.SepulchreConstants;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;

@Getter
public class FloorState
{
	private final Client client;
	private final SepulchreConfig config;

	private Runnable onLowerSectionEntered;

	@Setter
	private int currentFloor = 0;

	private SepulchreRoute currentRoute = SepulchreRoute.UNKNOWN;

	/*
	 * Route relevance is a pure function of (tile, currentRoute), so it only changes when the
	 * route changes or the scene reloads. Caching it per scene tile keeps the overlay's
	 * per-frame work to an array read instead of a coordinate conversion plus map lookups.
	 */
	private static final int ROUTE_CACHE_SIZE = Constants.MAX_Z * Constants.SCENE_SIZE * Constants.SCENE_SIZE;
	private final boolean[] routeCacheComputed = new boolean[ROUTE_CACHE_SIZE];
	private final boolean[] routeCacheValue = new boolean[ROUTE_CACHE_SIZE];

	private int floorStartPlane = -1;
	private boolean inLowerSection = false;

	private int floorTicks = 0;
	private int runTicks = 0;
	private int timeRemaining = 0;
	private int previousRunTicks = 0;
	private boolean timerPaused = false;
	private boolean timerStarted = false;

	private boolean doorToNextFloorClosed = false;

	public FloorState(Client client, SepulchreConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public void setOnLowerSectionEntered(Runnable callback)
	{
		this.onLowerSectionEntered = callback;
	}

	public void onFloorEntered(boolean isFirstFloor)
	{
		if (isFirstFloor)
		{
			currentFloor = 1;
		}
		else if (currentFloor > 0 && currentFloor < 5)
		{
			currentFloor++;
		}
	}

	public void setFloorStartPlane(int plane)
	{
		this.floorStartPlane = plane;
		this.inLowerSection = false;
	}

	public void restoreFloorState(boolean wasInLowerSection, int previousStartPlane)
	{
		this.inLowerSection = wasInLowerSection;
		this.floorStartPlane = previousStartPlane;
		if (wasInLowerSection && previousStartPlane < 0)
		{
			this.floorStartPlane = 99;
		}
	}

	public void onDoorToNextFloorClosed()
	{
		doorToNextFloorClosed = true;
	}

	/**
	 * Returns true if the player moved to a lower plane, indicating a scan may be needed.
	 */
	public void updateLowerSectionStatus(int currentPlane)
	{
		if (floorStartPlane >= 0 && currentPlane < floorStartPlane)
		{
			boolean wasInLowerSection = inLowerSection;
			inLowerSection = true;

			if (!wasInLowerSection && currentFloor == 1
				&& (currentRoute == SepulchreRoute.FLOOR_1_NORTHEAST || currentRoute == SepulchreRoute.FLOOR_1_NORTHWEST))
			{
				if (onLowerSectionEntered != null)
				{
					onLowerSectionEntered.run();
				}
			}
		}
	}

	public void updateTimers()
	{
		runTicks = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_TIME_SPENT);
		floorTicks = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_CURRENT_FLOOR_TIME_SPENT);
		timeRemaining = client.getVarbitValue(SepulchreConstants.VARBIT_HALLOWED_TIME_REMAINING);
		timerStarted = runTicks > 0;
		timerPaused = timerStarted && runTicks == previousRunTicks;
		previousRunTicks = runTicks;

		// The chat message that normally signals this is easy to miss (spam, filters, relogging),
		// so corroborate it with the server's own clock. Deliberately additive: the message
		// remains the primary trigger and this only ever sets the flag, never clears it.
		if (timerStarted && timeRemaining == SepulchreConstants.TIME_REMAINING_EXPIRED)
		{
			doorToNextFloorClosed = true;
		}
	}

	public void setCurrentRoute(SepulchreRoute route)
	{
		if (currentRoute != route)
		{
			currentRoute = route;
			invalidateRouteCache();
		}
	}

	/**
	 * Discards cached route relevance. Must be called whenever the active route changes, the
	 * scene reloads (which remaps scene tiles to world coordinates), or the route filter toggles.
	 */
	public void invalidateRouteCache()
	{
		Arrays.fill(routeCacheComputed, false);
	}

	/**
	 * Index into the route cache for the scene tile containing this point, or -1 if the point
	 * falls outside the loaded scene and therefore cannot be cached.
	 */
	private int routeCacheIndex(LocalPoint localPoint)
	{
		WorldView worldView = client.getWorldView(localPoint.getWorldView());
		if (worldView == null)
		{
			return -1;
		}

		int plane = worldView.getPlane();
		int sceneX = localPoint.getSceneX();
		int sceneY = localPoint.getSceneY();

		if (plane < 0 || plane >= Constants.MAX_Z
			|| sceneX < 0 || sceneX >= Constants.SCENE_SIZE
			|| sceneY < 0 || sceneY >= Constants.SCENE_SIZE)
		{
			return -1;
		}

		return (plane * Constants.SCENE_SIZE + sceneY) * Constants.SCENE_SIZE + sceneX;
	}

	private boolean computeRouteRelevance(LocalPoint localPoint)
	{
		if (!config.filterByRoute())
		{
			return true;
		}
		WorldPoint canonicalLocation = WorldPoint.fromLocalInstance(client, localPoint);
		if (canonicalLocation == null)
		{
			return true;
		}
		return RouteObstacles.isRelevantForRoute(canonicalLocation, currentRoute);
	}

	public boolean shouldShowForCurrentRoute(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return true;
		}

		int index = routeCacheIndex(localPoint);
		if (index < 0)
		{
			return computeRouteRelevance(localPoint);
		}

		if (!routeCacheComputed[index])
		{
			routeCacheValue[index] = computeRouteRelevance(localPoint);
			routeCacheComputed[index] = true;
		}
		return routeCacheValue[index];
	}

	public boolean shouldShowForCurrentRoute(WorldPoint instanceLocation)
	{
		if (instanceLocation == null)
		{
			return true;
		}
		LocalPoint localPoint = LocalPoint.fromWorld(client, instanceLocation);
		if (localPoint == null)
		{
			return true;
		}
		return shouldShowForCurrentRoute(localPoint);
	}

	public boolean isCoffinLootingEnabledForCurrentFloor()
	{
		return config.lootingFloors().includesFloor(currentFloor);
	}

	public boolean isGrandCoffinLootingEnabled()
	{
		return config.lootingFloors().includesGrandCoffin();
	}

	public int getPlayerMaxFloor()
	{
		int agilityLevel = client.getBoostedSkillLevel(net.runelite.api.Skill.AGILITY);
		for (int i = SepulchreConstants.FLOOR_AGILITY_REQUIREMENTS.length - 1; i >= 0; i--)
		{
			if (agilityLevel >= SepulchreConstants.FLOOR_AGILITY_REQUIREMENTS[i])
			{
				return i + 1;
			}
		}
		return 0;
	}

	public void reset()
	{
		doorToNextFloorClosed = false;
		currentFloor = 0;
		currentRoute = SepulchreRoute.UNKNOWN;
		invalidateRouteCache();
		floorStartPlane = -1;
		inLowerSection = false;
		floorTicks = 0;
		runTicks = 0;
		timeRemaining = 0;
		previousRunTicks = 0;
		timerPaused = false;
		timerStarted = false;
	}
}
