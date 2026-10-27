/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.seibel.distanthorizons.common.wrappers.chunk;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.MutableBlockPosWrapper;
import com.seibel.distanthorizons.common.wrappers.modAccessor.CubicChunksCompat;
import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import net.minecraft.init.Blocks;
import org.jetbrains.annotations.Nullable;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import com.seibel.distanthorizons.common.backports.FakeBlockState;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.seibel.distanthorizons.core.logging.DhLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public class ChunkWrapper implements IChunkWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	/** can be used for interactions with the underlying chunk where creating new BlockPos objects could cause issues for the garbage collector. */
	private static final ThreadLocal<MutableBlockPosWrapper> MUTABLE_BLOCK_POS_WRAPPER_REF = ThreadLocal.withInitial(() -> new MutableBlockPosWrapper());

	public static final Set<String> LOGGED_BLOCK_GET_ERRORS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	private static boolean heightmapThreadWarningLogged = false;

	private final Chunk chunk;

	private final DhChunkPos chunkPos;
	private final ILevelWrapper wrappedLevel;

	private boolean isDhBlockLightCorrect = false;
	private boolean isDhSkyLightCorrect = false;

	private ChunkLightStorage blockLightStorage;
	private ChunkLightStorage skyLightStorage;

	private ArrayList<DhBlockPos> blockLightPosList = null;

	private int minNonEmptyHeight = Integer.MIN_VALUE;
	private int maxNonEmptyHeight = Integer.MAX_VALUE;

	/** will be null if we are using MC heightmaps */
	private int[][] solidHeightMap = null;
	/** will be null if we are using MC heightmaps */
	private int[][] lightBlockingHeightMap = null;

	private final BiomeGenBase[] biomeList;

	private final int minBuildHeight;
	private final int maxBuildHeight;
	/**
	 * Cubic Chunks: the storage of every loaded cube from {@link #minBuildHeight} up, null if the world isn't cubic. <br>
	 * Reading blocks through the chunk would load or generate missing cubes,
	 * and cubes can unload before DH reads them.
	 */
	@Nullable
	private final ExtendedBlockStorage[] cubicSections;



	//=============//
	// constructor //
	//=============//
	//region

	/**
	 * Note: this constructor should be very
	 * fast since it will be called frequently on the MC
	 * server thread and a slow method will cause server lag.
	 */
	public ChunkWrapper(Chunk chunk, ILevelWrapper wrappedLevel)
	{
		this.chunk = chunk;
		this.wrappedLevel = wrappedLevel;

		this.chunkPos = new DhChunkPos(chunk.xPosition, chunk.zPosition);
		this.biomeList = fillBiomeMap(chunk);

		ICubicChunksCommonAccessor cubicChunks = CubicChunksCompat.getAccessor(chunk.worldObj);
		if (cubicChunks != null)
		{
			this.minBuildHeight = cubicChunks.getMinHeight(chunk.worldObj);
			this.maxBuildHeight = cubicChunks.getMaxHeight(chunk.worldObj);
			this.cubicSections = cubicChunks.getLoadedStorages(chunk, this.minBuildHeight >> 4, (this.maxBuildHeight >> 4) - 1);
		}
		else
		{
			this.minBuildHeight = 0;
			this.maxBuildHeight = 256;
			this.cubicSections = null;
		}
	}

	/** used for copying */
	private ChunkWrapper(ChunkWrapper that, ILevelWrapper wrappedLevel)
	{
		this.chunk = that.chunk;
		this.wrappedLevel = wrappedLevel;
		this.chunkPos = new DhChunkPos(that.chunkPos.getX(), that.chunkPos.getZ());
		this.biomeList = that.biomeList;
		this.minBuildHeight = that.minBuildHeight;
		this.maxBuildHeight = that.maxBuildHeight;
		this.cubicSections = that.cubicSections;
	}

	private ExtendedBlockStorage[] getSections() { return (this.cubicSections != null) ? this.cubicSections : this.chunk.getBlockStorageArray(); }



	// copiers //
	//region

	@Override
	public ChunkWrapper copy()
	{
		return new ChunkWrapper(this, this.wrappedLevel);
	}

	@Override
	public ChunkWrapper copyWithLevel(ILevelWrapper levelWrapper)
	{
		return new ChunkWrapper(this, levelWrapper);
	}

	//endregion



	// constructor helpers //
	//region

	private static BiomeGenBase[] fillBiomeMap(Chunk chunk)
	{
		World world = chunk.worldObj;
		// Mods like LOTR override the provider's biome lookup, those must go through the world.
		// Otherwise the biome is read from this chunk, the world's lookup would end up there too
		// but first checks the server's chunk map, which isn't thread safe and wrappers are created off thread.
		boolean useWorldLookup = providerOverridesBiomeLookup(world.provider);
		WorldChunkManager chunkManager = world.getWorldChunkManager();

		BiomeGenBase[] biomeArray = new BiomeGenBase[LodUtil.CHUNK_WIDTH * LodUtil.CHUNK_WIDTH];
		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				biomeArray[x * LodUtil.CHUNK_WIDTH + z] = useWorldLookup
					? world.getBiomeGenForCoords((chunk.xPosition << 4) + x, (chunk.zPosition << 4) + z)
					: chunk.getBiomeGenForWorldCoords(x, z, chunkManager);
			}
		}
		return biomeArray;
	}

	/** getBiomeGenForCoords is added by Forge, so its name isn't obfuscated */
	private static boolean providerOverridesBiomeLookup(WorldProvider provider)
	{
		return PROVIDER_OVERRIDES_BIOME_LOOKUP.computeIfAbsent(provider.getClass(), (providerClass) ->
		{
			try
			{
				return providerClass.getMethod("getBiomeGenForCoords", int.class, int.class).getDeclaringClass() != WorldProvider.class;
			}
			catch (NoSuchMethodException e)
			{
				return true;
			}
		});
	}
	private static final ConcurrentHashMap<Class<?>, Boolean> PROVIDER_OVERRIDES_BIOME_LOOKUP = new ConcurrentHashMap<>();

	//endregion
	//endregion



	//=========//
	// getters //
	//=========//
	//region

	@Override
	public int getHeight() { return (this.cubicSections != null) ? this.maxBuildHeight - this.minBuildHeight : 255; }

	@Override
	public int getInclusiveMinBuildHeight() { return this.minBuildHeight; }

	@Override
	public int getExclusiveMaxBuildHeight() { return this.maxBuildHeight; }

	@Override
	public int getMinNonEmptyHeight()
	{
		if (this.minNonEmptyHeight != Integer.MIN_VALUE)
		{
			return this.minNonEmptyHeight;
		}


		// default if every section is empty or missing
		this.minNonEmptyHeight = this.getInclusiveMinBuildHeight();

		// determine the lowest empty section (bottom up)
		ExtendedBlockStorage[] sections = this.getSections();
		for (int index = 0; index < sections.length; index++)
		{
			if (sections[index] == null)
			{
				continue;
			}

			if (!isChunkSectionEmpty(sections[index]))
			{
				this.minNonEmptyHeight = this.getChunkSectionMinHeight(index);
				break;
			}
		}

		return this.minNonEmptyHeight;
	}


	@Override
	public int getMaxNonEmptyHeight()
	{
		if (this.maxNonEmptyHeight != Integer.MAX_VALUE)
		{
			return this.maxNonEmptyHeight;
		}


		// default if every section is empty or missing
		this.maxNonEmptyHeight = this.getExclusiveMaxBuildHeight();

		// determine the highest empty section (top down)
		ExtendedBlockStorage[] sections = this.getSections();
		for (int index = sections.length-1; index >= 0; index--)
		{
			// update at each position to fix using the max height if the chunk is empty
			this.maxNonEmptyHeight = this.getChunkSectionMinHeight(index) + 16;

			if (sections[index] == null)
			{
				continue;
			}

			if (!isChunkSectionEmpty(sections[index]))
			{
				// non-empty section found
				break;
			}
		}

		return this.maxNonEmptyHeight;
	}
	private static boolean isChunkSectionEmpty(ExtendedBlockStorage section)
	{
		return section.isEmpty();
	}
	private int getChunkSectionMinHeight(int index) { return (index * 16) + this.getInclusiveMinBuildHeight(); }

	@Override
	public void createDhHeightMaps()
	{
		if (!heightmapThreadWarningLogged
			&& !DhApi.isDhThread())
		{
			heightmapThreadWarningLogged = true;
			LOGGER.warn("ChunkWrapper Height maps created on non-DH thread ["+Thread.currentThread().getName()+"]. This may cause stuttering.");
		}



		this.solidHeightMap = new int[LodUtil.CHUNK_WIDTH][LodUtil.CHUNK_WIDTH];
		this.lightBlockingHeightMap = new int[LodUtil.CHUNK_WIDTH][LodUtil.CHUNK_WIDTH];

		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				int minInclusiveBuildHeight = this.getMinNonEmptyHeight();
				// if no blocks are found the height map will be at the bottom of the world
				int solidHeight = minInclusiveBuildHeight;
				int lightBlockingHeight = minInclusiveBuildHeight;


				int y = this.getMaxNonEmptyHeight();
				IBlockStateWrapper block = this.getBlockState(x, y, z);
				while (// go down until we reach the minimum build height
						y > minInclusiveBuildHeight
						// keep going until we find both height map values
						&&
						(
							solidHeight == minInclusiveBuildHeight
							|| lightBlockingHeight == minInclusiveBuildHeight
						)
					)
				{
					// is this block solid?
					if (solidHeight == minInclusiveBuildHeight
						&& block.isSolid())
					{
						solidHeight = y;
					}

					// is this block light blocking?
					if (lightBlockingHeight == minInclusiveBuildHeight
						&& block.getOpacity() != LodUtil.BLOCK_FULLY_TRANSPARENT)
					{
						lightBlockingHeight = y;
					}

					// get the next block down
					y--;
					block = this.getBlockState(x, y, z);
				}

				this.solidHeightMap[x][z] = solidHeight;
				this.lightBlockingHeightMap[x][z] = lightBlockingHeight;
			}
		}
	}

	@Override
	public int getSolidHeightMapValue(int xRel, int zRel)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);

		// will be null if we want to use MC heightmaps
		if (this.solidHeightMap == null)
		{
			// assume max height so we don't miss anything,
			// Cubic Chunks worlds are thousands of blocks tall
			return (this.cubicSections != null) ? this.getMaxNonEmptyHeight() - 1 : 255;
		}
		else
		{
			return this.solidHeightMap[xRel][zRel];
		}
	}

	@Override
	public int getLightBlockingHeightMapValue(int xRel, int zRel)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);

		if (this.lightBlockingHeightMap == null)
		{
			if (this.cubicSections != null)
			{
				// Cubic Chunks' height map covers the whole world, not just what DH stores
				int height = this.chunk.getHeightValue(xRel, zRel);
				return Math.max(this.getMinNonEmptyHeight(), Math.min(height, this.getMaxNonEmptyHeight() - 1));
			}
			return this.chunk.getHeightValue(xRel, zRel);
		}
		else
		{
			return this.lightBlockingHeightMap[xRel][zRel];
		}
	}


	@Override
	public IBiomeWrapper getBiome(int relX, int relY, int relZ)
	{
		BiomeGenBase biome = this.biomeList[(relX * LodUtil.CHUNK_WIDTH) + relZ];
		return BiomeWrapper.getBiomeWrapper(biome, this.wrappedLevel);
	}

	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);

		try
		{
			final Block block = this.getBlock(relX, relY, relZ);
			final int meta = this.getBlockMetadata(relX, relY, relZ);
			return BlockStateWrapper.fromBlockState(block, meta, this.wrappedLevel);
		}
		catch (Exception e)
		{
			if (LOGGED_BLOCK_GET_ERRORS.add(e.getMessage()))
			{
				LOGGER.warn("Failed to get block from chunk ["+this.chunkPos+"] at relative block pos ["+relX+","+relY+","+relZ+"], air will be used instead. This error message will only be logged once. error: ["+e.getMessage()+"].", e);
			}

			return BlockStateWrapper.AIR;
		}
	}

	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);

		try
		{
			final Block block = this.getBlock(relX, relY, relZ);
			final int meta = this.getBlockMetadata(relX, relY, relZ);
			return BlockStateWrapper.fromBlockState(block, meta, this.wrappedLevel, guess);
		}
		catch (Exception e)
		{
			if (LOGGED_BLOCK_GET_ERRORS.add(e.getMessage()))
			{
				LOGGER.warn("Failed to get block from chunk ["+this.chunkPos+"] at relative block pos ["+relX+","+relY+","+relZ+"], air will be used instead. This error message will only be logged once. error: ["+e.getMessage()+"].", e);
			}

			return BlockStateWrapper.AIR;
		}
	}

	private Block getBlock(int relX, int y, int relZ)
	{
		if (this.cubicSections == null)
		{
			return this.chunk.getBlock(relX, y, relZ);
		}

		ExtendedBlockStorage section = this.getCubicSection(y);
		return (section != null) ? section.getBlockByExtId(relX, y & 15, relZ) : Blocks.air;
	}
	private int getBlockMetadata(int relX, int y, int relZ)
	{
		if (this.cubicSections == null)
		{
			return this.chunk.getBlockMetadata(relX, y, relZ);
		}

		ExtendedBlockStorage section = this.getCubicSection(y);
		return (section != null) ? section.getExtBlockMetadata(relX, y & 15, relZ) : 0;
	}
	@Nullable
	private ExtendedBlockStorage getCubicSection(int y)
	{
		int index = (y - this.minBuildHeight) >> 4;
		return (index >= 0 && index < this.cubicSections.length) ? this.cubicSections[index] : null;
	}

	@Override
	public IMutableBlockPosWrapper getMutableBlockPosWrapper() { return MUTABLE_BLOCK_POS_WRAPPER_REF.get(); }

	@Override
	public DhChunkPos getChunkPos() { return this.chunkPos; }

	public Chunk getChunk()
	{ return this.chunk; }


	@Override
	public int getMinBlockX() { return this.chunkPos.getMinBlockX(); }
	@Override
	public int getMinBlockZ() { return this.chunkPos.getMinBlockZ(); }


	/**
	 * Returns true if the chunk has
	 * been populated far enough for us to
	 * convert it to an LOD.
	 */
	public static boolean canSaveChunk(Chunk chunk)
	{
		if (chunk == null
			|| !chunk.isTerrainPopulated
			|| !chunk.isLightPopulated)
		{
			return false;
		}

		// Cubic Chunks columns are always marked as populated, their cubes may not be there yet
		ICubicChunksCommonAccessor cubicChunks = CubicChunksCompat.getAccessor(chunk.worldObj);
		return cubicChunks == null || cubicChunks.isColumnReady(chunk);
	}

	//endregion



	//==========//
	// lighting //
	//==========//
	//region

	@Override
	public void setIsDhSkyLightCorrect(boolean isDhLightCorrect) { this.isDhSkyLightCorrect = isDhLightCorrect; }
	@Override
	public void setIsDhBlockLightCorrect(boolean isDhLightCorrect) { this.isDhBlockLightCorrect = isDhLightCorrect; }

	@Override
	public boolean isDhBlockLightingCorrect() { return this.isDhBlockLightCorrect; }
	@Override
	public boolean isDhSkyLightCorrect() { return this.isDhSkyLightCorrect; }


	@Override
	public int getDhBlockLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getBlockLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhBlockLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getBlockLightStorage().set(relX, y, relZ, lightValue);
	}

	private ChunkLightStorage getBlockLightStorage()
	{
		if (this.blockLightStorage == null)
		{
			this.blockLightStorage = ChunkLightStorage.createBlockLightStorage(this);
		}
		return this.blockLightStorage;
	}
	public void setBlockLightStorage(ChunkLightStorage lightStorage) { this.blockLightStorage = lightStorage; }
	@Override
	public void clearDhBlockLighting() { this.getBlockLightStorage().clear(); }


	@Override
	public int getDhSkyLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getSkyLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhSkyLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getSkyLightStorage().set(relX, y, relZ, lightValue);
	}
	@Override
	public void clearDhSkyLighting() { this.getSkyLightStorage().clear(); }

	private ChunkLightStorage getSkyLightStorage()
	{
		if (this.skyLightStorage == null)
		{
			this.skyLightStorage = ChunkLightStorage.createSkyLightStorage(this);
		}
		return this.skyLightStorage;
	}
	public void setSkyLightStorage(ChunkLightStorage lightStorage) { this.skyLightStorage = lightStorage; }


	/**
	 * FIXME synchronized is necessary for a rare issue where this method is called from two separate threads at the same time
	 *  before the list has finished populating.
	 */
	@Override
	public synchronized ArrayList<DhBlockPos> getWorldBlockLightPosList()
	{
		// only populate the list once
		if (this.blockLightPosList == null)
		{
			this.blockLightPosList = new ArrayList<>();

			// MC doesn't store light emitting block positions, so we have to bruteforce it
			for (ExtendedBlockStorage section : this.getSections())
			{
				if (section == null || section.isEmpty())
				{
					continue;
				}

				int baseY = section.getYLocation();

				for (int x = 0; x < 16; x++)
				{
					for (int z = 0; z < 16; z++)
					{
						for (int y = 0; y < 16; y++)
						{
							int lightValue = FakeBlockState.getLightEmission(section.getBlockByExtId(x, y, z), section.getExtBlockMetadata(x, y, z));
							if (lightValue > 0)
							{
								this.blockLightPosList.add(new DhBlockPos(this.chunkPos.getMinBlockX() + x, baseY + y, this.chunkPos.getMinBlockZ() + z));
							}
						}
					}
				}
			}
		}

		return this.blockLightPosList;
	}

	//endregion



	//================//
	// base overrides //
	//================//
	//region

	@Override
	public String toString() { return this.chunk.getClass().getSimpleName() + this.chunkPos; }

	//endregion



}
