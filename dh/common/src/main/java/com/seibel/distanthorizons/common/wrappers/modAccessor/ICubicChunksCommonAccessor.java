package com.seibel.distanthorizons.common.wrappers.modAccessor;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Cubic Chunks splits columns into 16 block tall cubes that load and generate separately,
 * and worlds go below 0 and above 256. <br>
 * Chunks (columns) only hold the cubes between 0 and 255 in their storage array,
 * and reading other heights through the chunk can load or generate cubes.
 */
public interface ICubicChunksCommonAccessor extends IModAccessor
{
	boolean isCubicWorld(World world);

	/** @return the lowest block Y DH stores, a multiple of 16 */
	int getMinHeight(World world);
	/** @return one above the highest block Y DH stores, a multiple of 16 */
	int getMaxHeight(World world);

	/**
	 * Doesn't load or generate anything.
	 *
	 * @return the storage of each loaded cube from minCubeY to maxCubeY (inclusive),
	 *         null where the cube isn't loaded or is empty
	 */
	ExtendedBlockStorage[] getLoadedStorages(Chunk column, int minCubeY, int maxCubeY);

	/**
	 * Columns load before their cubes and cubes unload one by one,
	 * so a loaded column is often missing the cubes at its surface.
	 *
	 * @return true if the cubes from a bit below the column's surface up to its highest block are loaded and finished
	 */
	boolean isColumnReady(Chunk column);

	/**
	 * Doesn't load anything, the column's height map covers its unloaded cubes too.
	 *
	 * @return the lowest and highest cube Y holding the column's top blocks,
	 *         null if the column has no blocks within DH's height range
	 */
	@Nullable
	int[] getSurfaceCubeRange(Chunk column);

	/**
	 * Has to be called on the server thread.
	 * Loads or generates, populates and lights the column and
	 * its cubes within the generation range.
	 *
	 * @return null if the column couldn't be generated
	 */
	@Nullable
	Chunk generateColumn(World world, int columnX, int columnZ);

}
