package com.seibel.distanthorizons.forge17.modAccessor;

import com.mitchej123.hodgepodge.SimulationDistanceHelper;
import com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IHodgePodgeCommonAccessor;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

public class HodgePodgeAccessor implements IHodgePodgeCommonAccessor
{
	/** older Hodgepodge versions don't throttle chunk generation */
	private static final boolean HAS_CHUNK_GEN_SCHEDULER = hasChunkGenScheduler();

	@Override
	public String getModName() { return "HodgePodge"; }

	@Override
	public void preventChunkSimulation(World world, int x, int z)
	{ SimulationDistanceHelper.preventChunkSimulation(world, ChunkCoordIntPair.chunkXZ2Int(x, z), true); }

	@Override
	public void allowChunkSimulation(World world, int x, int z)
	{ SimulationDistanceHelper.preventChunkSimulation(world, ChunkCoordIntPair.chunkXZ2Int(x, z), false); }

	@Override
	public boolean allowChunkGeneration()
	{
		if (!HAS_CHUNK_GEN_SCHEDULER
			|| !ChunkGenScheduler.isBlocked())
		{
			return false;
		}

		ChunkGenScheduler.enableChunkLoads();
		return true;
	}

	@Override
	public void restoreChunkGeneration(boolean wasBlocked)
	{
		if (wasBlocked)
		{
			ChunkGenScheduler.disableChunkLoads();
		}
	}

	@Override
	public void runAsPopulation(World world, Runnable populate)
	{
		if (!HAS_CHUNK_GEN_SCHEDULER)
		{
			populate.run();
			return;
		}

		ChunkGenScheduler scheduler = ChunkGenScheduler.forDimension(world.provider.dimensionId);
		scheduler.incrementPopulationDepth();
		try
		{
			populate.run();
		}
		finally
		{
			scheduler.decrementPopulationDepth();
		}
	}

	private static boolean hasChunkGenScheduler()
	{
		try
		{
			Class.forName("com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler", false, HodgePodgeAccessor.class.getClassLoader());
			return true;
		}
		catch (ClassNotFoundException | LinkageError e)
		{
			return false;
		}
	}
}
