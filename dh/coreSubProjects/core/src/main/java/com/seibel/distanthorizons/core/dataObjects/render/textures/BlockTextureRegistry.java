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

package com.seibel.distanthorizons.core.dataObjects.render.textures;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateFaceTextureProvider;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.coreapi.util.TextureUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns each block state face a global atlas tile id
 * and holds the tile pixel data until it's uploaded to the GPU. <br><br>
 *
 * Tile pixels are stored as color ratios relative to the LOD's average color
 * instead of absolute colors (a gray value of 128 means the base color is used without modification).
 * This way the current tinting/shading pipeline
 * and the average color seen at a distance is unchanged. <br><br>
 *
 * Tile id {@link BlockTextureRegistry#UNTEXTURED_ID} represents "no texture"
 * and renders identically to a flat-colored LOD.
 */
public class BlockTextureRegistry
{
	private static final IBlockStateFaceTextureProvider TEXTURE_PROVIDER = SingletonInjector.INSTANCE.get(IBlockStateFaceTextureProvider.class);
	
	/** RGBA */
	public static final int TILE_BYTE_COUNT = TextureUtil.TEXTURE_WIDTH_AND_HEIGHT * TextureUtil.TEXTURE_WIDTH_AND_HEIGHT * 4;
	
	/**
	 * tile ids are stored in 16 vertex bits,
	 * if we somehow run out of ids additional faces just render flat
	 */
	public static final int MAX_TILE_COUNT = 65_536;
	
	/** renders every face flat, also used when set registration overflows */
	public static final short UNTEXTURED_ID = 0;
	
	
	// needs to be defined after the other static variables so it can reference them
	public static final BlockTextureRegistry INSTANCE = new BlockTextureRegistry();
	
	
	
	
	/** indexed by tile id, holds the ratio-encoded RGBA pixels ready for GPU upload */
	private final ArrayList<byte[]> tilePixelsById = new ArrayList<>();
	/** used to dedupe tiles so block faces sharing a texture share a tile */
	private final HashMap<TileKey, Short> tileIdByContent = new HashMap<>();
	/** tiles that haven't been uploaded to the GPU yet, drained by the render thread */
	private int firstTileIdPendingUpload = 0;
	
	/** 
	 * indexed by set id (stored by {@link BlockTextureRegistry#setIdByBlockStateWrapper}), 
	 * holds the 6 face tile ids for one block state.
	 */
	private final ArrayList<short[]> faceTileIdsBySetId = new ArrayList<>();
	private final ConcurrentHashMap<IBlockStateWrapper, Short> setIdByBlockStateWrapper = new ConcurrentHashMap<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private BlockTextureRegistry()
	{
		// reserve the flat tile so id 0 always renders as a 1.0 multiplier
		byte[] flatPixels = new byte[TILE_BYTE_COUNT];
		
		// fill everything with a gray pixel
		for (int i = 0; i < TILE_BYTE_COUNT; i += 4)
		{
			flatPixels[i] = (byte) 128; // half of an unsigned byte, ie gray
			flatPixels[i + 1] = (byte) 128;
			flatPixels[i + 2] = (byte) 128;
			flatPixels[i + 3] = (byte) 0xFF; // max opacity (256)
		}
		this.tilePixelsById.add(flatPixels);
		
		// reserve the all-flat face set so set id 0 always renders flat
		this.faceTileIdsBySetId.add(new short[6]);
	}
	
	//endregion
	
	
	
	//==============//
	// tile getters //
	//==============//
	//region
	
	/**
	 * Returns the id of the given block state's face tile set,
	 * baking and registering the face textures if necessary. <br>
	 * Thread safe, although baking may block briefly.
	 *
	 * @return {@link BlockTextureRegistry#UNTEXTURED_ID} if no textures are available
	 */
	public short getOrRegisterBlockStateSetId(IBlockStateWrapper blockState)
	{
		Short setId = this.setIdByBlockStateWrapper.get(blockState);
		if (setId == null)
		{
			setId = this.registerBlockState(blockState);
		}
		return setId;
	}
	
	/**
	 * Returns the tile id for each face of the given set,
	 * indexed by {@link EDhDirection#faceIndex}. <br>
	 * The returned array must not be modified.
	 *
	 * @return null if the set id is invalid or the flat set
	 */
	public synchronized short @Nullable [] getFaceTileIds(int setId)
	{
		if (setId <= UNTEXTURED_ID || setId >= this.faceTileIdsBySetId.size())
		{
			return null;
		}
		return this.faceTileIdsBySetId.get(setId);
	}
	
	private short registerBlockState(IBlockStateWrapper blockState)
	{
		short[] faceTileIds = new short[6]; // 6 faces on a cube
		boolean anyFaceTextured = false;
		
		for (EDhDirection direction : EDhDirection.ALL)
		{
			BlockFaceTexture faceTexture = TEXTURE_PROVIDER.getFaceTexture(blockState, direction);
			short tileId = (faceTexture != null)
					? this.getOrCreateTileId(faceTexture)
					: UNTEXTURED_ID;
			
			// If no texture exists in the current north/sout/west/east direction,
			// try using the top instead.
			// This fixes fences, walls, anvils, etc.
			// from having untextured sides, but textured tops
			if (tileId == UNTEXTURED_ID
				&& direction != EDhDirection.UP
				&& direction != EDhDirection.DOWN)
			{
				faceTexture = TEXTURE_PROVIDER.getFaceTexture(blockState, EDhDirection.UP);
				tileId = (faceTexture != null)
					? this.getOrCreateTileId(faceTexture)
					: UNTEXTURED_ID;
			}
			
			faceTileIds[direction.faceIndex] = tileId;
			anyFaceTextured |= (tileId != UNTEXTURED_ID);
		}
		
		short textureId;
		if (!anyFaceTextured)
		{
			// blocks whose every face is flat (IE uniformly colored textures)
			// share the reserved flat set
			textureId = UNTEXTURED_ID;
		}
		else
		{
			// synchronized to prevent concurrent array modifications
			synchronized (this)
			{
				if (this.faceTileIdsBySetId.size() >= MAX_TILE_COUNT)
				{
					textureId = UNTEXTURED_ID;
				}
				else
				{
					textureId = (short) this.faceTileIdsBySetId.size();
					this.faceTileIdsBySetId.add(faceTileIds);
				}
			}
		}
		
		Short existingSetId = this.setIdByBlockStateWrapper.putIfAbsent(blockState, textureId);
		return (existingSetId != null) ? existingSetId : textureId;
	}
	
	//endregion
	
	
	
	//===================//
	// tile registration //
	//===================//
	//region
	
	private synchronized short getOrCreateTileId(BlockFaceTexture faceTexture)
	{
		byte[] ratioPixels = convertColorsToDifferenceRatios(faceTexture);
		if (ratioPixels == null)
		{
			return UNTEXTURED_ID;
		}
		
		TileKey key = new TileKey(ratioPixels);
		Short existingId = this.tileIdByContent.get(key);
		if (existingId != null)
		{
			return existingId;
		}
		
		if (this.tilePixelsById.size() >= MAX_TILE_COUNT)
		{
			return UNTEXTURED_ID;
		}
		
		short newId = (short) this.tilePixelsById.size();
		this.tilePixelsById.add(ratioPixels);
		this.tileIdByContent.put(key, newId);
		return newId;
	}
	
	/**
	 * Converts the texture's absolute colors into ratios
	 * relative to the texture's average color.
	 * 128 = 1.0, 255 = 2.0, ratios above 2.0 are clamped.
	 *
	 * @return null if the texture is a single uniform color, IE flat shading is identical
	 */
	private static byte[] convertColorsToDifferenceRatios(BlockFaceTexture faceTexture)
	{
		int[] argbPixels = faceTexture.argbPixels;
		
		// average only the visible pixels, otherwise
		// cutout textures (IE fences) would have overly dark averages.
		// The LOD's base color is averaged in linear light (see ClientBlockStateColorCache.calculateColorFromTexture),
		// so the same average is used here, otherwise textured LODs would be brighter than the real block
		// (IE leaves looking yellow-green on their lit top faces)
		double redSum = 0;
		double greenSum = 0;
		double blueSum = 0;
		double alphaSum = 0;
		for (int i = 0; i < argbPixels.length; i++)
		{
			int alpha = ColorUtil.getAlpha(argbPixels[i]);
			if (alpha != 0)
			{
				redSum += srgbToLinear(ColorUtil.getRed(argbPixels[i])) * alpha;
				greenSum += srgbToLinear(ColorUtil.getGreen(argbPixels[i])) * alpha;
				blueSum += srgbToLinear(ColorUtil.getBlue(argbPixels[i])) * alpha;
				alphaSum += alpha;
			}
		}
		if (alphaSum == 0)
		{
			return null;
		}

		float averageRed = Math.max(linearToSrgb(redSum / alphaSum), 1.0f);
		float averageGreen = Math.max(linearToSrgb(greenSum / alphaSum), 1.0f);
		float averageBlue = Math.max(linearToSrgb(blueSum / alphaSum), 1.0f);

		// relative to the color the face is actually drawn with
		if (faceTexture.ratioReferenceColor != 0)
		{
			averageRed = Math.max(ColorUtil.getRed(faceTexture.ratioReferenceColor), 1.0f);
			averageGreen = Math.max(ColorUtil.getGreen(faceTexture.ratioReferenceColor), 1.0f);
			averageBlue = Math.max(ColorUtil.getBlue(faceTexture.ratioReferenceColor), 1.0f);
		}
		
		
		byte[] uploadPixels = new byte[TILE_BYTE_COUNT];
		boolean anyPixelDiffersFromAverage = false;
		for (int v = 0; v < TextureUtil.TEXTURE_WIDTH_AND_HEIGHT; v++)
		{
			for (int u = 0; u < TextureUtil.TEXTURE_WIDTH_AND_HEIGHT; u++)
			{
				// 1x1 fallback tiles repeat their single pixel
				int sourceIndex = ((v * faceTexture.height / TextureUtil.TEXTURE_WIDTH_AND_HEIGHT) * faceTexture.width)
						+ (u * faceTexture.width / TextureUtil.TEXTURE_WIDTH_AND_HEIGHT);
				int argb = argbPixels[sourceIndex];
				
				byte red, green, blue, alpha;
				if (faceTexture.uploadAsColorRatio)
				{
					// upload as a ratio so the texture modifies the base DH defined color
					float redRatio = ColorUtil.getRed(argb) / averageRed;
					float greenRatio = ColorUtil.getGreen(argb) / averageGreen;
					float blueRatio = ColorUtil.getBlue(argb) / averageBlue;
					// ratios above the max are scaled down together,
					// clamping them separately would shift the pixel's hue (IE tint leaves yellow)
					float ratioScale = MAX_RATIO / Math.max(MAX_RATIO, Math.max(redRatio, Math.max(greenRatio, blueRatio)));
					red   = encodeRatio(redRatio * ratioScale);
					green = encodeRatio(greenRatio * ratioScale);
					blue  = encodeRatio(blueRatio * ratioScale);
					alpha = (byte)ColorUtil.getAlpha(argb);
				}
				else
				{
					// upload as an absolute color
					// (due to how rendering is done this will only partially work,
					// but is helpful for the error texture to appear correctly)
					red   = (byte) ColorUtil.getRed(argb);
					green = (byte) ColorUtil.getGreen(argb);
					blue  = (byte) ColorUtil.getBlue(argb);
					alpha = (byte) ColorUtil.getAlpha(argb);
				}
				
				int outIndex = TextureUtil.getPixelIndex(u,v) * 4;
				uploadPixels[outIndex] = red;
				uploadPixels[outIndex + 1] = green;
				uploadPixels[outIndex + 2] = blue;
				uploadPixels[outIndex + 3] = alpha;
				
				
				// only check if the pixel differs when the alpha is visible (non-zero)
				if (alpha != 0)
				{
					// 128 is gray (half-way between 0 and 256)
					byte averageGray = (byte) 128;
					
					// we only care about the texture if it differs from the average block color 
					// (represented by gray in the uploaded texture)
					anyPixelDiffersFromAverage |=
						red != averageGray
						|| green != averageGray
						|| blue != averageGray;
				}
			}
		}
		
		return anyPixelDiffersFromAverage ? uploadPixels : null;
	}
	/** @param channel 0-255 */
	private static double srgbToLinear(int channel)
	{
		double value = channel / 255.0;
		return (value <= 0.04045) ? (value / 12.92) : Math.pow((value + 0.055) / 1.055, 2.4);
	}
	/** @return 0-255 */
	private static float linearToSrgb(double value)
	{
		double srgb = (value <= 0.0031308) ? (value * 12.92) : (1.055 * Math.pow(value, 1.0 / 2.4) - 0.055);
		return (float) (srgb * 255.0);
	}

	/** the largest ratio a tile pixel can store (255) */
	private static final float MAX_RATIO = 2.0f;
	private static byte encodeRatio(float ratio)
	{
		int encoded = Math.round(ratio * 127.5f);
		return (byte) Math.min(encoded, 255);
	}
	
	//endregion
	
	
	
	//============//
	// GPU upload //
	//============//
	//region
	
	/**
	 * Returns the tiles registered since the last call so they can
	 * be uploaded to the GPU, must be called from the render thread.
	 */
	public synchronized PendingTiles getAndClearPendingUploadTiles()
	{
		int firstId = this.firstTileIdPendingUpload;
		int tileCount = this.tilePixelsById.size() - firstId;
		if (tileCount <= 0)
		{
			return null;
		}
		
		byte[][] pixelArrays = new byte[tileCount][];
		for (int i = 0; i < tileCount; i++)
		{
			pixelArrays[i] = this.tilePixelsById.get(firstId + i);
		}
		this.firstTileIdPendingUpload = this.tilePixelsById.size();
		return new PendingTiles(firstId, pixelArrays);
	}
	
	public synchronized int getTileCount() { return this.tilePixelsById.size(); }
	
	/** Makes every tile pending again, used when the GPU atlas is re-allocated. */
	public synchronized void resetPendingUploads() { this.firstTileIdPendingUpload = 0; }
	
	/** Should be called whenever MC's textures change, IE when resource packs are swapped. */
	public synchronized void clear()
	{
		this.setIdByBlockStateWrapper.clear();
		short[] flatSet = this.faceTileIdsBySetId.get(UNTEXTURED_ID);
		this.faceTileIdsBySetId.clear();
		this.faceTileIdsBySetId.add(flatSet);
		
		this.tileIdByContent.clear();
		byte[] flatTile = this.tilePixelsById.get(UNTEXTURED_ID);
		this.tilePixelsById.clear();
		this.tilePixelsById.add(flatTile);
		this.firstTileIdPendingUpload = 0;
		
		IBlockStateFaceTextureProvider textureProvider = SingletonInjector.INSTANCE.get(IBlockStateFaceTextureProvider.class);
		if (textureProvider != null)
		{
			textureProvider.clear();
		}
	}
	
	//endregion
	
	
	
	//================//
	// helper classes //
	//================//
	//region
	
	/** wraps tile pixels so they can be used as a hash map key for deduplication */
	private static class TileKey
	{
		private final byte[] pixels;
		private final int hash;
		
		public TileKey(byte[] pixels)
		{
			this.pixels = pixels;
			this.hash = Arrays.hashCode(pixels);
		}
		
		@Override
		public int hashCode() { return this.hash; }
		
		@Override
		public boolean equals(Object obj)
		{
			if (this == obj) 
			{
				return true; 
			}
			
			if (!(obj instanceof TileKey)) 
			{
				return false; 
			}
			
			return Arrays.equals(this.pixels, ((TileKey) obj).pixels);
		}
		
	}
	
	public static class PendingTiles
	{
		public final int firstTileId;
		public final byte[][] tilePixels;
		
		public PendingTiles(int firstTileId, byte[][] tilePixels)
		{
			this.firstTileId = firstTileId;
			this.tilePixels = tilePixels;
		}
	}
	
	//endregion
	
	
	
}
