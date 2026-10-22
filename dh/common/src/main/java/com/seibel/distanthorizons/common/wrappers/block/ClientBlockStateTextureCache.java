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

package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDirection;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockTextureOverrideEvent;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.coreapi.util.TextureUtil;
import com.seibel.distanthorizons.common.backports.FakeBlockState;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.ColorizerGrass;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Bakes and stores a small texture for each face of a block state
 * by sampling the block's icon for that face. <br>
 * This allows LODs to show the block's actual texture
 * instead of a single flat color.
 *
 * @see ClientBlockStateColorCache
 * @see BlockFaceTexture
 * @see BlockTextureRegistry
 */
public class ClientBlockStateTextureCache
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	/**
	 * The resolution face textures are baked at. <br>
	 * Sprites with a higher resolution (IE from resource packs) are down-sampled.
	 */
	public static final int TEXTURE_WIDTH_AND_HEIGHT = TextureUtil.TEXTURE_WIDTH_AND_HEIGHT;

	/** The bake order */
	private static final EDhDirection[] FACE_DIRECTIONS =
		{
			EDhDirection.DOWN,
			EDhDirection.UP,
			EDhDirection.NORTH,
			EDhDirection.SOUTH,
			EDhDirection.WEST,
			EDhDirection.EAST
		};

	/** Mod icon lookups aren't guaranteed to be thread safe, so all baking is done in this lock. */
	private static final ReentrantLock BAKE_LOCK = new ReentrantLock();

	private static final ConcurrentHashMap<BlockStateWrapper, BlockFaceTexture[]> TEXTURES_BY_BLOCK_WRAPPER = new ConcurrentHashMap<>();

	private static final ThreadLocal<DhApiBlockTextureOverrideEvent.EventParam> TEXTURE_OVERRIDE_EVENT_PARAM_THREAD_LOCAL = ThreadLocal.withInitial(DhApiBlockTextureOverrideEvent.EventParam::new);




	//================//
	// public methods //
	//================//
	//region

	public static BlockFaceTexture getFaceTexture(BlockStateWrapper blockStateWrapper, EDhDirection direction)
	{
		BlockFaceTexture[] faceTextures = TEXTURES_BY_BLOCK_WRAPPER.computeIfAbsent(
				blockStateWrapper,
				ClientBlockStateTextureCache::computeFaceTextures);
		return faceTextures[direction.faceIndex];
	}

	/** Should be called whenever MC's textures change, IE when resource packs are swapped. */
	public static void clearCache() { TEXTURES_BY_BLOCK_WRAPPER.clear(); }

	//endregion



	//============//
	// API events //
	//============//
	//region

	private static BlockFaceTexture[] computeFaceTextures(BlockStateWrapper blockStateWrapper)
	{
		BlockFaceTexture[] blockFaceTextures = bakeAllFaceTextures(blockStateWrapper);

		// allow changing the texture if the API event is requested
		// (this is done to reduce GC pressure and speed up color getting)
		if (blockStateWrapper.allowApiTextureOverride())
		{
			DhApiBlockTextureOverrideEvent.EventParam eventParam = TEXTURE_OVERRIDE_EVENT_PARAM_THREAD_LOCAL.get();

			for (int faceIndex = 0; faceIndex < FACE_DIRECTIONS.length; faceIndex++)
			{
				BlockFaceTexture blockFaceTexture = blockFaceTextures[faceIndex];
				EDhDirection faceDirection = FACE_DIRECTIONS[faceIndex];

				eventParam.update(
					blockStateWrapper,
					faceDirection.getApiVersion(),
					// the current pixels are needed, otherwise listeners that don't change this face would blank it
					blockFaceTexture.argbPixels,
					ClientBlockStateTextureCache::ApiTextureRegenFunc
				);
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiBlockTextureOverrideEvent.class, eventParam);

				// let the API user change this texture
				blockFaceTexture.updateFromApiEvent(eventParam);
			}
		}

		return blockFaceTextures;
	}

	public static void ApiTextureRegenFunc(DhApiBlockTextureOverrideEvent.EventParam newEventParam, IDhApiBlockStateWrapper newApiBlockState, EDhApiDirection newApiDirection)
	{
		if (!(newApiBlockState instanceof BlockStateWrapper))
		{
			throw new ClassCastException("Invalid ["+ IDhApiBlockStateWrapper.class.getSimpleName()+"] value given. Block State wrapper object must be one given by the DH API (it can't be a custom implementation), specifically of type ["+ BlockStateWrapper.class.getName()+"].");
		}
		BlockStateWrapper newBlockState = (BlockStateWrapper) newApiBlockState;


		BlockFaceTexture newFaceTexture = bakeFaceTexture(newBlockState, EDhDirection.fromApiVersion(newApiDirection));
		for (int u = 0; u < TextureUtil.TEXTURE_WIDTH_AND_HEIGHT; u++)
		{
			for (int v = 0; v < TextureUtil.TEXTURE_WIDTH_AND_HEIGHT; v++)
			{
				int newColor = newFaceTexture.argbPixels[TextureUtil.getPixelIndex(u, v)];
				int a = ColorUtil.getAlpha(newColor);
				int r = ColorUtil.getRed(newColor);
				int g = ColorUtil.getGreen(newColor);
				int b = ColorUtil.getBlue(newColor);
				newEventParam.setColor(u, v, a, r, g, b);
			}
		}
	}

	//endregion



	//================//
	// texture baking //
	//================//
	//region

	private static BlockFaceTexture[] bakeAllFaceTextures(BlockStateWrapper blockStateWrapper)
	{
		BlockFaceTexture[] faceTextures = new BlockFaceTexture[FACE_DIRECTIONS.length];


		// only render textures if requested
		if (!blockStateWrapper.renderTexture())
		{
			for (int faceIndex = 0; faceIndex < faceTextures.length; faceIndex++)
			{
				// just use the invisible color for each direction
				faceTextures[faceIndex] = BlockFaceTexture.createSolidColor(ColorUtil.INVISIBLE);
			}
			return faceTextures;
		}


		try
		{
			BAKE_LOCK.lock();

			if (BlockStateWrapper.isAir(blockStateWrapper.blockState))
			{
				// Air shouldn't be rendered and is just here to capture the rare bug state where air's texture is requested.
				BlockFaceTexture invisibleTexture = BlockFaceTexture.createSolidColor(ColorUtil.INVISIBLE);
				Arrays.fill(faceTextures, invisibleTexture);
				return faceTextures;
			}

			if (blockStateWrapper.isLiquid())
			{
				// liquids use the same sprite on every face
				BlockFaceTexture liquidTexture = bakeSpriteTexture(getParticleSprite(blockStateWrapper));
				Arrays.fill(faceTextures, liquidTexture);
				return faceTextures;
			}

			for (int faceIndex = 0; faceIndex < FACE_DIRECTIONS.length; faceIndex++)
			{
				faceTextures[faceIndex] = bakeFaceTexture(blockStateWrapper, FACE_DIRECTIONS[faceIndex]);
			}

			matchVanillaFaces(blockStateWrapper, faceTextures);
		}
		catch (Exception bakeError)
		{
			LOGGER.warn("Failed to bake face textures for block ["+blockStateWrapper.getSerialString()+"], error: ["+bakeError.getMessage()+"], block will render as hot pink.", bakeError);
		}
		finally
		{
			BAKE_LOCK.unlock();
		}

		for (int faceIndex = 0; faceIndex < faceTextures.length; faceIndex++)
		{
			if (faceTextures[faceIndex] == null)
			{
				faceTextures[faceIndex] = BlockFaceTexture.createErrorGridTexture();
			}
		}



		return faceTextures;
	}
	private static BlockFaceTexture bakeFaceTexture(BlockStateWrapper blockStateWrapper, EDhDirection dhDirection)
	{
		TextureAtlasSprite sprite = getFaceSprite(blockStateWrapper, dhDirection);

		// leaves use their fancy graphics texture so the gaps can be found (see matchVanillaFaces),
		// with fast graphics MC gives the opaque one
		if (sprite != null
			&& blockStateWrapper.blockState != null
			&& blockStateWrapper.blockState.getBlock() instanceof BlockLeavesBase
			&& sprite.getIconName().endsWith(OPAQUE_LEAVES_SUFFIX))
		{
			String fancyName = sprite.getIconName().substring(0, sprite.getIconName().length() - OPAQUE_LEAVES_SUFFIX.length());
			TextureAtlasSprite fancySprite = TextureAtlasSpriteWrapper.findBlockAtlasSprite(fancyName);
			if (fancySprite != null)
			{
				sprite = fancySprite;
			}
		}

		return bakeSpriteTexture(sprite);
	}
	private static final String OPAQUE_LEAVES_SUFFIX = "_opaque";
	/**
	 * The gaps of MC's fast graphics leaves are this dark gray. <br>
	 * The 254 alpha marks them as gaps, DH's shader draws them as is,
	 * shader packs show the lit leaves behind them like MC does (see ShaderPackLodTextures).
	 */
	private static final int LEAVES_GAP_COLOR = ColorUtil.argbToInt(254, 44, 44, 44);

	/**
	 * Makes each face's texture relative to the color its LOD face is drawn with,
	 * so textured faces look like the real block's faces.
	 */
	private static void matchVanillaFaces(BlockStateWrapper blockStateWrapper, BlockFaceTexture[] faceTextures)
	{
		if (!(blockStateWrapper.blockState instanceof FakeBlockState))
		{
			return;
		}
		FakeBlockState blockState = (FakeBlockState) blockStateWrapper.blockState;

		int untintedColor = ClientBlockStateColorCache.calculateUntintedColor(blockState);
		boolean isGrass = blockState.block instanceof BlockGrass;
		int dirtColor = isGrass ? ClientBlockStateColorCache.calculateUntintedColor(new FakeBlockState(Blocks.dirt, 0)) : 0;

		for (int faceIndex = 0; faceIndex < faceTextures.length; faceIndex++)
		{
			BlockFaceTexture faceTexture = faceTextures[faceIndex];
			if (faceTexture == null || !faceTexture.uploadAsColorRatio)
			{
				continue;
			}

			if (blockState.block instanceof BlockLeavesBase)
			{
				for (int i = 0; i < faceTexture.argbPixels.length; i++)
				{
					if (ColorUtil.getAlpha(faceTexture.argbPixels[i]) == 0)
					{
						faceTexture.argbPixels[i] = LEAVES_GAP_COLOR;
					}
				}
			}

			EDhDirection direction = FACE_DIRECTIONS[faceIndex];
			if (isGrass && direction != EDhDirection.UP)
			{
				// grass sides and bottoms are drawn with the dirt color (see LodQuadBuilder)
				if (direction != EDhDirection.DOWN)
				{
					addGrassSideOverlay(faceTexture);
				}
				faceTexture.ratioReferenceColor = dirtColor;
			}
			else
			{
				faceTexture.ratioReferenceColor = untintedColor;
			}
		}
	}

	/**
	 * MC draws grass sides as the dirt-like side texture with the biome tinted overlay on top. <br>
	 * Grass sides are drawn with the dirt color, which has no biome tint,
	 * so the overlay is tinted with MC's forest grass color (temperature 0.7, rainfall 0.8).
	 */
	private static void addGrassSideOverlay(BlockFaceTexture faceTexture)
	{
		IIcon overlayIcon = BlockGrass.getIconSideOverlay();
		TextureAtlasSprite overlaySprite = (overlayIcon != null) ? TextureAtlasSpriteWrapper.findBlockAtlasSprite(overlayIcon.getIconName()) : null;
		if (overlaySprite == null)
		{
			return;
		}

		BlockFaceTexture overlayTexture = bakeSpriteTexture(overlaySprite);
		for (int i = 0; i < faceTexture.argbPixels.length && i < overlayTexture.argbPixels.length; i++)
		{
			int overlay = overlayTexture.argbPixels[i];
			int overlayAlpha = ColorUtil.getAlpha(overlay);
			if (overlayAlpha == 0)
			{
				continue;
			}

			int tinted = ColorUtil.multiplyARGBwithRGB(overlay, ColorizerGrass.getGrassColor(0.7, 0.8));
			float blend = overlayAlpha / 255.0f;
			int base = faceTexture.argbPixels[i];
			faceTexture.argbPixels[i] = ColorUtil.argbToInt(
				255,
				Math.round(ColorUtil.getRed(base) + (ColorUtil.getRed(tinted) - ColorUtil.getRed(base)) * blend),
				Math.round(ColorUtil.getGreen(base) + (ColorUtil.getGreen(tinted) - ColorUtil.getGreen(base)) * blend),
				Math.round(ColorUtil.getBlue(base) + (ColorUtil.getBlue(tinted) - ColorUtil.getBlue(base)) * blend));
		}
	}


	/** Copies the given sprite, down-sampled to {@link #TEXTURE_WIDTH_AND_HEIGHT}. */
	private static BlockFaceTexture bakeSpriteTexture(@Nullable TextureAtlasSprite sprite)
	{
		if (sprite == null)
		{
			return BlockFaceTexture.createErrorGridTexture();
		}

		int spriteWidth = TextureAtlasSpriteWrapper.getWidth(sprite);
		int spriteHeight = TextureAtlasSpriteWrapper.getHeight(sprite);
		if (spriteWidth <= 0
			|| spriteHeight <= 0)
		{
			return BlockFaceTexture.createErrorGridTexture();
		}

		int[] pixels = new int[TEXTURE_WIDTH_AND_HEIGHT * TEXTURE_WIDTH_AND_HEIGHT];
		for (int u = 0; u < TEXTURE_WIDTH_AND_HEIGHT; u++)
		{
			for (int v = 0; v < TEXTURE_WIDTH_AND_HEIGHT; v++)
			{
				int texelX = (u * spriteWidth) / TEXTURE_WIDTH_AND_HEIGHT;
				int texelY = (v * spriteHeight) / TEXTURE_WIDTH_AND_HEIGHT;
				pixels[TextureUtil.getPixelIndex(u,v)] = TextureAtlasSpriteWrapper.getPixelARGB(sprite, 0, texelX, texelY);
			}
		}
		return BlockFaceTexture.createTexture(TEXTURE_WIDTH_AND_HEIGHT, TEXTURE_WIDTH_AND_HEIGHT, pixels);
	}

	//endregion



	//================//
	// helper methods //
	//================//
	//region

	@Nullable
	private static TextureAtlasSprite getParticleSprite(BlockStateWrapper blockStateWrapper)
	{
		if (blockStateWrapper.blockState == null)
		{
			return null;
		}

		try
		{
			return getFaceSprite(blockStateWrapper, EDhDirection.UP);
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to get particle sprite for block ["+blockStateWrapper.getSerialString()+"], error: ["+e.getMessage()+"].", e);
			return null;
		}
	}

	@Nullable
	private static TextureAtlasSprite getFaceSprite(BlockStateWrapper blockStateWrapper, EDhDirection direction)
	{
		if (blockStateWrapper.blockState == null)
		{
			return null;
		}

		// shouldn't happen, but just in case
		int directionIndex = 0;
		ForgeDirection forgeDirection = McObjectConverter.convert(direction);
		if (forgeDirection != null)
		{
			directionIndex = forgeDirection.ordinal();
		}

		return TextureAtlasSpriteWrapper.resolveFaceSprite(blockStateWrapper.blockState, directionIndex);
	}


	//endregion



}
