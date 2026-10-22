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

package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality;
import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.seibel.distanthorizons.core.logging.DhLogger;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * Handles converting {@link FullDataSourceV2}'s to {@link ColumnRenderSource}.
 */
public class FullDataToRenderDataTransformer
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	private static final LongOpenHashSet BROKEN_POS_SET = new LongOpenHashSet();
	private static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Data Transformer");
	
	private static HashSet<IBlockStateWrapper> snowLayerBlockStates = null;
	
	
	
	//==============================//
	// public transformer interface //
	//==============================//
	//region
	
	@Nullable
	public static ColumnRenderSource transformFullDataToRenderSource(
			@Nullable FullDataSourceV2 fullDataSource, @Nullable IClientLevelWrapper levelWrapper)
	{
		if (fullDataSource == null)
		{
			return null;
		}
		else if (levelWrapper == null)
		{
			// if the client is no longer loaded in the world, render sources cannot be created 
			return null;
		}
		
		
		try
		{
			return transformCompleteFullDataToColumnData(levelWrapper, fullDataSource);
		}
		catch (InterruptedException e)
		{
			return null;
		}
	}
	
	//endregion
	
	
	
	//==============//
	// transformers //
	//==============//
	//region
	
	/**
	 * Creates a LodNode for a chunk in the given world.
	 *
	 * @throws IllegalArgumentException thrown if either the chunk or world is null.
	 * @throws InterruptedException Can be caused by interrupting the thread upstream.
	 * Generally thrown if the method is running after the client leaves the current world.
	 */
	private static ColumnRenderSource transformCompleteFullDataToColumnData(
		IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource) throws InterruptedException
	{
		final long pos = fullDataSource.getPos();
		final byte dataDetail = fullDataSource.getDataDetailLevel();
		
		int configVertSliceCount = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxNumberOfVerticalSlicesAtDetailLevel(dataDetail);
		// Textured LODs are close enough to see single blocks, too few slices merge them together
		// (IE a tree trunk drawn as the leaves above it, or sandstone as the sand on top),
		// so they keep at least the high vertical quality's slices
		if (Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& dataDetail <= Config.Client.Advanced.Graphics.Texture.maxTexturedLodDetailLevel.get())
		{
			configVertSliceCount = Math.max(configVertSliceCount, EDhApiVerticalQuality.HIGH.calculateMaxNumberOfVerticalSlicesAtDetailLevel(dataDetail));
		}
		final int maxVertSliceCount = configVertSliceCount;
		
		
		
		final ColumnRenderSource columnSource = ColumnRenderSource.createEmpty(pos, maxVertSliceCount, levelWrapper.getMinHeight());
		if (fullDataSource.isEmpty)
		{
			return columnSource;
		}
		
		columnSource.markNotEmpty();
		int baseX = DhSectionPos.getMinCornerBlockX(pos);
		int baseZ = DhSectionPos.getMinCornerBlockZ(pos);
		
		try(ColumnRenderView columnArrayView = ColumnRenderView.getPooled();
			PhantomArrayListCheckout phantomCheckout = ARRAY_LIST_POOL.checkoutLongArrays(2);
			ColumnRenderView tempExpandingColumnView = ColumnRenderView.getPooled();
			RenderDataPointReducingList reducingList = new RenderDataPointReducingList())
		{
			DhBlockPosMutable mutableBlockPos = new DhBlockPosMutable();
			for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
			{
				for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
				{
					columnSource.populateColumnView(columnArrayView, x, z);
					LongArrayList dataColumn = fullDataSource.getColumnAtRelPos(x, z);
					
					updateOrReplaceRenderDataViewColumnWithFullDataColumn(
						levelWrapper, fullDataSource,
						// bit shift is to account for LODs with a detail level greater than 0 so the block pos is correct
						baseX + BitShiftUtil.pow(x, dataDetail), baseZ + BitShiftUtil.pow(z, dataDetail),
						columnArrayView, dataColumn,
						columnSource, x, z,
						// pooled references so we don't need to re-allocate/get them 4000 times per render source
						phantomCheckout, tempExpandingColumnView, reducingList, mutableBlockPos);
				}
			}
		}
		
		return columnSource;
	}
	
	/** Updates the given {@link ColumnRenderView} to match the incoming Full data {@link LongArrayList} */
	private static void updateOrReplaceRenderDataViewColumnWithFullDataColumn(
		IClientLevelWrapper levelWrapper,
		FullDataSourceV2 fullDataSource, int blockX, int blockZ, 
		ColumnRenderView columnArrayView, 
		LongArrayList fullDataColumn,
		// only needed if the render source stores texture ids, see applyTextureSetIds
		@Nullable ColumnRenderSource columnSource, int sourceRelX, int sourceRelZ,
		// pooled references
		PhantomArrayListCheckout phantomCheckout, ColumnRenderView tempExpandingColumnView, 
		RenderDataPointReducingList reducingList, DhBlockPosMutable mutableBlockPos)
	{
		// we can't do anything if the full data is missing or empty
		if (fullDataColumn == null 
			|| fullDataColumn.size() == 0)
		{
			return;
		}
		
		int fullDataLength = fullDataColumn.size();
		if (fullDataLength <= columnArrayView.maxVerticalSliceCount)
		{
			// Directly use the arrayView since it fits.
			setRenderColumnView(levelWrapper, fullDataSource, blockX, blockZ, columnArrayView, fullDataColumn, mutableBlockPos);
		}
		else
		{
			LongArrayList dataArrayList = phantomCheckout.getLongArray(0, fullDataLength);
			LongArrayList voidDataArrayList = phantomCheckout.getLongArray(1, fullDataLength);
			
			// expand the ColumnArrayView to fit the new larger max vertical size
			tempExpandingColumnView.populate(
				dataArrayList, voidDataArrayList,
				fullDataLength, 0, 0, fullDataLength);
			setRenderColumnView(levelWrapper, fullDataSource, blockX, blockZ, tempExpandingColumnView, fullDataColumn, mutableBlockPos);
			
			columnArrayView.changeVerticalSizeFrom(tempExpandingColumnView, reducingList);
		}
		
		if (columnSource != null 
			&& columnSource.hasTextureSetIds())
		{
			applyTextureSetIds(levelWrapper, fullDataSource, blockX, blockZ, columnArrayView, fullDataColumn, columnSource, sourceRelX, sourceRelZ);
		}
	}
	
	/**
	 * Determines the texture for each finished render data point
	 * by finding the full data block at the data point's top. <br>
	 * The top block is used since merged data points keep their top block's color,
	 * keeping the texture consistent with the color it multiplies. <br><br>
	 *
	 * This runs after any vertical size reduction so it doesn't matter
	 * how the data points were merged along the way. <br><br>
	 *
	 * Blocks that look different depending on where they are (IE Chisel's connected textures)
	 * get the texture MC draws at this position.
	 */
	private static void applyTextureSetIds(
			IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource, int blockX, int blockZ,
			ColumnRenderView renderColumnData, LongArrayList fullDataColumn,
			ColumnRenderSource columnSource, int sourceRelX, int sourceRelZ)
	{
		for (int renderIndex = 0; renderIndex < renderColumnData.size; renderIndex++)
		{
			long renderData = renderColumnData.get(renderIndex);
			if (!RenderDataPointUtil.doesDataPointExist(renderData)
				|| RenderDataPointUtil.hasZeroHeight(renderData))
			{
				break;
			}
			
			int topBlockY = RenderDataPointUtil.getYMax(renderData) - 1;
			byte materialId = RenderDataPointUtil.getBlockMaterialId(renderData);
			
			// find the full data point containing the render data point's top block,
			// the full data column is sorted top down
			short textureId = 0;
			for (int fullIndex = 0; fullIndex < fullDataColumn.size(); fullIndex++)
			{
				long fullData = fullDataColumn.getLong(fullIndex);
				int bottomY = FullDataPointUtil.getBottomY(fullData);
				if (bottomY > topBlockY)
				{
					continue;
				}
				
				if (topBlockY < bottomY + FullDataPointUtil.getHeight(fullData))
				{
					try
					{
						IBlockStateWrapper block = fullDataSource.mapping.getBlockStateWrapper(FullDataPointUtil.getId(fullData));
						
						// If this render data is snow, but the block ID doesn't match,
						// try getting the block above it.
						// This is a hacky fix for snow on LOD borders rendering with the
						// underlying grass/dirt block, instead of as snow.
						if (materialId == EDhApiBlockMaterial.SNOW.index
							&& block.getMaterialId() != materialId
							&& fullIndex > 0)
						{
							// Note: this is a hack.
							// Since the Render data and textures aren't in the same data array it's difficult to map
							// them together, but this guess works well enough for now.
							
							fullData = fullDataColumn.getLong(fullIndex - 1);
							IBlockStateWrapper newBlock = fullDataSource.mapping.getBlockStateWrapper(FullDataPointUtil.getId(fullData));
							// don't use air since that'll remove the texture
							if (!newBlock.isAir())
							{
								block = newBlock;
							}
						}
						
						// the same plant top as the render data was built with (see setRenderColumnView)
						if (RenderDataPointUtil.getShape(renderData) == RenderDataPointUtil.SHAPE_CROSS)
						{
							block = block.getPlantTopWithBottom(getTouchingBlock(fullDataColumn, fullIndex + 1, FullDataPointUtil.getBottomY(fullData), false, fullDataSource.mapping), levelWrapper);
						}

						textureId = BlockTextureRegistry.INSTANCE.getOrRegisterBlockStateSetId(
							block, levelWrapper, fullDataSource, blockX, topBlockY + levelWrapper.getMinHeight(), blockZ);
					}
					catch (IndexOutOfBoundsException ignore)
					{
						// broken mappings are logged during color resolution, render flat here
					}
				}
				
				// when no data point contains the top block (IE merged across an air gap)
				// the data point renders flat
				break;
			}
			
			columnSource.setTextureSetId(sourceRelX, sourceRelZ, renderIndex, textureId);
		}
	}
	
	/**
	 * Grass keeps its own material under plants (IE tall grass),
	 * its sides are drawn as dirt based on the grass material (see LodQuadBuilder),
	 * with the plant's material they'd be drawn in the plant's green. <br>
	 * Snow still replaces it, snowy grass sides aren't green in MC either.
	 */
	private static IBlockStateWrapper keepGrassMaterial(IBlockStateWrapper block, IBlockStateWrapper avoidedBlockAbove)
	{
		if (block.getMaterialId() == EDhApiBlockMaterial.GRASS.index
			&& avoidedBlockAbove.getMaterialId() != EDhApiBlockMaterial.SNOW.index)
		{
			return block;
		}
		return avoidedBlockAbove;
	}

	private static void setRenderColumnView(
			IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
			int blockX, int blockZ,
			ColumnRenderView renderColumnData, LongArrayList fullColumnData, DhBlockPosMutable mutableBlockPos)
	{
		//===============//
		// config values //
		//===============//
		
		boolean ignoreNonCollidingBlocks = (Config.Client.Advanced.Graphics.Culling.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
		boolean colorBelowWithAvoidedBlocks = Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks.get();
		boolean grassKeepsColorUnderPlants = Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& fullDataSource.getDataDetailLevel() <= Config.Client.Advanced.Graphics.Texture.maxTexturedLodDetailLevel.get();
		// plants are only drawn by 1 block wide LODs, wider LODs are full boxes (see ColumnBox)
		boolean plantsAsCrosses = grassKeepsColorUnderPlants && fullDataSource.getDataDetailLevel() == 0;
		
		final ObjectOpenHashSet<IBlockStateWrapper> blockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredBlocks(levelWrapper);
		final ObjectOpenHashSet<IBlockStateWrapper> caveBlockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredCaveBlocks(levelWrapper);
		final ObjectOpenHashSet<IBlockStateWrapper> waterSubsurfaceReplacementBlocks = WRAPPER_FACTORY.getWaterSubsurfaceReplacementBlocks(levelWrapper);
		final ObjectOpenHashSet<IBlockStateWrapper> waterSurfaceReplacementBlocks = WRAPPER_FACTORY.getWaterSurfaceReplacementBlocks(levelWrapper);
		final IBlockStateWrapper water = WRAPPER_FACTORY.getWaterBlockStateWrapper(levelWrapper);
		
		
		// build snow block cache if needed
		if (snowLayerBlockStates == null)
		{
			snowLayerBlockStates = new HashSet<>();
			// ignore snow layers 1-3, everything above should be considered a full block.
			// 1.7.10 calls the layer block "minecraft:snow_layer" ("minecraft:snow" is the full block)
			// and stores the layer count minus one as its meta
			for (int meta = 0; meta <= 2; meta++)
			{
				String serial = (meta == 0) ? "minecraft:snow_layer" : ("minecraft:snow_layer:" + meta);
				IBlockStateWrapper snowLayer = WRAPPER_FACTORY.deserializeBlockStateWrapperOrGetDefault(serial, levelWrapper);

				// a failed lookup returns air, which must not be treated as a snow layer
				if (!snowLayer.isAir())
				{
					snowLayerBlockStates.add(snowLayer);
				}
			}
		}
		
		int caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - levelWrapper.getMinHeight();
		boolean caveCullingEnabled = 
			Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get()
			&& (
				// dimensions with a ceiling will be all caves so we don't want cave culling
				!levelWrapper.hasCeiling()
				// the end has a lot of overhangs with 0 lighting above the void, which look broken with
				// the current cave culling logic (this could probably be improved, but just skipping it works best for now)
				&& !levelWrapper.getDimensionType().isTheEnd()
			);
		
		boolean isColumnVoid = true;
		
		int lastColor = 0;
		int lastBottom = -10_000;
		IBlockStateWrapper lastBlock = null;
		byte lastShape = RenderDataPointUtil.SHAPE_NONE;
		
		// there are several instances where we'll want
		// to copy the top datapoint down and override the one below it
		int colorToApplyToNextBlock = -1;
		IBlockStateWrapper blockToApplyToNextBlock = null;
		int skylightToApplyToNextBlock = -1;
		int blocklightToApplyToNextBlock = -1;
		
		int renderDataIndex = 0;
		
		
		
		//==================================//
		// convert full data to render data //
		//==================================//
		
		FullDataPointIdMap fullDataMapping = fullDataSource.mapping;
		
		mutableBlockPos.setX(blockX);
		mutableBlockPos.setZ(blockZ);
		
		// goes from the top down
		for (int fullDataIndex = 0; fullDataIndex < fullColumnData.size(); fullDataIndex++)
		{
			long fullData = fullColumnData.getLong(fullDataIndex);
			
			int bottomY = FullDataPointUtil.getBottomY(fullData);
			int blockHeight = FullDataPointUtil.getHeight(fullData);
			int topY = bottomY + blockHeight;
			int id = FullDataPointUtil.getId(fullData);
			int blockLight = FullDataPointUtil.getBlockLight(fullData);
			int skyLight = FullDataPointUtil.getSkyLight(fullData);
			
			// using the top Y samples the surface biome instead of a cave biome for tall datapoints
			mutableBlockPos.setY(topY - 1 + levelWrapper.getMinHeight());
			
			IBiomeWrapper biome;
			IBlockStateWrapper block;
			try
			{
				biome = fullDataMapping.getBiomeWrapper(id);
				block = fullDataMapping.getBlockStateWrapper(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				if (!BROKEN_POS_SET.contains(fullDataMapping.getPos()))
				{
					BROKEN_POS_SET.add(fullDataMapping.getPos());
					String levelId = levelWrapper.getDhIdentifier();
					LOGGER.warn("Unable to get data point with id ["+id+"] " +
							"(Max possible ID: ["+fullDataMapping.getMaxValidId()+"]) " +
							"for pos ["+fullDataMapping.getPos()+"] in level ["+levelId+"]. " +
							"Error: ["+e.getMessage()+"]. " +
							"Further errors for this position won't be logged.");
				}
				
				// don't render broken data
				continue;
			}
			
			// taken before any overrides, IE a slab with a snow layer on it is still a slab
			byte shape = block.getLodShape();
			if (shape == RenderDataPointUtil.SHAPE_BOTTOM_HALF
				&& blockHeight != 1)
			{
				// the half is only applied to the top, a stack of slabs would look 1.5 blocks tall
				shape = RenderDataPointUtil.SHAPE_NONE;
			}
			
			//==================//
			// special case:    // 
			// air gap leak fix //
			//==================//
			
			if (block.isAir())
			{
				// we don't want to apply the last block over an air gap,
				// doing so can cause weird lighting behavior for cave berries
				// when hanging above water
				colorToApplyToNextBlock = -1;
				blockToApplyToNextBlock = null;
				skylightToApplyToNextBlock = -1;
				blocklightToApplyToNextBlock = -1;
			}
			
			
			
			//=================//
			// special case:   //
			// water under ice //
			//=================//
			
			// is this block water and the one above it ice?
			if (block.getMaterialId() == EDhApiBlockMaterial.WATER.index
				&& lastBlock != null
				&& lastBlock.isIceBlock())
			{
				// Replace the water with ice to prevent rendering water
				// under the frozen lakes (which looks bad).
				block = lastBlock;
			}
			
			
			
			//====================//
			// ignored block and  //
			// cave culling check //
			//====================//
			
			if (waterSubsurfaceReplacementBlocks.contains(block)
				&& (lastBlock == null || lastBlock.isAir()))
			{
				block = water;
			}
			
			boolean ignoreBlock = blockStatesToIgnore.contains(block);
			boolean caveBlock = caveBlockStatesToIgnore.contains(block);
			if (caveBlock
				// caves also ignore transparent/non-solid blocks (IE grass and plants) without each being defined
				|| !block.isSolid()
				|| block.isLiquid()
				|| block.getOpacity() < LodUtil.BLOCK_FULLY_OPAQUE)
			{
				if (caveCullingEnabled
					// assume this data point is underground if it has no sky-light
					&& skyLight == LodUtil.MIN_MC_LIGHT
					// ignore caves above a certain height to prevent floating islands from having walls underneath them
					&& topY < caveCullingMaxY
					// cave culling shouldn't happen when at the top of the world
					&& renderDataIndex != 0 && fullDataIndex != 0
					// cave culling can't happen when at the bottom of the world
					&& (fullDataIndex + 1) < fullColumnData.size())
				{
					// we need to get the next sky/block lights because
					// the air block here will always have a light of 0/0 due to only the top of the LOD's light being saved.
					long nextFullData = fullColumnData.getLong(fullDataIndex + 1);
					int nextSkyLight = FullDataPointUtil.getSkyLight(nextFullData);
					
					if (nextSkyLight == LodUtil.MIN_MC_LIGHT
						&& ColorUtil.getAlpha(lastColor) == 255)
					{
						// replace the previous block with new bottom
						long columnData = renderColumnData.get(renderDataIndex - 1);
						columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
						renderColumnData.set(renderDataIndex - 1, columnData);
					}
					
					continue;
				}
				
				
				if (ignoreBlock)
				{
					// if this is the bottom datapoint
					// save it so lighting appears properly for floating
					// islands over the void
					if (fullDataIndex == fullColumnData.size() - 1)
					{
						setVoidAir(
							renderColumnData, renderDataIndex,
							bottomY, blockHeight,
							skyLight, blockLight
						);
					}
					
					// this is a merged block and a cave block, so it shouldn't be rendered
					continue;
				}
			}
			else if (ignoreBlock)
			{
				// this is an ignored block, but shouldn't be merged like a cave block
				
				// applying this sky light to the next block should prevent black spots for opaque covering blocks 
				skylightToApplyToNextBlock = skyLight;
				continue;
			}
			
			
			
			//=======================//
			// non-solid block check //
			//=======================//
			
			boolean ignoreNonSolidBlock =
				ignoreNonCollidingBlocks
				&& !block.isSolid()
				&& !block.isLiquid()
				&& block.getOpacity() != LodUtil.BLOCK_FULLY_OPAQUE;
			
			// handle height reduction
			boolean isSnowLayer = snowLayerBlockStates.contains(block);
			boolean isWaterSurfaceReplacement = waterSurfaceReplacementBlocks.contains(block);
			if (isSnowLayer || isWaterSurfaceReplacement)
			{
				if (isWaterSurfaceReplacement)
				{
					// replace the block with water
					block = WRAPPER_FACTORY.getWaterBlockStateWrapper(levelWrapper);
				}
				
				// sometimes a datapoint will be multiple blocks tall,
				// in that case we just want to drop the top by 1
				blockHeight -= 1;
				if (blockHeight == 0)
				{
					// this block was entirely removed, just color the block below it
					ignoreNonSolidBlock = true;
					
					
					if (isSnowLayer)
					{
						// snow is a special case where it should always tint the block
						// below it, if not done grass will appear as gray
						int snowColor = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
						colorToApplyToNextBlock = ColorUtil.setAlpha(snowColor, 255);
						
						// the dirt/grass below the snow should be related with snow
						blockToApplyToNextBlock = block;
					}
					else //if (isWaterSurfaceReplacement)
					{
						colorToApplyToNextBlock = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
					}
				}
			}
			
			if (ignoreNonSolidBlock
				&& plantsAsCrosses
				&& block.isCrossPlant())
			{
				// close LODs draw plants (IE tall grass, flowers) as crossed planes like MC,
				// the block below keeps its own color and is lit by the air the plant is in
				block = block.getPlantTopWithBottom(getTouchingBlock(fullColumnData, fullDataIndex + 1, bottomY, false, fullDataMapping), levelWrapper);
				int plantColor = ColorUtil.setAlpha(levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block), 255);
				isColumnVoid = false;
				long columnData = RenderDataPointUtil.createDataPoint(bottomY + blockHeight, bottomY, plantColor, skyLight, blockLight, block.getMaterialId(), RenderDataPointUtil.SHAPE_CROSS);
				renderColumnData.set(renderDataIndex, columnData);
				renderDataIndex++;

				lastBottom = bottomY;
				lastColor = plantColor;
				lastBlock = block;
				lastShape = RenderDataPointUtil.SHAPE_CROSS;

				skylightToApplyToNextBlock = skyLight;
				blocklightToApplyToNextBlock = blockLight;
				continue;
			}

			if (ignoreNonSolidBlock)
			{
				int ignoredColor = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
				int ignoredAlpha = ColorUtil.getAlpha(ignoredColor);
				
				// light emitting blocks (fire, torches) only pass on their light,
				// otherwise netherrack under fire is painted fire colored
				if (colorBelowWithAvoidedBlocks
					&& block.getLightEmission() == 0)
				{
					// don't transfer the color when alpha is 0
					// this prevents issues if grass is transparent
					if (ignoredAlpha != 0)
					{
						colorToApplyToNextBlock = ColorUtil.setAlpha(ignoredColor, 255);
						// also copy over the material so shaders/textures render correctly
						blockToApplyToNextBlock = block;
					}
				}
				
				// Don't transfer the lighting when alpha is 0
				// (the block below should have its own lighting).
				if (ignoredAlpha != 0)
				{
					// Lighting is transferred even when "colorBelowWithAvoidedBlocks"
					// is false, since otherwise the blocks underneath may have a light value of "0"
					// which makes things look darker than they should.
					// This can specifically manifest as grid lines on LOD borders
					// (not entire sure why grid lines on LOD borders, maybe it has to do with the fact that those LODs aren't occluded?).
					skylightToApplyToNextBlock = skyLight;
					blocklightToApplyToNextBlock = blockLight;
				}
				
				
				// if this is the bottom datapoint
				// save it so lighting appears properly for floating
				// islands over the void
				if (fullDataIndex == fullColumnData.size() - 1)
				{
					setVoidAir(
						renderColumnData, renderDataIndex,
						bottomY, blockHeight,
						skyLight, blockLight
					);
				}
				
				// skip this non-colliding block
				continue;
			}
			
			
			// lava pockets hidden inside the Nether's netherrack are enclosed by solid blocks, in game they can't be seen
			// but LODs are coarser than the real walls so they poke through as orange dots, draw them as the rock above instead
			if (block.isLiquid()
				&& block.getLightEmission() > 0
				&& isFullyOpaqueBlockAt(fullColumnData, fullDataIndex + 1, bottomY, false, fullDataMapping))
			{
				IBlockStateWrapper blockAbove = getTouchingBlock(fullColumnData, fullDataIndex - 1, topY, true, fullDataMapping);
				if (blockAbove != null
					&& blockAbove.getOpacity() == LodUtil.BLOCK_FULLY_OPAQUE)
				{
					block = blockAbove;
				}
			}

			int color;
			// use the override values if necessary
			if (colorToApplyToNextBlock == -1
				// textured LODs are close enough to see the grass top, MC draws plants (IE tall grass) standing on it
				// instead of recoloring it, so the grass keeps its own color like the grass around it
				|| (grassKeepsColorUnderPlants
					&& block.getMaterialId() == EDhApiBlockMaterial.GRASS.index
					&& blockToApplyToNextBlock != null
					&& blockToApplyToNextBlock.getMaterialId() != EDhApiBlockMaterial.SNOW.index))
			{
				// use this block's color
				color = levelWrapper.getBlockColor(mutableBlockPos, biome, fullDataSource, block);
				// an ignored plant color mustn't carry over to the block below
				colorToApplyToNextBlock = -1;

				// use the skylight override if present
				if (skylightToApplyToNextBlock != -1)
				{
					skyLight = skylightToApplyToNextBlock;
					// remove the override so we don't accidentally override the next datapoint
					skylightToApplyToNextBlock = -1;
				}
				
				if (blocklightToApplyToNextBlock != -1)
				{
					blockLight = blocklightToApplyToNextBlock;
					blocklightToApplyToNextBlock = -1;
				}
				
				if (blockToApplyToNextBlock != null)
				{
					block = keepGrassMaterial(block, blockToApplyToNextBlock);
					blockToApplyToNextBlock = null;
				}
			}
			else
			{
				// use the previous block's info as available
				
				color = colorToApplyToNextBlock;
				colorToApplyToNextBlock = -1;
				
				// use the skylight override if present
				if (skylightToApplyToNextBlock != -1)
				{
					skyLight = skylightToApplyToNextBlock;
					// remove the override so we don't accidentally override the next datapoint
					skylightToApplyToNextBlock = -1;
				}

				if (blocklightToApplyToNextBlock != -1)
				{
					blockLight = blocklightToApplyToNextBlock;
					blocklightToApplyToNextBlock = -1;
				}

				if (blockToApplyToNextBlock != null)
				{
					block = keepGrassMaterial(block, blockToApplyToNextBlock);
					blockToApplyToNextBlock = null;
				}
			}

			if (block.getMaterialId() == EDhApiBlockMaterial.WATER.index)
			{
				color = blendSeaFloorIntoWater(color, fullColumnData, fullDataIndex, bottomY, fullDataMapping, levelWrapper, biome, mutableBlockPos, fullDataSource);
			}



			// light emitting blocks (lava, glowstone) light themselves. A LOD only stores the light
			// above it, which can be 0 for lava falls in the Nether or chunks lit before their neighbors
			int lightEmission = Math.min(block.getLightEmission(), LodUtil.MAX_MC_LIGHT);
			if (lightEmission > blockLight
				// lava pockets hidden inside the Nether's netherrack ceiling are enclosed by solid blocks,
				// lower detail LODs can still expose them and they shouldn't glow there
				&& !(isFullyOpaqueBlockAt(fullColumnData, fullDataIndex - 1, topY, true, fullDataMapping)
					&& isFullyOpaqueBlockAt(fullColumnData, fullDataIndex + 1, bottomY, false, fullDataMapping)))
			{
				blockLight = lightEmission;
			}
			else if (lightEmission == 0
				&& block.getOpacity() == LodUtil.BLOCK_FULLY_OPAQUE
				&& isCoveringBlock(getTouchingBlock(fullColumnData, fullDataIndex - 1, topY, true, fullDataMapping)))
			{
				// the stored light is the light inside the block sitting on this one, which MC keeps at 0 for opaque blocks.
				// Anything else is from an emitter sitting on it (IE netherrack under a lava pocket in the Nether ceiling)
				// or from lower detail LODs merging in a neighbor's hidden lava, both would light up random netherrack
				blockLight = LodUtil.MIN_MC_LIGHT;
			}



			//=============================//
			// merge same-colored adjacent //
			//=============================//
			
			// check if they share a top-bottom face and if they have same color
			// (shapes are only merged when they stay the same shape, a half block on a half block isn't a half block)
			if (color == lastColor 
				&& bottomY + blockHeight == lastBottom  
				&& renderDataIndex > 0
				&& shape == lastShape
				&& shape != RenderDataPointUtil.SHAPE_BOTTOM_HALF)
			{
				//replace the previous block with new bottom
				long columnData = renderColumnData.get(renderDataIndex - 1);
				columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
				renderColumnData.set(renderDataIndex - 1, columnData);
			}
			else
			{
				// add the block
				isColumnVoid = false;
				long columnData = RenderDataPointUtil.createDataPoint(bottomY + blockHeight, bottomY, color, skyLight, blockLight, block.getMaterialId(), shape);
				renderColumnData.set(renderDataIndex, columnData);
				renderDataIndex++;
			}
			lastBottom = bottomY;
			lastColor = color;
			lastBlock = block;
			lastShape = shape;
		}
		
		
		if (isColumnVoid)
		{
			renderColumnData.set(0, RenderDataPointUtil.EMPTY_DATA);
		}
	}

	/**
	 * MC's water is see-through, the sea floor shows through it, darker the deeper it is since water blocks light.
	 * LOD water shows the water's color alone, so the floor is blended into it with MC's light curve
	 * otherwise LOD water is a lot brighter than the vanilla water next to it.
	 *
	 * @param index the water's data point, the column is ordered top to bottom
	 * @return the water's color with the floor blended in, unchanged if the water is opaque or has no floor
	 */
	private static int blendSeaFloorIntoWater(
		int waterColor, LongArrayList fullColumnData, int index, int waterBottomY,
		FullDataPointIdMap fullDataMapping, IClientLevelWrapper levelWrapper, IBiomeWrapper biome,
		DhBlockPosMutable blockPos, FullDataSourceV2 fullDataSource)
	{
		int waterAlpha = ColorUtil.getAlpha(waterColor);
		if (waterAlpha == 255
			// shader packs draw their own water (depth, reflections) from the water's color
			|| (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isShaderPackInUse()))
		{
			return waterColor;
		}

		// the water may be split into several data points, the floor is under the last one
		IBlockStateWrapper floor = getTouchingBlock(fullColumnData, index + 1, waterBottomY, false, fullDataMapping);
		while (floor != null && floor.getMaterialId() == EDhApiBlockMaterial.WATER.index)
		{
			index++;
			waterBottomY = FullDataPointUtil.getBottomY(fullColumnData.getLong(index));
			floor = getTouchingBlock(fullColumnData, index + 1, waterBottomY, false, fullDataMapping);
		}
		if (floor == null || floor.isAir() || floor.isLiquid())
		{
			return waterColor;
		}

		// the floor's stored light is the light in the water right above it
		long floorData = fullColumnData.getLong(index + 1);
		int light = Math.max(FullDataPointUtil.getSkyLight(floorData), FullDataPointUtil.getBlockLight(floorData));
		float lightRatio = light / (float) LodUtil.MAX_MC_LIGHT;
		// MC's (1.7.10) lightmap brightness for a light level
		float floorBrightness = lightRatio / ((1.0f - lightRatio) * 3.0f + 1.0f);

		int floorColor = levelWrapper.getBlockColor(blockPos, biome, fullDataSource, floor);
		float alpha = waterAlpha / 255.0f;
		// the alpha is kept so the water is still drawn with the transparent LODs
		return ColorUtil.argbToInt(waterAlpha,
			blendWaterChannel(ColorUtil.getRed(waterColor), ColorUtil.getRed(floorColor), floorBrightness, alpha),
			blendWaterChannel(ColorUtil.getGreen(waterColor), ColorUtil.getGreen(floorColor), floorBrightness, alpha),
			blendWaterChannel(ColorUtil.getBlue(waterColor), ColorUtil.getBlue(floorColor), floorBrightness, alpha));
	}
	/** blended like MC's blending, without linearizing */
	private static int blendWaterChannel(int water, int floor, float floorBrightness, float alpha)
	{ return Math.round(water * alpha + floor * floorBrightness * (1.0f - alpha)); }

	/** @see #getTouchingBlock */
	private static boolean isFullyOpaqueBlockAt(LongArrayList fullColumnData, int index, int touchingY, boolean isAbove, FullDataPointIdMap fullDataMapping)
	{
		IBlockStateWrapper neighborBlock = getTouchingBlock(fullColumnData, index, touchingY, isAbove, fullDataMapping);
		return neighborBlock != null && neighborBlock.getOpacity() == LodUtil.BLOCK_FULLY_OPAQUE;
	}

	/**
	 * @return true for opaque blocks and rendered light emitters (lava) that cover the block below them,
	 * fire and torches don't cover it
	 */
	private static boolean isCoveringBlock(@Nullable IBlockStateWrapper block)
	{
		return block != null
			&& (block.getOpacity() == LodUtil.BLOCK_FULLY_OPAQUE
				|| (block.isLiquid() && block.getLightEmission() > 0));
	}

	/**
	 * @param index the neighboring data point, the column is ordered top to bottom
	 * @param touchingY the Y the neighbor has to start or end at to be touching
	 * @param isAbove true if the neighbor is above, then its bottom has to be at touchingY, otherwise its top
	 * @return the neighbor's block, null if that data point doesn't exist or doesn't touch
	 */
	@Nullable
	private static IBlockStateWrapper getTouchingBlock(LongArrayList fullColumnData, int index, int touchingY, boolean isAbove, FullDataPointIdMap fullDataMapping)
	{
		if (index < 0 || index >= fullColumnData.size())
		{
			return null;
		}

		long fullData = fullColumnData.getLong(index);
		int neighborBottomY = FullDataPointUtil.getBottomY(fullData);
		int neighborTouchingY = isAbove ? neighborBottomY : (neighborBottomY + FullDataPointUtil.getHeight(fullData));
		if (neighborTouchingY != touchingY)
		{
			return null;
		}

		try
		{
			return fullDataMapping.getBlockStateWrapper(FullDataPointUtil.getId(fullData));
		}
		catch (IndexOutOfBoundsException e)
		{
			return null;
		}
	}

	/**
	 * Necessary to handle adjacent sky/block lighting
	 * over the void.
	 */
	private static void setVoidAir(
		ColumnRenderView renderColumnData, int renderDataIndex,
		int bottomY, int blockHeight,
		int skyLight, int blockLight)
	{
		// get the last render point (if present)
		long lastDatapoint = RenderDataPointUtil.EMPTY_DATA;
		if (renderDataIndex > 0)
		{
			lastDatapoint = renderColumnData.get(renderDataIndex - 1);
		}
		
		// Get the max y position from the previous render data point's bottom Y.
		// This is necessary for lighting to appear correctly for adjacent air data points over the void.
		int maxY = (bottomY + blockHeight);
		if (lastDatapoint != RenderDataPointUtil.EMPTY_DATA)
		{
			maxY = RenderDataPointUtil.getYMin(lastDatapoint);
		}
		
		long columnData = RenderDataPointUtil.createDataPoint(
			maxY, 0, // min Y since this datapoint should go all the way down to the void
			ColorUtil.INVISIBLE, skyLight, blockLight,
			// use air's material since this datapoint shouldn't be rendered, 
			// it's only present to handle sky/block lighting
			EDhApiBlockMaterial.AIR.index);
		renderColumnData.setVoid(columnData);
	}
	
	//endregion
	
	
	
}
