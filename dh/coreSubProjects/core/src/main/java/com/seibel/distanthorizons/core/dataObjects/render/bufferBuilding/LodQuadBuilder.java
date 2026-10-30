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

import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.ByteBufferCheckoutWrapper;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListPool;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Used to create the quads before they are converted to render-able buffers. <br><br>
 *
 * Note: the magic number 6 you see throughout this method represents the number of sides on a cube.
 */
public class LodQuadBuilder implements AutoCloseable
{
	/** ThreadLocal is the simplest way to allow each LOD loading thread to have their own builder */
	private static final ThreadLocal<LodQuadBuilder> THREAD_LOCAL = ThreadLocal.withInitial(LodQuadBuilder::new);

	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("LodQuadBuilder");

	/** the number of bytes for a single vertex */
	public static final int BYTES_PER_VERTEX = 16;
	public static final int BYTES_PER_QUAD = BYTES_PER_VERTEX * 4;

	public static final int[][][] DIRECTION_VERTEX_IBO_QUAD = new int[][][]
		//region
		{
			// X,Z //
			{ // UP
				{1, 0}, // 0
				{1, 1}, // 1
				{0, 1}, // 2
				{0, 0}, // 3
			},
			{ // DOWN
				{0, 0}, // 0
				{0, 1}, // 1
				{1, 1}, // 2
				{1, 0}, // 3
			},

			// X,Y //
			{ // NORTH
				{0, 0}, // 0
				{0, 1}, // 1
				{1, 1}, // 2

				{1, 0}, // 3
			},
			{ // SOUTH
				{1, 0}, // 0
				{1, 1}, // 1
				{0, 1}, // 2

				{0, 0}, // 3
			},

			// Z,Y //
			{ // WEST
				{0, 0}, // 0
				{1, 0}, // 1
				{1, 1}, // 2

				{0, 1}, // 3
			},
			{ // EAST
				{0, 1}, // 0
				{1, 1}, // 1
				{1, 0}, // 2

				{0, 0}, // 3
			},
		};
	//endregion



	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] opaqueQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] transparentQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	/**
	 * Crossed plant planes (see {@link #addCrossQuads}), always opaque and never merged. <br>
	 * Their direction marks the plane and side: north/south are the two sides of the
	 * min corner to max corner diagonal, west/east the two sides of the other one.
	 */
	@SuppressWarnings("unchecked")
	private final ArrayList<BufferQuad>[] crossQuads = (ArrayList<BufferQuad>[]) new ArrayList[]{new ArrayList<BufferQuad>()};

	/** normal index of the min corner to max corner diagonal plane, after the 6 cube faces */
	public static final byte CROSS_PLANE_A_NORMAL_INDEX = 6;
	/** normal index of the other diagonal plane */
	public static final byte CROSS_PLANE_B_NORMAL_INDEX = 7;

	/**
	 * Caching the BufferQuad objects reduces overhead slightly. <br>
	 * Caching is handled per builder (vs globally in {@link BufferQuad} itself)
	 * to prevent concurrency overhead.
	 */
	private final ArrayList<BufferQuad> bufferQuadCacheList = new ArrayList<>();

	/**
	 * Reused by {@link #mergeQuadsInternal}, sorting primitive keys is much faster
	 * than sorting the quads with a comparator.
	 */
	private long[] mergeSortKeys = new long[0];
	private long[] mergeSortKeysTemp = new long[0];
	private BufferQuad[] mergeSortQuads = new BufferQuad[0];
	private BufferQuad[] mergeSortQuadsTemp = new BufferQuad[0];
	private final int[] radixBucketStarts = new int[RADIX_BUCKET_COUNT + 1];

	/**
	 * Smaller lists are quicker to sort with their list index packed into the sort keys' low 16 bits,
	 * which are always 0 (see {@code BufferQuad.computeSortKey}). <br>
	 * Must be at most 2^16 so the index fits.
	 */
	private static final int RADIX_SORT_MIN_QUAD_COUNT = 2048;
	private static final int RADIX_BITS = 12;
	private static final int RADIX_BUCKET_COUNT = 1 << RADIX_BITS;

	private boolean doTransparency;
	private IClientLevelWrapper clientLevelWrapper;
	private boolean noSkyLight;

	/**
	 * {@link RenderDataPointUtil#SHAPE_NONE} etc. applied to every quad added until it's changed,
	 * set by {@link ColumnBox} for each LOD box.
	 */
	public byte shapeForNewQuads = RenderDataPointUtil.SHAPE_NONE;

	/**
	 * If true quads added are see-through leaf faces, which have their gaps cut out by the shaders,
	 * marked by adding {@link #SEE_THROUGH_NORMAL_INDEX_OFFSET} to their normal index. Set by {@link ColumnBox}.
	 */
	public boolean seeThroughForNewQuads = false;
	public static final byte SEE_THROUGH_NORMAL_INDEX_OFFSET = 8;

	/**
	 * If set, side quads are split into rows with their own tiles,
	 * IE the top and bottom of a wall of Chisel's connected textures. <br>
	 * Indexed by the row counted down from {@link #sideRowTopYForNewQuads}, then {@link EDhDirection#faceIndex},
	 * rows past the end keep the quad's tile. Set by {@link ColumnBox} for each LOD box.
	 *
	 * @see com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry#getSideRowTileIds
	 */
	public short @Nullable [][] sideRowTileIdsForNewQuads = null;
	/** the Y right above the first row */
	public short sideRowTopYForNewQuads = 0;

	private EDhApiDebugRendering debugRenderingMode;
	private EDhApiGrassSideRendering grassSideRenderingMode;

	private int premergeCount = 0;



	//=============//
	// constructor //
	//=============//
	//region

	private LodQuadBuilder()
	{
		for (int i = 0; i < 6; i++)
		{
			this.opaqueQuads[i] = new ArrayList<>();
			this.transparentQuads[i] = new ArrayList<>();
		}
	}

	public static LodQuadBuilder getBuilder(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		LodQuadBuilder builder = THREAD_LOCAL.get();
		builder.set(doTransparency, clientLevelWrapper);
		return builder;
	}
	private void set(boolean doTransparency, IClientLevelWrapper clientLevelWrapper)
	{
		this.doTransparency = doTransparency;

		this.clientLevelWrapper = clientLevelWrapper;
		// faces next to open air default to full skylight, which lights up caves in dimensions without a sky
		this.noSkyLight = (clientLevelWrapper != null && !clientLevelWrapper.hasSkyLight());

		this.debugRenderingMode = Config.Client.Advanced.Debugging.debugRenderingColors.get();
		this.grassSideRenderingMode = Config.Client.Advanced.Graphics.Quality.grassSideRendering.get();

		this.premergeCount = 0;
	}

	//endregion



	//===========//
	// add quads //
	//===========//
	//region

	public void addQuadAdj(
		EDhDirection dir,
		short x, short y, short z,
		short width, short height,
		int color, short faceTextureId,
		byte irisBlockMaterialId, byte skyLight, byte blockLight)
	{
		if (dir == EDhDirection.DOWN)
		{
			throw new IllegalArgumentException("addQuadAdj() is only for adj direction! Not UP or Down!");
		}

		if (this.sideRowTileIdsForNewQuads != null)
		{
			this.addRowSplitQuadAdj(dir, x, y, z, width, height, color, faceTextureId, irisBlockMaterialId, skyLight, blockLight);
			return;
		}


		ArrayList<BufferQuad> quadList;
		if (this.doTransparency && ColorUtil.getAlpha(color) < 255)
		{
			quadList = this.transparentQuads[dir.faceIndex];
		}
		else
		{
			quadList = this.opaqueQuads[dir.faceIndex];
		}

		BufferQuad quad = this.getOrCreateBufferQuad();
		quad.set(x, y, z, width, height, color, faceTextureId, irisBlockMaterialId, skyLight, blockLight, dir);
		quad.shape = this.shapeForNewQuads;
		quad.seeThrough = this.seeThroughForNewQuads;
		if (!quadList.isEmpty()
			&& (
			quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
				|| quadList.get(quadList.size() - 1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
		)
		{
			this.premergeCount++;
			// the merged quad isn't used, otherwise the cache runs dry and every quad is a new allocation
			this.bufferQuadCacheList.add(quad);
			return;
		}

		quadList.add(quad);
	}
	/** rows next to each other with the same tile stay one quad */
	private void addRowSplitQuadAdj(
		EDhDirection dir,
		short x, short y, short z,
		short width, short height,
		int color, short faceTextureId,
		byte irisBlockMaterialId, byte skyLight, byte blockLight)
	{
		short[][] rowTileIds = this.sideRowTileIdsForNewQuads;
		this.sideRowTileIdsForNewQuads = null;
		try
		{
			int endY = y + height;
			int rowStartY = y;
			while (rowStartY < endY)
			{
				short rowTileId = this.getRowTileId(rowTileIds, dir, rowStartY, faceTextureId);
				int rowEndY = rowStartY + 1;
				while (rowEndY < endY
					&& this.getRowTileId(rowTileIds, dir, rowEndY, faceTextureId) == rowTileId)
				{
					rowEndY++;
				}

				this.addQuadAdj(dir, x, (short) rowStartY, z, width, (short) (rowEndY - rowStartY), color, rowTileId, irisBlockMaterialId, skyLight, blockLight);
				rowStartY = rowEndY;
			}
		}
		finally
		{
			this.sideRowTileIdsForNewQuads = rowTileIds;
		}
	}
	private short getRowTileId(short[][] rowTileIds, EDhDirection dir, int blockY, short defaultTileId)
	{
		int row = this.sideRowTopYForNewQuads - 1 - blockY;
		return (row >= 0 && row < rowTileIds.length) ? rowTileIds[row][dir.faceIndex] : defaultTileId;
	}

	// XZ
	public void addQuadUp(
		short minX, short maxY, short minZ,
		short blockWidth,
		int color, short faceTextureId,
		byte irisBlockMaterialId,
		byte skylight, byte blocklight)
	{ this.addQuadUp(minX, maxY, minZ, blockWidth, color, faceTextureId, irisBlockMaterialId, skylight, blocklight, BufferQuad.UNIFORM_CORNER_LIGHTS); }
	/** @param cornerLights see {@link BufferQuad#cornerLights} */
	public void addQuadUp(
		short minX, short maxY, short minZ,
		short blockWidth,
		int color, short faceTextureId,
		byte irisBlockMaterialId,
		byte skylight, byte blocklight,
		int cornerLights)
	{
		boolean isTransparent = (this.doTransparency && ColorUtil.getAlpha(color) < 255);
		ArrayList<BufferQuad> quadList = isTransparent
			? this.transparentQuads[EDhDirection.UP.faceIndex]
			: this.opaqueQuads[EDhDirection.UP.faceIndex];

		BufferQuad quad = this.getOrCreateBufferQuad();
		quad.set(minX, maxY, minZ, blockWidth, blockWidth, color, faceTextureId, irisBlockMaterialId, skylight, blocklight, EDhDirection.UP);
		quad.shape = this.shapeForNewQuads;
		quad.seeThrough = this.seeThroughForNewQuads;
		// evenly lit quads stay uniform so they can still be merged
		int firstCorner = cornerLights & 0xFF;
		if (cornerLights != BufferQuad.UNIFORM_CORNER_LIGHTS)
		{
			if (cornerLights == (firstCorner * 0x01010101))
			{
				quad.skyLight = (byte) (firstCorner >>> 4);
				quad.blockLight = (byte) (firstCorner & 0xF);
			}
			else
			{
				quad.cornerLights = cornerLights;
			}
		}
		quadList.add(quad);
	}

	public void addQuadDown(
		short x, short y, short z,
		short blockWidth,
		int color, short faceTextureId,
		byte irisBlockMaterialId,
		byte skylight, byte blocklight)
	{
		ArrayList<BufferQuad> quadArray = (this.doTransparency && ColorUtil.getAlpha(color) < 255)
			? this.transparentQuads[EDhDirection.DOWN.faceIndex]
			: this.opaqueQuads[EDhDirection.DOWN.faceIndex];

		BufferQuad quad = this.getOrCreateBufferQuad();
		quad.set(x, y, z, blockWidth, blockWidth, color, faceTextureId, irisBlockMaterialId, skylight, blocklight, EDhDirection.DOWN);
		quad.shape = this.shapeForNewQuads;
		quad.seeThrough = this.seeThroughForNewQuads;
		quadArray.add(quad);
	}

	/**
	 * Adds two crossed diagonal planes, each drawn from both sides,
	 * like MC draws tall grass and flowers.
	 */
	public void addCrossQuads(
		short minX, short minY, short minZ,
		short blockWidth, short height,
		int color, short faceTextureId,
		byte irisBlockMaterialId,
		byte skylight, byte blocklight)
	{
		// plants have cutout textures, they're never blended
		color = ColorUtil.setAlpha(color, 255);
		for (EDhDirection planeSide : EDhDirection.CARDINAL_COMPASS)
		{
			BufferQuad quad = this.getOrCreateBufferQuad();
			quad.set(minX, minY, minZ, blockWidth, height, color, faceTextureId, irisBlockMaterialId, skylight, blocklight, planeSide);
			quad.shape = RenderDataPointUtil.SHAPE_NONE;
			this.crossQuads[0].add(quad);
		}
	}

	//endregion



	//=================//
	// data finalizing //
	//=================//
	//region

	/** Uses Greedy meshing to merge this builder's Quads. */
	public void mergeQuads()
	{
		long mergeCount = 0; // can be used for debugging
		long preQuadsCount = this.getCurrentOpaqueQuadsCount() + this.getCurrentTransparentQuadsCount();
		if (preQuadsCount <= 1)
		{
			return;
		}

		for (int directionIndex = 0; directionIndex < 6; directionIndex++)
		{
			mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			if (this.doTransparency)
			{
				mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			}


			// only run the second merge if the face is the top or bottom
			if (directionIndex == EDhDirection.UP.faceIndex
				|| directionIndex == EDhDirection.DOWN.faceIndex)
			{
				mergeCount += mergeQuadsInternal(this.opaqueQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				if (this.doTransparency)
				{
					mergeCount += mergeQuadsInternal(this.transparentQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				}
			}
		}
	}

	/** Merges all of this builder's quads for the given directionIndex (up, down, left, etc.) in the given direction */
	private long mergeQuadsInternal(ArrayList<BufferQuad>[] list, int directionIndex, BufferMergeDirectionEnum mergeDirection)
	{
		ArrayList<BufferQuad> quads = list[directionIndex];
		int quadCount = quads.size();
		if (quadCount <= 1)
		{
			return 0;
		}

		if (this.mergeSortKeys.length < quadCount)
		{
			this.mergeSortKeys = new long[quadCount];
			this.mergeSortKeysTemp = new long[quadCount];
			this.mergeSortQuads = new BufferQuad[quadCount];
			this.mergeSortQuadsTemp = new BufferQuad[quadCount];
		}

		// both sorts are stable, quads with the same key stay in list order like the comparator sort they replaced
		boolean eastWest = (mergeDirection == BufferMergeDirectionEnum.EastWest);
		BufferQuad[] sortedQuads = (quadCount < RADIX_SORT_MIN_QUAD_COUNT)
			? this.sortQuadsWithPackedIndex(quads, quadCount, eastWest)
			: this.radixSortQuads(quads, quadCount, eastWest);

		// only the quads that weren't merged into the one before them are kept
		quads.clear();
		long mergeCount = 0;
		BufferQuad currentQuad = sortedQuads[0];
		quads.add(currentQuad);
		for (int i = 1; i < quadCount; i++)
		{
			BufferQuad nextQuad = sortedQuads[i];
			if (currentQuad.tryMerge(nextQuad, mergeDirection))
			{
				mergeCount++;
				// merged quads go back to the cache so the next quads don't have to be allocated
				this.bufferQuadCacheList.add(nextQuad);
			}
			else
			{
				currentQuad = nextQuad;
				quads.add(nextQuad);
			}
		}

		// don't keep the quads alive after this builder is done with them
		Arrays.fill(this.mergeSortQuads, 0, quadCount, null);
		Arrays.fill(this.mergeSortQuadsTemp, 0, quadCount, null);
		return mergeCount;
	}

	/** @return the sorted quads, the list index breaks ties since it's packed into the low bits of each key */
	private BufferQuad[] sortQuadsWithPackedIndex(ArrayList<BufferQuad> quads, int quadCount, boolean eastWest)
	{
		long[] keys = this.mergeSortKeys;
		BufferQuad[] unsortedQuads = this.mergeSortQuads;
		for (int i = 0; i < quadCount; i++)
		{
			BufferQuad quad = quads.get(i);
			unsortedQuads[i] = quad;
			keys[i] = (eastWest ? quad.sortKeyEastWest : quad.sortKeyNorthSouth) | i;
		}
		Arrays.sort(keys, 0, quadCount);

		BufferQuad[] sortedQuads = this.mergeSortQuadsTemp;
		for (int i = 0; i < quadCount; i++)
		{
			sortedQuads[i] = unsortedQuads[(int) (keys[i] & 0xFFFF)];
		}
		return sortedQuads;
	}

	/**
	 * A stable least significant digit radix sort over the keys' upper 48 bits
	 * (the low 16 are always 0), it's linear so large lists sort several times faster.
	 *
	 * @return the sorted quads
	 */
	private BufferQuad[] radixSortQuads(ArrayList<BufferQuad> quads, int quadCount, boolean eastWest)
	{
		long[] keys = this.mergeSortKeys;
		long[] tempKeys = this.mergeSortKeysTemp;
		BufferQuad[] sortedQuads = this.mergeSortQuads;
		BufferQuad[] tempQuads = this.mergeSortQuadsTemp;
		int[] bucketStarts = this.radixBucketStarts;

		for (int i = 0; i < quadCount; i++)
		{
			BufferQuad quad = quads.get(i);
			sortedQuads[i] = quad;
			// flipping the sign bit makes the unsigned digit order match Long.compare's signed order
			keys[i] = (eastWest ? quad.sortKeyEastWest : quad.sortKeyNorthSouth) ^ Long.MIN_VALUE;
		}

		for (int shift = 16; shift < Long.SIZE; shift += RADIX_BITS)
		{
			Arrays.fill(bucketStarts, 0);
			for (int i = 0; i < quadCount; i++)
			{
				bucketStarts[(int) ((keys[i] >>> shift) & (RADIX_BUCKET_COUNT - 1)) + 1]++;
			}
			for (int bucket = 0; bucket < RADIX_BUCKET_COUNT; bucket++)
			{
				bucketStarts[bucket + 1] += bucketStarts[bucket];
			}
			for (int i = 0; i < quadCount; i++)
			{
				int destination = bucketStarts[(int) ((keys[i] >>> shift) & (RADIX_BUCKET_COUNT - 1))]++;
				tempKeys[destination] = keys[i];
				tempQuads[destination] = sortedQuads[i];
			}

			long[] swapKeys = keys;
			keys = tempKeys;
			tempKeys = swapKeys;
			BufferQuad[] swapQuads = sortedQuads;
			sortedQuads = tempQuads;
			tempQuads = swapQuads;
		}
		return sortedQuads;
	}

	//endregion



	//==============//
	// buffer setup //
	//==============//
	//region

	public ArrayList<ByteBuffer> makeOpaqueVertexBuffers(PhantomArrayListCheckout checkout) { return this.makeVertexBuffers(checkout, this.opaqueQuads, true); }
	public ArrayList<ByteBuffer> makeTransparentVertexBuffers(PhantomArrayListCheckout checkout) { return this.makeVertexBuffers(checkout, this.transparentQuads, false); }
	private ArrayList<ByteBuffer> makeVertexBuffers(PhantomArrayListCheckout checkout, ArrayList<BufferQuad>[] quadList, boolean includeCrossQuads)
	{
		ArrayList<ByteBuffer> byteBufferList = new ArrayList<>(3);

		int byteBufferSize = getMaxBufferByteSize();

		ByteBuffer buffer = null;
		// the 7th list is the crossed plants
		for (int directionIndex = 0; directionIndex < 7; directionIndex++)
		{
			ArrayList<BufferQuad> quads;
			if (directionIndex < 6)
			{
				quads = quadList[directionIndex];
			}
			else
			{
				quads = includeCrossQuads ? this.crossQuads[0] : null;
			}

			// ignore empty directions
			if (quads == null || quads.isEmpty())
			{
				continue;
			}

			// put all the quads in this direction into the buffer
			for (int quadIndex = 0; quadIndex < quads.size(); quadIndex++)
			{
				// if this is the first iteration or the buffer is full,
				// create a new buffer
				if (buffer == null
					|| buffer.remaining() < BYTES_PER_QUAD)
				{
					if ((byteBufferList.size() + 1) > checkout.getByteBufferWrapperCount())
					{
						ByteBufferCheckoutWrapper wrapper = new ByteBufferCheckoutWrapper(byteBufferSize);
						checkout.addByteBufferWrapper(wrapper);
						buffer = wrapper.buffer;
					}
					else
					{
						buffer = checkout.getByteBuffer(byteBufferList.size(), byteBufferSize);
					}

					byteBufferList.add(buffer);
				}

				if (directionIndex < 6)
				{
					this.putQuad(buffer, quads.get(quadIndex));
				}
				else
				{
					this.putCrossQuad(buffer, quads.get(quadIndex));
				}
			}
		}

		// rewind all the buffers so they can be read from
		for (int i = 0; i < byteBufferList.size(); i++)
		{
			buffer = byteBufferList.get(i);
			buffer.limit(buffer.position());
			buffer.rewind();
		}

		return byteBufferList;
	}
	private void putQuad(ByteBuffer bb, BufferQuad quad)
	{
		int[][] quadBase = DIRECTION_VERTEX_IBO_QUAD[quad.direction.faceIndex];
		short widthEastWest = quad.widthEastWest;
		short widthNorthSouth = quad.widthNorthSouthOrHeight;
		byte normalIndex = (byte) (quad.direction.faceIndex + (quad.seeThrough ? SEE_THROUGH_NORMAL_INDEX_OFFSET : 0));
		EDhDirection.Axis axis = quad.direction.axis;
		for (int i = 0; i < quadBase.length; i++)
		{
			short dx, dy, dz;
			int mx, my, mz;
			switch (axis)
			{
				case X: // ZY
					dx = 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = quadBase[i][0] == 1 ? widthEastWest : 0;
					mx = 0;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = quadBase[i][0] == 1 ? 1 : -1;
					break;
				case Y: // XZ
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = 0;
					dz = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = 0;
					mz = quadBase[i][1] == 1 ? 1 : -1;
					break;
				case Z: // XY
					dx = quadBase[i][0] == 1 ? widthEastWest : 0;
					dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
					dz = 0;
					mx = quadBase[i][0] == 1 ? 1 : -1;
					my = quadBase[i][1] == 1 ? 1 : -1;
					mz = 0;
					break;
				default:
					throw new IllegalArgumentException("Invalid Axis enum: " + axis);
			}


			int color = quad.color;

			// use custom side color logic for grass blocks
			if (quad.irisBlockMaterialId == EDhApiBlockMaterial.GRASS.index)
			{
				// only use dirt colors if debug rendering is disabled
				if (this.debugRenderingMode == EDhApiDebugRendering.OFF)
				{
					// determine if any custom coloring logic should be used
					if (this.grassSideRenderingMode != EDhApiGrassSideRendering.AS_GRASS
						|| quad.textureTileId != 0)
					{
						// only change the vertex color if it's on the side or bottom
						if (quad.direction.axis.isHorizontal() || quad.direction == EDhDirection.DOWN)
						{
							if (this.grassSideRenderingMode == EDhApiGrassSideRendering.AS_DIRT
								// textured grass sides are relative to the dirt color, their texture has the grass strip (see ClientBlockStateTextureCache)
								|| quad.textureTileId != 0
								// if we want the color to fade, only apply the dirt color to the bottom vertices
								|| (this.grassSideRenderingMode == EDhApiGrassSideRendering.FADE_TO_DIRT && quadBase[i][1] == 0)
								// always render the bottom as dirt
								|| quad.direction == EDhDirection.DOWN)
							{
								// for horizontal and bottom faces of grass blocks, use the  dirt color to
								// prevent green cliff walls
								color = this.clientLevelWrapper.getDirtBlockColor();
								color = ColorUtil.applyShade(color, this.clientLevelWrapper.getShade(quad.direction));
							}
						}
					}
				}
			}


			byte skyLight = quad.skyLight;
			byte blockLight = quad.blockLight;
			if (quad.cornerLights != BufferQuad.UNIFORM_CORNER_LIGHTS)
			{
				// smooth lighting, only up faces have corner lights
				int corner = quadBase[i][0] | (quadBase[i][1] << 1);
				int cornerLight = (quad.cornerLights >>> (corner * 8)) & 0xFF;
				skyLight = (byte) (cornerLight >>> 4);
				blockLight = (byte) (cornerLight & 0xF);
			}

			this.putVertex(bb, (short) (quad.x + dx), (short) (quad.y + dy), (short) (quad.z + dz),
				quad.hasError ? ColorUtil.RED : color,
				quad.hasError ? 0 : normalIndex,
				quad.hasError ? 0 : quad.irisBlockMaterialId,
				quad.hasError ? 15 : (this.noSkyLight ? 0 : skyLight),
				quad.hasError ? 15 : blockLight,
				quad.hasError ? 0 : quad.textureTileId,
				quad.hasError ? RenderDataPointUtil.SHAPE_NONE : quad.shape,
				mx, my, mz);
		}
	}
	/**
	 * The corners are on the block grid, so no micro offset or shape inset is needed
	 * and shader packs draw them correctly too.
	 */
	private void putCrossQuad(ByteBuffer bb, BufferQuad quad)
	{
		boolean planeA = (quad.direction == EDhDirection.NORTH || quad.direction == EDhDirection.SOUTH);
		boolean backSide = (quad.direction == EDhDirection.SOUTH || quad.direction == EDhDirection.EAST);
		byte normalIndex = planeA ? CROSS_PLANE_A_NORMAL_INDEX : CROSS_PLANE_B_NORMAL_INDEX;

		short width = quad.widthEastWest;
		short height = quad.widthNorthSouthOrHeight;
		short startX = quad.x;
		short endX = (short) (quad.x + width);
		short startZ = planeA ? quad.z : (short) (quad.z + width);
		short endZ = planeA ? (short) (quad.z + width) : quad.z;
		short minY = quad.y;
		short maxY = (short) (quad.y + height);

		// corners 0-3 are bottom start, bottom end, top end, top start,
		// the back side is wound the other way so it isn't culled.
		// (computed instead of put in an array, there are a lot of plants)
		for (int i = 0; i < 4; i++)
		{
			int corner = backSide ? (4 - i) % 4 : i;
			boolean atEnd = (corner == 1 || corner == 2);
			boolean atTop = (corner >= 2);
			this.putVertex(bb, atEnd ? endX : startX, atTop ? maxY : minY, atEnd ? endZ : startZ,
				quad.color,
				normalIndex,
				quad.irisBlockMaterialId,
				this.noSkyLight ? 0 : quad.skyLight,
				quad.blockLight,
				quad.textureTileId,
				RenderDataPointUtil.SHAPE_NONE,
				0, 0, 0);
		}
	}

	private void putVertex(
		ByteBuffer bb,
		short x, short y, short z,
		int color,
		byte normalIndex, byte irisBlockMaterialId,
		byte skylight, byte blocklight,
		short textureTileId,
		byte shape,
		int mx, int my, int mz)
	{
		bb.putShort(x);
		bb.putShort(y);
		bb.putShort(z);

		short meta = 0;
		{
			skylight %= 16;
			blocklight %= 16;
			meta |= (short) (skylight | (blocklight << 4));

			byte mircoOffset = 0;
			// mirco offset which is a xyz 2bit value
			// 0b00 = no offset
			// 0b01 = positive offset
			// 0b11 = negative offset
			// format is: 0b00zzyyxx
			if (mx != 0)
			{
				mircoOffset |= (byte) (mx > 0 ? 0b01 : 0b11);
			}
			if (my != 0)
			{
				mircoOffset |= (byte) (my > 0 ? 0b0100 : 0b1100);
			}
			if (mz != 0)
			{
				mircoOffset |= (byte) (mz > 0 ? 0b010000 : 0b110000);
			}
			meta |= (short) (mircoOffset << 8);

			// the shape uses the 2 unused bits above the micro offset (0bss00_0000_0000_0000),
			// DH's vertex shader insets the vertex based on it, shader packs get the same inset from ShaderPackLodTextures
			meta |= (short) ((shape & 0b11) << 14);
		}
		bb.putShort(meta);

		byte r = (byte) ColorUtil.getRed(color);
		byte g = (byte) ColorUtil.getGreen(color);
		byte b = (byte) ColorUtil.getBlue(color);
		byte a = this.doTransparency ? (byte) ColorUtil.getAlpha(color) : (byte) 255;
		bb.put(r);
		bb.put(g);
		bb.put(b);
		bb.put(a);

		// Block ID and normal index are used by the Iris format
		bb.put(irisBlockMaterialId);
		bb.put(normalIndex);

		// These two bytes keep the vertex a multiple of 4
		// and are treated as padding by Iris's mirror of this format,
		// so the tile id is invisible to shader packs.
		bb.putShort(textureTileId);
	}

	//endregion



	//=========//
	// getters //
	//=========//
	//region

	public int getCurrentOpaqueQuadsCount()
	{
		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.opaqueQuads)
		{
			i += quadList.size();
		}
		i += this.crossQuads[0].size();

		return i;
	}
	public int getCurrentTransparentQuadsCount()
	{
		if (!this.doTransparency)
		{
			return 0;
		}

		int i = 0;
		for (ArrayList<BufferQuad> quadList : this.transparentQuads)
		{
			i += quadList.size();
		}

		return i;
	}

	private static int maxBufferByteSize = -1;
	/**
	 * The max number of bytes we allow for a single Vertex buffer.
	 * If an LOD has more data than this it will be split
	 * up into multiple buffers.
	 */
	public static int getMaxBufferByteSize()
	{
		if (maxBufferByteSize != -1)
		{
			return maxBufferByteSize;
		}

		// 2 MB
		// note: this is relatively small (10 MB was the previous max) to reduce stuttering
		// during the upload process by having smaller upload steps
		int maxVboByteSize = 2 * 1024 * 1024;

		int maxQuadsPerBuffer = maxVboByteSize / BYTES_PER_QUAD;
		// integer truncation to remove decimal component
		int fullSizedBuffer = maxQuadsPerBuffer * BYTES_PER_QUAD;

		maxBufferByteSize = fullSizedBuffer;

		return fullSizedBuffer;
	}

	//endregion



	//=====================//
	// buffer quad pooling //
	//=====================//
	//region

	private BufferQuad getOrCreateBufferQuad()
	{
		// start from the back of the list so we don't have
		// to move the array around
		int index = bufferQuadCacheList.size() - 1;
		if (index < 0)
		{
			// cache empty, create a new object
			return new BufferQuad();
		}

		BufferQuad quad = bufferQuadCacheList.remove(index);
		if (quad != null) // shouldn't happen, but just in case
		{
			return quad;
		}

		return new BufferQuad();
	}

	@SuppressWarnings({"UseBulkOperation", "ForLoopReplaceableByForEach"})
	private static void returnQuadsToCache(ArrayList<BufferQuad> quadCache, ArrayList<BufferQuad>[] quadsToReturn)
	{
		for (int i = 0; i < quadsToReturn.length; i++)
		{
			// manual add and loop to reduce GC pressure due to addAll() doing unnecessary
			// array copies
			for (int j = 0; j < quadsToReturn[i].size(); j++)
			{
				quadCache.add(quadsToReturn[i].get(j));
			}

			quadsToReturn[i].clear();
		}
	}

	//endregion



	//================//
	// base overrides //
	//================//
	//region

	// can be used/closed multiple times
	@Override
	public void close()
	{
		returnQuadsToCache(this.bufferQuadCacheList, this.opaqueQuads);
		returnQuadsToCache(this.bufferQuadCacheList, this.transparentQuads);
		returnQuadsToCache(this.bufferQuadCacheList, this.crossQuads);
	}

	//endregion



}
