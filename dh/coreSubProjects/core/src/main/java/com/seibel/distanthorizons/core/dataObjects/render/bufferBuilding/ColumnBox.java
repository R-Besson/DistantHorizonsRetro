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
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ColumnBox
{
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
	
	/**
	 * @param shape only used when the blockWidth is 1, see {@link RenderDataPointUtil#SHAPE_NONE}
	 * @param sideRowTileIds see {@link BlockTextureRegistry#getSideRowTileIds}
	 */
	public static void addBoxQuadsToBuilder(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IClientLevelWrapper clientLevelWrapper,
		short blockWidth, short yHeight,
		short minX, short minY, short minZ,
		int color, byte irisBlockMaterialId, byte skyLight, byte blockLight, byte shape,
		long topData, long bottomData,
		ColumnRenderView[] adjData, boolean[] isAdjDataSameDetailLevel,
		short @Nullable [] faceTileIdsByDirectionOrdinal, short @Nullable [][] sideRowTileIds)
	{
		// the closest LODs draw leaves like MC's fancy leaves, with see-through gaps and the faces between them,
		// read once here since the config getters are slow enough to show up when checked for every neighbor
		boolean seeThroughLeavesEnabled = blockWidth == 1
			&& Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& Config.Client.Advanced.Graphics.Texture.seeThroughLeaves.get();
		boolean seeThroughLeaves = seeThroughLeavesEnabled
			&& irisBlockMaterialId == EDhApiBlockMaterial.LEAVES.index
			&& faceTileIdsByDirectionOrdinal != null;

		builder.shapeForNewQuads = shape;
		builder.seeThroughForNewQuads = seeThroughLeaves;
		builder.sideRowTileIdsForNewQuads = sideRowTileIds;
		builder.sideRowTopYForNewQuads = (short) (minY + yHeight);
		try
		{
			addBoxQuads(
				builder, phantomArrayCheckout, clientLevelWrapper,
				blockWidth, yHeight,
				minX, minY, minZ,
				color, irisBlockMaterialId, skyLight, blockLight, shape,
				topData, bottomData,
				adjData, isAdjDataSameDetailLevel,
				faceTileIdsByDirectionOrdinal, seeThroughLeaves, seeThroughLeavesEnabled);
		}
		finally
		{
			builder.shapeForNewQuads = RenderDataPointUtil.SHAPE_NONE;
			builder.seeThroughForNewQuads = false;
			builder.sideRowTileIdsForNewQuads = null;
		}
	}
	private static void addBoxQuads(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IClientLevelWrapper clientLevelWrapper,
		short blockWidth, short yHeight,
		short minX, short minY, short minZ,
		int color, byte irisBlockMaterialId, byte skyLight, byte blockLight, byte shape,
		long topData, long bottomData,
		ColumnRenderView[] adjData, boolean[] isAdjDataSameDetailLevel,
		short @Nullable [] faceTileIdsByDirectionOrdinal, boolean seeThroughLeaves, boolean seeThroughLeavesEnabled)
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

		// plants are crossed planes instead of a box
		// (they only exist in 1 block wide LODs, see FullDataToRenderDataTransformer)
		if (shape == RenderDataPointUtil.SHAPE_CROSS)
		{
			builder.addCrossQuads(
				minX, minY, minZ,
				blockWidth, yHeight,
				color, tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.NORTH),
				irisBlockMaterialId,
				skyLight, blockLight);
			return;
		}
		
		
		
		//==========================//
		// add top and bottom faces //
		//==========================//
		
		// top face
		{
			// a bottom half's top is below the block above it,
			// and a post above only covers part of this top
			boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData)
					&& (RenderDataPointUtil.getYMin(topData) == maxY)
					&& !isTopTransparent
					&& shape != RenderDataPointUtil.SHAPE_BOTTOM_HALF
					&& !isPost(getShape(topData, blockWidth))
					&& getShape(topData, blockWidth) != RenderDataPointUtil.SHAPE_CROSS
					&& !isSeeThroughLeaves(topData, seeThroughLeavesEnabled);
			
			if (!skipTop)
			{
				// MC doesn't smooth light emitting blocks, they're drawn evenly lit
				boolean selfLit = irisBlockMaterialId == EDhApiBlockMaterial.LAVA.index
					|| irisBlockMaterialId == EDhApiBlockMaterial.ILLUMINATED.index;
				int cornerLights = selfLit
					? BufferQuad.UNIFORM_CORNER_LIGHTS
					: getSmoothTopCornerLights(adjData, maxY, skyLightTop, blockLight);

				builder.addQuadUp(
					minX, maxY, minZ,
					blockWidth,
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(EDhDirection.UP)), tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.UP),
					irisBlockMaterialId,
					skyLightTop, blockLight,
					cornerLights);
			}
		}
		
		// bottom face 
		{
			// shaped blocks below don't reach or don't cover this bottom
			boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData)
					&& (RenderDataPointUtil.getYMax(bottomData) == minY)
					&& !isBottomTransparent
					&& getShape(bottomData, blockWidth) == RenderDataPointUtil.SHAPE_NONE
					&& !isSeeThroughLeaves(bottomData, seeThroughLeavesEnabled);
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

		// see-through leaves stacked in one LOD also show the faces between each leaf block
		if (seeThroughLeaves)
		{
			for (int faceY = minY + 1; faceY < maxY; faceY++)
			{
				builder.addQuadUp(
					minX, (short) faceY, minZ,
					blockWidth,
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(EDhDirection.UP)), tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.UP),
					irisBlockMaterialId,
					skyLight, blockLight);
				builder.addQuadDown(
					minX, (short) faceY, minZ,
					blockWidth,
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(EDhDirection.DOWN)), tryGetTextureId(faceTileIdsByDirectionOrdinal, EDhDirection.DOWN),
					irisBlockMaterialId,
					skyLight, blockLight);
			}
		}

		
		
		//========================================//
		// add North, south, east, and west faces //
		//========================================//
		
		if (isPost(shape))
		{
			// posts are thinner than the neighbors that would cover them, so their sides are always drawn,
			// lit by the air around them
			for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
			{
				short faceX = (direction == EDhDirection.EAST) ? maxX : minX;
				short faceZ = (direction == EDhDirection.SOUTH) ? maxZ : minZ;
				builder.addQuadAdj(
					direction,
					faceX, minY, faceZ,
					blockWidth, yHeight,
					ColorUtil.applyShade(color, clientLevelWrapper.getShade(direction)), tryGetTextureId(faceTileIdsByDirectionOrdinal, direction),
					irisBlockMaterialId, skyLight, blockLight);
			}
			return;
		}
		
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
					color, irisBlockMaterialId, blockLight,
					transparencyEnabled, seeThroughLeavesEnabled);
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
					color, irisBlockMaterialId, blockLight,
					transparencyEnabled, seeThroughLeavesEnabled);
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
					color, irisBlockMaterialId, blockLight,
					transparencyEnabled, seeThroughLeavesEnabled);
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
					color, irisBlockMaterialId, blockLight,
					transparencyEnabled, seeThroughLeavesEnabled);
			}
		}
	}
	
	private static void makeAdjVerticalQuad(
		LodQuadBuilder builder, PhantomArrayListCheckout phantomArrayCheckout, IClientLevelWrapper clientLevelWrapper,
		@NotNull ColumnRenderView adjColumnView, short @Nullable [] faceTileIdsByDirectionOrdinal,
		boolean adjacentIsSameDetailLevel, int caveCullingMaxY, EDhDirection direction,
		short x, short yMin, short z, short horizontalBlockWidth, short ySize,
		int color, byte irisBlockMaterialId, byte blockLight,
		boolean transparencyEnabled, boolean seeThroughLeavesEnabled)
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
			// where the overhang above this point ends, or everything above the adjacent column's top
			short adjAboveMinY = (adjIndex == 0) ? yMax : RenderDataPointUtil.getYMin(adjAbovePoint);

			// the segments only cover this face's height, so a point and overhang
			// that end below the face wouldn't change any of them
			if (adjMaxY <= yMin
				&& adjAboveMinY <= yMin)
			{
				// the column's points go from the top down,
				// so every point after this one is below the face too
				if (adjIndex >= 0)
				{
					break;
				}
				continue;
			}

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
			
			// shaped neighbors don't fill their block, so they don't hide this face
			boolean adjShaped = (adjacentIsSameDetailLevel
					&& getShape(adjPoint, horizontalBlockWidth) != RenderDataPointUtil.SHAPE_NONE)
				// plants never cover anything, even next to a lower detail LOD
				|| (RenderDataPointUtil.doesDataPointExist(adjPoint)
					&& RenderDataPointUtil.getShape(adjPoint) == RenderDataPointUtil.SHAPE_CROSS)
				// faces behind see-through leaves can be seen through their gaps
				|| (adjacentIsSameDetailLevel
					&& isSeeThroughLeaves(adjPoint, seeThroughLeavesEnabled));
			// a transparent neighbor only hides the same material (IE the walls between water blocks),
			// otherwise water behind glass (or glass in water) would be invisible
			boolean adjOtherTransparentMaterial = adjTransparent
				&& RenderDataPointUtil.getBlockMaterialId(adjPoint) != irisBlockMaterialId;
			boolean adjCoversFace = !adjShaped && !adjOtherTransparentMaterial;
			
			if (adjShaped)
			{
				// lit by the air next to the neighbor
				lightToApply = adjSkyLight;
				blockLightToApply = adjBlockLight;
			}
			else if (!adjTransparent)
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
			if (adjMaxY > yMin)
			{
				applyLightToRangeAndPopulateNewSgements(segments, newSegments, adjMinY, adjMaxY, lightToApply, blockLightToApply, adjCoversFace);
				{
					// swap references so we can use the newly populated segments
					LongArrayList temp = segments;
					segments = newSegments;
					newSegments = temp;
				}
			}

			// Fill overhang area [adjMaxY, adjAboveMinY) with adjSkyLight
			if (adjMaxY < adjAboveMinY)
			{
				// the topmost air keeps its max sky light (min is taken)
				byte overhangSkyLight = (adjIndex == 0) ? LodUtil.MAX_MC_LIGHT : adjSkyLight;

				// the stored block light is the light right above the adjacent point,
				// it drops by one per block going up like it does in MC, otherwise a lava sea lights cliffs all the way to the ceiling
				short stepStartY = adjMaxY;
				// steps above the face can't change any segment
				while (stepStartY < adjAboveMinY
					&& stepStartY < yMax)
				{
					byte stepBlockLight = (byte) Math.max(LodUtil.MIN_MC_LIGHT, adjBlockLight - (stepStartY - adjMaxY));
					short stepEndY = (stepBlockLight == LodUtil.MIN_MC_LIGHT)
						? adjAboveMinY
						: (short) Math.min(stepStartY + BLOCK_LIGHT_FALLOFF_STEP_HEIGHT, adjAboveMinY);

					// neither can steps below it
					if (stepEndY > yMin)
					{
						applyLightToRangeAndPopulateNewSgements(segments, newSegments, stepStartY, stepEndY, overhangSkyLight, stepBlockLight, false);
						{
							LongArrayList temp = segments;
							segments = newSegments;
							newSegments = temp;
						}
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
	
	/**
	 * Smooth lighting like MC's: each corner of a top face gets the average light of the air above
	 * this column and the 2 columns next to that corner.
	 * MC also uses the diagonal block, the adjacent data doesn't have it so the 2 sides stand in for it.
	 * Neighbors that are solid at that height use this column's light instead, the same as MC.
	 *
	 * @return see {@link BufferQuad#cornerLights}
	 */
	private static int getSmoothTopCornerLights(ColumnRenderView[] adjData, short airY, byte skyLight, byte blockLight)
	{
		int center = (skyLight << 4) | blockLight;
		int north = getAirLight(adjData[EDhDirection.NORTH.compassIndex], airY, center);
		int south = getAirLight(adjData[EDhDirection.SOUTH.compassIndex], airY, center);
		int west = getAirLight(adjData[EDhDirection.WEST.compassIndex], airY, center);
		int east = getAirLight(adjData[EDhDirection.EAST.compassIndex], airY, center);

		int cornerLights = 0;
		for (int corner = 0; corner < 4; corner++)
		{
			int sideX = ((corner & 1) != 0) ? east : west;
			int sideZ = ((corner & 2) != 0) ? south : north;
			// (center + sideX + sideZ + diagonal) / 4, with the diagonal being the sides' average
			int cornerSky = (2 * (center >>> 4) + 3 * (sideX >>> 4) + 3 * (sideZ >>> 4) + 4) / 8;
			int cornerBlock = (2 * (center & 0xF) + 3 * (sideX & 0xF) + 3 * (sideZ & 0xF) + 4) / 8;
			cornerLights |= ((cornerSky << 4) | cornerBlock) << (corner * 8);
		}
		return cornerLights;
	}
	/**
	 * @return the light of the air at the given height in the column as (skyLight << 4 | blockLight),
	 *          or the fallback if there isn't air there
	 */
	private static int getAirLight(@Nullable ColumnRenderView column, short airY, int fallback)
	{
		if (column == null)
		{
			return fallback;
		}

		// the column's points go from the top down
		for (int i = 0; i < column.size; i++)
		{
			long dataPoint = column.get(i);
			if (!RenderDataPointUtil.doesDataPointExist(dataPoint)
				|| RenderDataPointUtil.hasZeroHeight(dataPoint))
			{
				continue;
			}

			short minY = RenderDataPointUtil.getYMin(dataPoint);
			short maxY = RenderDataPointUtil.getYMax(dataPoint);
			if (maxY <= airY)
			{
				// the first point below, a data point's light is the light of the air above it
				return (RenderDataPointUtil.getLightSky(dataPoint) << 4) | RenderDataPointUtil.getLightBlock(dataPoint);
			}

			if (minY <= airY)
			{
				// something is at that height,
				// only air, plants and shapes that don't fill their block let the light through
				boolean lightPassesThrough = RenderDataPointUtil.getBlockMaterialId(dataPoint) == EDhApiBlockMaterial.AIR.index
					|| RenderDataPointUtil.getShape(dataPoint) != RenderDataPointUtil.SHAPE_NONE;
				return lightPassesThrough
					? (RenderDataPointUtil.getLightSky(dataPoint) << 4) | RenderDataPointUtil.getLightBlock(dataPoint)
					: fallback;
			}
		}

		return fallback;
	}

	/** shapes are only drawn by 1 block wide LODs */
	private static byte getShape(long dataPoint, short blockWidth)
	{
		return (blockWidth == 1 && RenderDataPointUtil.doesDataPointExist(dataPoint))
			? RenderDataPointUtil.getShape(dataPoint)
			: RenderDataPointUtil.SHAPE_NONE;
	}
	
	/**
	 * See-through leaves (like MC's fancy leaves) are only drawn by 1 block wide textured LODs,
	 * the faces they touch can be seen through their gaps so they aren't culled.
	 *
	 * @param seeThroughLeavesEnabled true if the LOD is 1 block wide and see-through leaves are enabled
	 */
	private static boolean isSeeThroughLeaves(long dataPoint, boolean seeThroughLeavesEnabled)
	{
		return seeThroughLeavesEnabled
			&& RenderDataPointUtil.doesDataPointExist(dataPoint)
			&& RenderDataPointUtil.getBlockMaterialId(dataPoint) == EDhApiBlockMaterial.LEAVES.index;
	}

	private static boolean isPost(byte shape)
	{
		return shape == RenderDataPointUtil.SHAPE_THIN_POST
			|| shape == RenderDataPointUtil.SHAPE_THICK_POST;
	}
	
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
