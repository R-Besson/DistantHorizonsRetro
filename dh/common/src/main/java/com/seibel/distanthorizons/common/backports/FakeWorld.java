package com.seibel.distanthorizons.common.backports;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Used by {@link ClientBlockStateColorCache}
 * in order to handle some world related operations.
 */
public class FakeWorld implements IBlockAccess
{
	private IBlockAccess realWorld;
	private BiomeGenBase biome;
	
	private int blockX;
	private int blockY;
	private int blockZ;
	
	private FakeBlockState blockState;
	/** neighboring biomes are read from here, null if unknown */
	@Nullable
	private FullDataSourceV2 fullDataSource;



	//========//
	// update //
	//========//
	//region

	public void update(
		IBlockAccess realWorld, BiomeGenBase biome, @Nullable FullDataSourceV2 fullDataSource,
		int blockX, int blockY, int blockZ,
		FakeBlockState blockState)
	{
		this.realWorld = realWorld;
		this.biome = biome;
		this.fullDataSource = fullDataSource;
		
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		
		this.blockState = blockState;
		if (this.biome == null)
		{
			this.biome = BiomeGenBase.plains; // Fallback to prevent null pointers
		}
	}
	
	//endregion
	
	
	
	//===========//
	// overrides //
	//===========//
	//region
	
	@Override
	public Block getBlock(int x, int y, int z)
	{
		if (x == this.blockX 
			&& y == this.blockY 
			&& z == this.blockZ)
		{
			return this.blockState.block;
		}
		
		return Blocks.air;
	}

	@Override
	public TileEntity getTileEntity(int x, int y, int z) { return null; }

	@Override
	public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) { return 0; }

	@Override
	public int getBlockMetadata(int x, int y, int z)
	{
		if (x == this.blockX 
			&& y == this.blockY 
			&& z == this.blockZ)
		{
			return this.blockState.meta;
		}
		else if (this.blockState.block instanceof BlockDoublePlant)
		{
			/*
			 * Double tall grass on 1.7.10 has
			 * top block = meta 8
			 * bottom block = meta 2
			 * To get the color of the top block, BlockDoublePlant uses the meta of the bottom block.
			 * Since this is a fake world we can't get the real meta for the bottom block.
			 * We default to 2 since this applies a biome tint, which is what we want in most cases.
			 */
			return 2;
		}
		
		return 0;
	}

	@Override
	public int isBlockProvidingPowerTo(int x, int y, int z, int directionIn) { return 0; }

	@Override
	public boolean isAirBlock(int x, int y, int z) { return false; }

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z)
	{
		// Grass and leaves blend the biome colors of their neighbors (vanilla averages 3x3 columns),
		// without the neighbors' real biomes a tree hanging over a biome border
		// would get the other biome's color on those leaves (IE yellow plains leaves on a forest tree)
		if (this.fullDataSource == null
			|| (x == this.blockX && z == this.blockZ))
		{
			return this.biome;
		}

		long sectionPos = this.fullDataSource.getPos();
		int columnWidth = DhSectionPos.getBlockWidth(sectionPos) / this.fullDataSource.getWidthInDataColumns();
		int relX = Math.floorDiv(x - DhSectionPos.getMinCornerBlockX(sectionPos), columnWidth);
		int relZ = Math.floorDiv(z - DhSectionPos.getMinCornerBlockZ(sectionPos), columnWidth);

		// outside this LOD (IE on its border), the neighbor's biome isn't known
		LongArrayList column = this.fullDataSource.tryGetColumnAtRelPos(relX, relZ);
		if (column == null || column.isEmpty())
		{
			return this.biome;
		}

		// MC's biomes are per column, so any datapoint works
		try
		{
			IBiomeWrapper biomeWrapper = this.fullDataSource.mapping.getBiomeWrapper(FullDataPointUtil.getId(column.getLong(0)));
			BiomeGenBase columnBiome = (biomeWrapper instanceof BiomeWrapper) ? ((BiomeWrapper) biomeWrapper).biome : null;
			return (columnBiome != null) ? columnBiome : this.biome;
		}
		catch (IndexOutOfBoundsException e)
		{
			return this.biome;
		}
	}

	@Override
	public int getHeight() { return this.realWorld.getHeight(); }

	@Override
	public boolean extendedLevelsInChunkCache() { return this.realWorld.extendedLevelsInChunkCache(); }

	@Override
	public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) { throw new UnsupportedOperationException("isSideSolid() isn't implemented"); }
	
	//endregion
	
	
	
}
