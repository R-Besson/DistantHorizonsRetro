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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ColumnBox
{
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	/** 
	 * if the skylight has this value that means
	 * that block position is covered/occluded by an adjacent block/column.
	 */
	private static final byte SKYLIGHT_COVERED = -1;
	/** block light falloff on vertical faces is done in steps this tall to keep the quad count down */
	private static final int BLOCK_LIGHT_FALLOFF_STEP_HEIGHT = 3;
	
	
	
	//=========//
	// builder //
	//=========//
	//region
	
	public static void addBoxQuadsToBuilder(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IClientLevelWrapper clientLevelWrapper,
		short blockWidth, short yHeight,
		short minX, short minY, short minZ,
		int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
		long topData, long bottomData, 
		ColumnRenderView[] adjData, boolean[] isAdjDataSameDetailLevel,
		short @Nullable [] faceTileIdsByDirectionOrdinal)
	{
		//================//
		// variable setup //
		//================//
		
		short maxX = (short) (minX + blockWidth);
		short maxY = (short) (minY + yHeight);
		short maxZ = (short) (minZ + blockWidth);
		byte skyLightTop = skyLight;
		byte skyLightBottom = RenderDataPointUtil.doesDataPointExist(bottomData) ? RenderDataPointUtil.getLightSky(bottomData) : LodUtil.MIN_MC_LIGHT;
		
		boolean transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get() == EDhApiTransparency.COMPLETE;
		
		boolean isTransparent = ColorUtil.getAlpha(color) < 255 && transparencyEnabled;
		boolean overVoid = !RenderDataPointUtil.doesDataPointExist(bottomData);
		boolean isTopTransparent = RenderDataPointUtil.getAlpha(topData) < 255 && transparencyEnabled;
		boolean isBottomTransparent = RenderDataPointUtil.getAlpha(bottomData) < 255 && transparencyEnabled;
		
		// defaulting to a value far below what we can normally render means we
		// don't need to have an additional "is cave culling enabled" check
		int caveCullingMaxY = Integer.MIN_VALUE;
		if (Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get())
		{
			caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - clientLevelWrapper.getMinHeight();
		}
		
		
		
		// if there isn't any data below this LOD, make this LOD's color opaque to prevent seeing void through transparent blocks
		// Note: this LOD should still be considered transparent for this method's checks, otherwise rendering bugs may occur
		if (!RenderDataPointUtil.doesDataPointExist(bottomData))
		{
			color = ColorUtil.setAlpha(color, 255);
		}
		
		// never let air render,
		// air is only used for holding sky/block light data
		if (irisBlockMaterialId == EDhApiBlockMaterial.AIR.index)
		{
			return;
		}
		
		
		
		//==========================//
		// add top and bottom faces //
		//==========================//
		
		// top face
		{
			boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData)
					&& (RenderDataPointUtil.getYMin(topData) == maxY)
					&& !isTopTransparent;
			
			if (!skipTop)
			{
				builder.addQuadUp(
					minX, maxY, minZ, 
					blockWidth, 
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(EDhDirection.UP)), tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.UP),
					irisBlockMaterialId, 
					skyLightTop, blockLight);
			}
		}
		
		// bottom face 
		{
			boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData)
					&& (RenderDataPointUtil.getYMax(bottomData) == minY)
					&& !isBottomTransparent;
			if (!skipBottom)
			{
				builder.addQuadDown(
					minX, minY, minZ, 
					blockWidth, 
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(EDhDirection.DOWN)), tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.DOWN), 
					irisBlockMaterialId, 
					skyLightBottom, blockLight);
			}
		}
		
		
		
		//========================================//
		// add North, south, east, and west faces //
		//========================================//
		
		// NORTH face
		{
			ColumnRenderView adjCol = adjData[EDhDirection.NORTH.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.NORTH.compassIndex];
			// if the adjacent column is null that generally means the adjacent area hasn't been generated yet
			if (adjCol == null)
			{
				// Add an adjacent face if this is opaque face or transparent over the void.
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(
						EDhDirection.NORTH, 
						minX, minY, minZ, 
						blockWidth, yHeight, 
						color, tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.NORTH),
						irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(
					builder, phantomArrayCheckout, clientLevelWrapper,
					adjCol, faceTileIdsByDirectionOrdinal,
					adjSameDetailLevel, caveCullingMaxY, EDhDirection.NORTH, 
					minX, minY, minZ, blockWidth, yHeight,
					color, irisBlockMaterialId, blockLight);
			}
		}
		
		// SOUTH face
		{
			ColumnRenderView adjCol = adjData[EDhDirection.SOUTH.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.SOUTH.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(
						EDhDirection.SOUTH, 
						minX, minY, maxZ, 
						blockWidth, yHeight, 
						color, tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.SOUTH),
						irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(
					builder, phantomArrayCheckout, clientLevelWrapper,
					adjCol, faceTileIdsByDirectionOrdinal,
					adjSameDetailLevel, caveCullingMaxY, EDhDirection.SOUTH,
					minX, minY, maxZ, blockWidth, yHeight,
					color, irisBlockMaterialId, blockLight);
			}
		}
		
		// WEST face
		{
			ColumnRenderView adjCol = adjData[EDhDirection.WEST.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.WEST.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(
						EDhDirection.WEST, 
						minX, minY, minZ, 
						blockWidth, yHeight, 
						color, tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.WEST),
						irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(
					builder, phantomArrayCheckout, clientLevelWrapper,
					adjCol, faceTileIdsByDirectionOrdinal,
					adjSameDetailLevel, caveCullingMaxY, EDhDirection.WEST, 
					minX, minY, minZ, blockWidth, yHeight,
					color, irisBlockMaterialId, blockLight);
			}
		}
		
		// EAST face
		{
			ColumnRenderView adjCol = adjData[EDhDirection.EAST.compassIndex];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.EAST.compassIndex];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(
						EDhDirection.EAST, 
						maxX, minY, minZ, 
						blockWidth, yHeight, 
						color, tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.EAST),
						irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(
					builder, phantomArrayCheckout, clientLevelWrapper,
					adjCol, faceTileIdsByDirectionOrdinal,
					adjSameDetailLevel, caveCullingMaxY, EDhDirection.EAST, 
					maxX, minY, minZ, blockWidth, yHeight,
					color, irisBlockMaterialId, blockLight);
			}
		}
	}
	
	private static void makeAdjVerticalQuad(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IClientLevelWrapper clientLevelWrapper,
		@NotNull ColumnRenderView adjColumnView, short @Nullable [] faceTileIdsByDirectionOrdinal,
		boolean adjacentIsSameDetailLevel, int caveCullingMaxY, EDhDirection direction,
		short x, short yMin, short z, short horizontalBlockWidth, short ySize,
		int color, byte irisBlockMaterialId, byte blockLight)
	{
		// pooled arrays
		LongArrayList segments = phantomArrayCheckout.getLongArray(0, 0);
		LongArrayList newSegments = phantomArrayCheckout.getLongArray(1, 0);
		
		
		
		//==================//
		// create face with //
		// no adjacent data //
		//==================//
		
		color = ColorUtil.applyShade(color, clientLevelWrapper.getShade(direction));
		
		if (adjColumnView.size == 0
			|| RenderDataPointUtil.hasZeroHeight(adjColumnView.get(0)))
		{
			builder.addQuadAdj(
				direction, 
				x, yMin, z, 
				horizontalBlockWidth, ySize, 
				color, tryGetTextureId(faceTileIdsByDirectionOrdinal, direction),
				irisBlockMaterialId, 
				LodUtil.MAX_MC_LIGHT, blockLight);
			return;
		}
		
		
		
		//=================================//
		// determine face visibility/light //
		//=================================//
		
		boolean transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get() == EDhApiTransparency.COMPLETE;
		boolean inputTransparent = ColorUtil.getAlpha(color) < 255 && transparencyEnabled;
		short yMax = (short) (yMin + ySize);
		
		
		int adjCount = adjColumnView.size;
		
		// Start with the entire range at max light.
		// Block light is replaced with the light of the air next to the face where the adjacent column has some,
		// otherwise a light on top of this column (IE fire on a hill) would light its whole side
		segments.add(YSegmentUtil.encode(yMin, yMax, LodUtil.MAX_MC_LIGHT, blockLight, false));
		
		// Process each adjacent datapoint and split segments as needed
		// (index -1 is a special case for the void datapoint)
		for (int adjIndex = -1; adjIndex < adjCount; adjIndex++)
		{
			long adjPoint = (adjIndex != -1) 
				? adjColumnView.get(adjIndex) 
				: adjColumnView.getVoid();
			short adjMinY = RenderDataPointUtil.getYMin(adjPoint);
			short adjMaxY = RenderDataPointUtil.getYMax(adjPoint);
			
			// skip empty adjacent points
			// or points below this one
			if (!RenderDataPointUtil.doesDataPointExist(adjPoint)
				|| RenderDataPointUtil.hasZeroHeight(adjPoint)
				|| yMax <= adjMinY)
			{
				continue;
			}
			
			
			long adjAbovePoint = (adjIndex > 0) ? adjColumnView.get(adjIndex - 1) : RenderDataPointUtil.EMPTY_DATA;
			
			long adjBelowPoint;
			if (adjIndex != -1)
			{
				adjBelowPoint = (adjIndex + 1 < adjCount) 
					? adjColumnView.get(adjIndex + 1) 
					: adjColumnView.getVoid();
			}
			else
			{
				adjBelowPoint = adjColumnView.getVoid();
			}
			
			// the datapoint below this may be empty due to how datapoint reducing
			// is done. In that case use the void datapoint.
			if (adjBelowPoint == RenderDataPointUtil.EMPTY_DATA)
			{
				adjBelowPoint = adjColumnView.getVoid();
			}
			
			boolean adjOverVoid = !RenderDataPointUtil.doesDataPointExist(adjBelowPoint);
			boolean adjTransparent = 
				!adjOverVoid
				&& RenderDataPointUtil.getAlpha(adjPoint) < 255
				&& transparencyEnabled;
			
			byte adjSkyLight = RenderDataPointUtil.getLightSky(adjPoint);
			// a data point's light is the light of the air right above it
			byte adjBlockLight = RenderDataPointUtil.getLightBlock(adjPoint);
			byte lightToApply;
			byte blockLightToApply;
			
			if (!adjTransparent)
			{
				// Adjacent is opaque
				
				// The following logic is done to provide a little bit of overdraw to
				// prevent holes when low detail LODs are replaced by higher-detail ones
				// when moving.
				// If not done higher quality LODs can cause holes due to not 
				// covering the whole face like the lower detail LODs they replaced,
				// while still culling most LODs that are covered by other blocks.
				
				boolean onBorder = 
					(direction == EDhDirection.WEST && x == 0)
					|| (direction == EDhDirection.NORTH && z == 0)
					|| (direction == EDhDirection.EAST && x == ((horizontalBlockWidth) * (ColumnRenderSource.WIDTH)))
					|| (direction == EDhDirection.SOUTH && z == ((horizontalBlockWidth) * (ColumnRenderSource.WIDTH)));
				
				boolean isLit =
					RenderDataPointUtil.getLightSky(adjPoint) != LodUtil.MIN_MC_LIGHT
					|| RenderDataPointUtil.getLightBlock(adjPoint) != LodUtil.MIN_MC_LIGHT;
				
				// render the face if...
				boolean useAdjLighting =
					// we're on the border... (holes can only happen on LOD borders since faces inside an LOD will always be the same detail level)
					onBorder
					// ...this face has some sort of lighting... (0 light generally means the face is covered by other blocks)
					&& isLit
					// ...and is above the culling height
					&& RenderDataPointUtil.getYMax(adjPoint) >= caveCullingMaxY;
				
				lightToApply = useAdjLighting ? adjSkyLight : SKYLIGHT_COVERED;
				blockLightToApply = useAdjLighting ? adjBlockLight : LodUtil.MIN_MC_LIGHT;
			}
			else
			{
				// Adjacent is transparent, use below light
				lightToApply = RenderDataPointUtil.getLightSky(adjBelowPoint);
				blockLightToApply = RenderDataPointUtil.getLightBlock(adjBelowPoint);
			}


			// Apply light to the range [adjMinY, adjMaxY)
			applyLightToRangeAndPopulateNewSgements(segments, newSegments, adjMinY, adjMaxY, lightToApply, blockLightToApply, true);
			{
				// swap references so we can use the newly populated segments
				LongArrayList temp = segments;
				segments = newSegments;
				newSegments = temp;
			}

			// Fill overhang area [adjMaxY, adjAboveMinY) with adjSkyLight,
			// or everything above the adjacent column's top
			short adjAboveMinY = (adjIndex == 0) ? yMax : RenderDataPointUtil.getYMin(adjAbovePoint);
			if (adjMaxY < adjAboveMinY)
			{
				// the topmost air keeps its max sky light (min is taken)
				byte overhangSkyLight = (adjIndex == 0) ? LodUtil.MAX_MC_LIGHT : adjSkyLight;

				// the stored block light is the light right above the adjacent point,
				// it drops by one per block going up like it does in MC, otherwise a lava sea lights cliffs all the way to the ceiling
				short stepStartY = adjMaxY;
				while (stepStartY < adjAboveMinY)
				{
					byte stepBlockLight = (byte) Math.max(LodUtil.MIN_MC_LIGHT, adjBlockLight - (stepStartY - adjMaxY));
					short stepEndY = (stepBlockLight == LodUtil.MIN_MC_LIGHT)
						? adjAboveMinY
						: (short) Math.min(stepStartY + BLOCK_LIGHT_FALLOFF_STEP_HEIGHT, adjAboveMinY);

					applyLightToRangeAndPopulateNewSgements(segments, newSegments, stepStartY, stepEndY, overhangSkyLight, stepBlockLight, false);
					{
						LongArrayList temp = segments;
						segments = newSegments;
						newSegments = temp;
					}

					stepStartY = stepEndY;
				}
			}
		}
		
		
		
		//=======================//
		// Create vertical faces //
		// from segments         //
		//=======================//
		
		boolean selfLit = irisBlockMaterialId == EDhApiBlockMaterial.LAVA.index
			|| irisBlockMaterialId == EDhApiBlockMaterial.ILLUMINATED.index;
		for (int i = 0; i < segments.size(); i++)
		{
			long segment = segments.getLong(i);
			tryAddVerticalFaceWithSkyLightToBuilder(
				builder, direction,
				x, z, horizontalBlockWidth,
				color, tryGetTextureId(faceTileIdsByDirectionOrdinal, direction),
				irisBlockMaterialId,
				// light emitting blocks (lava falls) light their own faces, the falloff is only for light from the neighbors
				selfLit ? (byte) Math.max(blockLight, YSegmentUtil.getBlockLight(segment)) : YSegmentUtil.getBlockLight(segment),
				YSegmentUtil.getSkyLight(segment), YSegmentUtil.isCovered(segment),
				inputTransparent, 
				YSegmentUtil.getEndY(segment), YSegmentUtil.getStartY(segment)
			);
		}
	}
	
	/**
	 * Apply the new light value over the given y range,
	 * splitting segments as needed
	 * and putting the new segments into "newSegments"
	 * <p>
	 * source: claude.ai
	 */
	private static void applyLightToRangeAndPopulateNewSgements(
			LongArrayList segments, LongArrayList newSegments, 
			short rangeStart, short rangeEnd,
			byte newLight, byte newBlockLight, boolean coversRange)
	{
		// clear the pooled array that the new segments will go into
		newSegments.clear();

		for (int i = 0; i < segments.size(); i++)
		{
			long seg = segments.getLong(i);
			short endY = YSegmentUtil.getEndY(seg);
			short startY = YSegmentUtil.getStartY(seg);
			byte skyLight = YSegmentUtil.getSkyLight(seg);
			byte blockLight = YSegmentUtil.getBlockLight(seg);
			boolean covered = YSegmentUtil.isCovered(seg);

			// No overlap
			if (endY <= rangeStart 
				|| startY >= rangeEnd)
			{
				newSegments.add(seg);
				continue;
			}
			
			// Partial or complete overlap - need to split
			
			// Part before the range
			if (startY < rangeStart)
			{
				newSegments.add(YSegmentUtil.encode(startY, rangeStart, skyLight, blockLight, covered));
			}

			// Overlapping part - take minimum sky light, the block light is replaced
			short overlapStart = (short)Math.max(startY, rangeStart);
			short overlapEnd = (short)Math.min(endY, rangeEnd);
			byte minLight = (byte) Math.min(newLight, skyLight);
			newSegments.add(YSegmentUtil.encode(overlapStart, overlapEnd, minLight, newBlockLight, covered || coversRange));

			// Part after the range
			if (endY > rangeEnd)
			{
				newSegments.add(YSegmentUtil.encode(rangeEnd, endY, skyLight, blockLight, covered));
			}
		}
	}
	
	private static void tryAddVerticalFaceWithSkyLightToBuilder(
		LodQuadBuilder builder, EDhDirection direction,
		short x, short z, short horizontalWidth,
		int color, short faceTextureId, 
		byte irisBlockMaterialId, 
		byte blockLight, byte lastSkyLight, boolean covered,
		boolean inputTransparent, 
		int quadTopY, int quadBottomY
		)
	{
		// invalid positions will have a negative skylight
		if (lastSkyLight < 0)
		{
			return;
		}
		
		// Don't add transparent vertical faces
		// unless the adjacent position is empty.
		// Sky light can't be used for this, it's below max under overhangs and in dimensions without a sky.
		// This is done to prevent walls between water blocks in the ocean.
		if (inputTransparent
			&& covered)
		{
			return;
		}
		
		// don't add negative/empty height faces
		short height = (short) (quadTopY - quadBottomY);
		if (height <= 0)
		{
			return;
		}
		
		builder.addQuadAdj(
			direction, 
			x, (short) quadBottomY, z, 
			horizontalWidth, height, 
			color, faceTextureId,
			irisBlockMaterialId, lastSkyLight, blockLight);
	}
	
	//endregion
	
	
	
	//================//
	// helper methods //
	//================//
	//region
	
	private static short tryGetTextureId(short @Nullable [] faceTileIdsByDirectionOrdinal, EDhDirection direction)
	{
		if (faceTileIdsByDirectionOrdinal == null)
		{
			return BlockTextureRegistry.UNTEXTURED_ID;
		}
		
		return faceTileIdsByDirectionOrdinal[direction.faceIndex];
	}
	
	//endregion
	
	
	
	//================//
	// helper classes //
	//================//
	//region
	
	/** 
	 * encodes height/light data into a long
	 * to reduce object allocations.
	 */
	private static class YSegmentUtil
	{
		private static final int HEIGHT_WIDTH = Short.SIZE;
		private static final int SKY_LIGHT_WIDTH = Byte.SIZE;
		
		private static final int START_Y_MASK = (int) Math.pow(2, HEIGHT_WIDTH) - 1;
		private static final int END_Y_MASK = (int) Math.pow(2, HEIGHT_WIDTH) - 1;
		private static final int SKY_LIGHT_MASK = (int) Math.pow(2, SKY_LIGHT_WIDTH) - 1;
		
		private static final int START_Y_OFFSET = 0;
		private static final int END_Y_OFFSET = START_Y_OFFSET + HEIGHT_WIDTH;
		private static final int SKY_LIGHT_OFFSET = END_Y_OFFSET + HEIGHT_WIDTH;
		private static final int BLOCK_LIGHT_OFFSET = SKY_LIGHT_OFFSET + SKY_LIGHT_WIDTH;
		/** set when an adjacent datapoint covers the segment, overhangs only darken it */
		private static final int COVERED_OFFSET = BLOCK_LIGHT_OFFSET + SKY_LIGHT_WIDTH;



		public static long encode(short startY, short endY, byte skyLight, byte blockLight, boolean covered)
		{
			long data = 0L;
			data |= (long) (startY & START_Y_MASK) << START_Y_OFFSET;
			data |= (long) (endY & END_Y_MASK) << END_Y_OFFSET;
			data |= (long) (skyLight & SKY_LIGHT_MASK) << SKY_LIGHT_OFFSET;
			data |= (long) (blockLight & SKY_LIGHT_MASK) << BLOCK_LIGHT_OFFSET;
			data |= (covered ? 1L : 0L) << COVERED_OFFSET;
			return data;
		}

		public static short getStartY(long data) { return (short) ((data >> START_Y_OFFSET) & START_Y_MASK); }
		public static short getEndY(long data) { return (short) ((data >> END_Y_OFFSET) & END_Y_MASK); }
		public static byte getSkyLight(long data) { return (byte) ((data >> SKY_LIGHT_OFFSET) & SKY_LIGHT_MASK); }
		public static byte getBlockLight(long data) { return (byte) ((data >> BLOCK_LIGHT_OFFSET) & SKY_LIGHT_MASK); }
		public static boolean isCovered(long data) { return ((data >> COVERED_OFFSET) & 1L) != 0; }
		
	}
	
	//endregion
	
	
	
}
