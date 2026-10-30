package com.seibel.distanthorizons.forge17.modAccessor;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;
import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;

public class CubicChunksAccessor implements ICubicChunksCommonAccessor
{
	public static final String CUBIC_CHUNKS_MOD_ID = "cubicchunks";

	/** DH stores heights in 12 bits, so it can't hold more than this */
	private static final int MAX_DH_HEIGHT_RANGE = 4096;
	/**
	 * Cubic Chunks worlds can go down to Integer.MIN_VALUE,
	 * client worlds do until the server sends the real range.
	 */
	private static final int LOWEST_MIN_HEIGHT = -2048;
	/** how many cubes below a column's lowest surface block have to be loaded to update its LOD */
	private static final int SURFACE_CUBES_BELOW = 2;



	@Override
	public String getModName() { return "CubicChunks"; }

	@Override
	public boolean isCubicWorld(World world)
	{
		// dummy worlds made by other mods stay vanilla
		return world.getChunkProvider() instanceof ICubeProvider;
	}

	@Override
	public int getMinHeight(World world)
	{
		int minHeight = Math.max(((ICubicWorld) world).getMinHeight(), LOWEST_MIN_HEIGHT);
		return Math.min(minHeight, 0) & ~15;
	}

	@Override
	public int getMaxHeight(World world)
	{
		int minHeight = this.getMinHeight(world);
		long maxHeight = Math.min(((ICubicWorld) world).getMaxHeight(), (long) minHeight + MAX_DH_HEIGHT_RANGE);
		return Math.max((int) maxHeight, 256) & ~15;
	}

	@Override
	public ExtendedBlockStorage[] getLoadedStorages(Chunk column, int minCubeY, int maxCubeY)
	{
		ExtendedBlockStorage[] storages = new ExtendedBlockStorage[maxCubeY - minCubeY + 1];
		for (Cube cube : ((IColumn) column).getLoadedCubes())
		{
			int cubeY = cube.getY();
			if (cubeY >= minCubeY && cubeY <= maxCubeY
				&& !cube.isEmpty())
			{
				storages[cubeY - minCubeY] = cube.getStorage();
			}
		}
		return storages;
	}

	@Override
	public boolean isColumnReady(Chunk column)
	{
		World world = column.worldObj;
		int[] surfaceCubeRange = this.getSurfaceCubeRange(column);
		if (surfaceCubeRange == null)
		{
			// nothing generated or loaded yet
			return false;
		}

		// a couple of cubes below the lowest top block, the top block can be water
		// and cliffs and shores show the cubes below them
		int minCubeY = Math.max(surfaceCubeRange[0] - SURFACE_CUBES_BELOW, this.getMinHeight(world) >> 4);
		for (int cubeY = minCubeY; cubeY <= surfaceCubeRange[1]; cubeY++)
		{
			Cube cube = ((IColumn) column).getLoadedCube(cubeY);
			if (cube == null)
			{
				return false;
			}

			// the client only gets finished cubes from the server
			if (!world.isRemote
				&& (!cube.isPopulated() || !cube.isInitialLightingDone()))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isCubeLoaded(Chunk column, int cubeY) { return ((IColumn) column).getLoadedCube(cubeY) != null; }

	@Override
	public int @Nullable [] getSurfaceCubeRange(Chunk column)
	{
		World world = column.worldObj;
		int minHeight = this.getMinHeight(world);
		int maxHeight = this.getMaxHeight(world);

		// the height map covers every cube the column ever had, loaded or not
		IHeightMap heightMap = ((IColumn) column).getOpacityIndex();
		int lowestTopY = Integer.MAX_VALUE;
		int highestTopY = Integer.MIN_VALUE;
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				int topY = heightMap.getTopBlockY(x, z);
				lowestTopY = Math.min(lowestTopY, topY);
				highestTopY = Math.max(highestTopY, topY);
			}
		}
		if (highestTopY < minHeight)
		{
			return null;
		}
		lowestTopY = Math.max(lowestTopY, minHeight);
		highestTopY = Math.min(highestTopY, maxHeight - 1);
		return new int[] { lowestTopY >> 4, highestTopY >> 4 };
	}

	@Override
	@Nullable
	public Chunk generateColumn(World world, int columnX, int columnZ)
	{
		ICubicWorld cubicWorld = (ICubicWorld) world;
		ICubeProviderServer provider = (ICubeProviderServer) cubicWorld.getCubeCache();

		Chunk column = provider.getColumn(columnX, columnZ, ICubeProviderServer.Requirement.GENERATE);
		if (column == null)
		{
			return null;
		}

		// cubes outside the generation range are only made when something
		// is built there, the column's existing ones are read anyway
		int minCubeY = Math.max(cubicWorld.getMinGenerationHeight(), this.getMinHeight(world)) >> 4;
		int maxCubeY = (Math.min(cubicWorld.getMaxGenerationHeight(), this.getMaxHeight(world)) - 1) >> 4;
		for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++)
		{
			provider.getCube(columnX, cubeY, columnZ, ICubeProviderServer.Requirement.LIGHT);
		}
		return column;
	}

}
