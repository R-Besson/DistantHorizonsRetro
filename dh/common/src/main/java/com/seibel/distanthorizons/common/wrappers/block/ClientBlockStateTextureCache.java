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

import com.seibel.distanthorizons.common.backports.FakeWorld;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IChiselCommonAccessor;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDirection;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockTextureOverrideEvent;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateFaceTextureProvider;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
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
import net.minecraft.world.IBlockAccess;
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
	public static void clearCache()
	{
		TEXTURES_BY_BLOCK_WRAPPER.clear();
		AVERAGE_COLOR_BY_APPEARANCE.clear();
		TEXTURED_COLOR_BY_APPEARANCE.clear();
	}

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



	//=====================//
	// positioned textures //
	//=====================//
	//region

	private static final ThreadLocal<FakeWorld> FAKE_WORLD_REF = ThreadLocal.withInitial(FakeWorld::new);
	/** block states whose positioned icons threw, they use their normal textures */
	private static final Set<BlockStateWrapper> BROKEN_POSITIONED_BLOCK_STATES = ConcurrentHashMap.newKeySet();

	/**
	 * Gets the icons MC draws each face with at this position,
	 * IE Chisel's connected textures depend on the neighbors and its variants on the position.
	 *
	 * @see IBlockStateFaceTextureProvider#getPositionedAppearance
	 */
	@Nullable
	public static Object getPositionedAppearance(
		BlockStateWrapper blockStateWrapper, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ)
	{ return getPositionedAppearance(blockStateWrapper, levelWrapper, fullDataSource, blockX, topY, blockZ, true); }
	/** @param withSideRows false if only the LOD's color is needed */
	@Nullable
	private static PositionedAppearance getPositionedAppearance(
		BlockStateWrapper blockStateWrapper, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ, boolean withSideRows)
	{
		IChiselCommonAccessor chiselAccessor = ModAccessorInjector.INSTANCE.get(IChiselCommonAccessor.class);
		if (chiselAccessor == null
			|| !(blockStateWrapper.blockState instanceof FakeBlockState)
			|| !chiselAccessor.hasPositionedTextures(((FakeBlockState) blockStateWrapper.blockState).block)
			|| !blockStateWrapper.renderTexture()
			// API texture overrides are per block state
			|| blockStateWrapper.allowApiTextureOverride()
			// user color overrides are final colors
			|| BlockColorOverrides.get(((FakeBlockState) blockStateWrapper.blockState).block, ((FakeBlockState) blockStateWrapper.blockState).meta) != null
			|| BROKEN_POSITIONED_BLOCK_STATES.contains(blockStateWrapper))
		{
			return null;
		}
		FakeBlockState blockState = (FakeBlockState) blockStateWrapper.blockState;
		IBlockAccess realLevel = (IBlockAccess) levelWrapper.getWrappedMcObject();
		FakeWorld fakeWorld = FAKE_WORLD_REF.get();

		IIcon[][] iconsByFaceIndex = new IIcon[FACE_DIRECTIONS.length][];
		IIcon[][][] sideIconsByRow = null;
		try
		{
			BAKE_LOCK.lock();

			// LODs usually reach down into the ground below the visible blocks,
			// only the blocks of the same type above it are the wall MC draws.
			// The wall is found from the top block alone so the LOD's color and textures get the same appearance
			fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, topY, blockZ, blockState);
			int wallBottomY = fakeWorld.getLowestSameBlockY(blockX, topY, levelWrapper.getMinHeight(), blockZ);

			for (EDhDirection direction : FACE_DIRECTIONS)
			{
				// tall LODs repeat one texture down their sides, the middle block's matches most of the wall
				int blockY;
				if (direction == EDhDirection.UP)
				{
					blockY = topY;
				}
				else if (direction == EDhDirection.DOWN)
				{
					blockY = wallBottomY;
				}
				else
				{
					blockY = (wallBottomY + topY + 1) / 2;
				}

				fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, blockY, blockZ, blockState);
				iconsByFaceIndex[direction.faceIndex] = chiselAccessor.getFaceIcons(fakeWorld, blockState.block, blockX, blockY, blockZ, McObjectConverter.convert(direction).ordinal());
			}

			// each row of the wall gets its own sides, IE the top and bottom borders or a 3x3 variant
			int wallRowCount = topY - wallBottomY + 1;
			if (withSideRows
				&& wallRowCount > 1)
			{
				sideIconsByRow = new IIcon[Math.min(wallRowCount, MAX_SIDE_ROWS)][][];
				for (int row = 0; row < sideIconsByRow.length; row++)
				{
					int blockY = topY - row;
					fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, blockY, blockZ, blockState);

					sideIconsByRow[row] = new IIcon[FACE_DIRECTIONS.length][];
					for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
					{
						sideIconsByRow[row][direction.faceIndex] = chiselAccessor.getFaceIcons(fakeWorld, blockState.block, blockX, blockY, blockZ, McObjectConverter.convert(direction).ordinal());
					}
				}
			}
		}
		catch (Exception e)
		{
			if (BROKEN_POSITIONED_BLOCK_STATES.add(blockStateWrapper))
			{
				LOGGER.warn("Failed to get the positioned textures for block ["+blockStateWrapper.getSerialString()+"], error: ["+e.getMessage()+"], it will use the same texture everywhere.", e);
			}
			return null;
		}
		finally
		{
			BAKE_LOCK.unlock();
		}

		return new PositionedAppearance(blockStateWrapper, iconsByFaceIndex, sideIconsByRow);
	}
	/** taller walls use the middle row's sides below this, a cap keeps the cost of tall walls down */
	private static final int MAX_SIDE_ROWS = 32;

	/** @see IBlockStateFaceTextureProvider#getPositionedFaceTexture */
	@Nullable
	public static BlockFaceTexture getPositionedFaceTexture(Object appearanceObject, EDhDirection direction)
	{
		PositionedAppearance appearance = (PositionedAppearance) appearanceObject;
		IIcon[] icons = appearance.iconsByFaceIndex[direction.faceIndex];
		if (icons == null)
		{
			return getFaceTexture(appearance.blockStateWrapper, direction);
		}
		return bakePositionedTexture(appearance, icons);
	}

	/** @see IBlockStateFaceTextureProvider#getPositionedSideRowCount */
	public static int getPositionedSideRowCount(Object appearanceObject)
	{
		IIcon[][][] sideIconsByRow = ((PositionedAppearance) appearanceObject).sideIconsByRow;
		return (sideIconsByRow != null) ? sideIconsByRow.length : 0;
	}

	/** @see IBlockStateFaceTextureProvider#getPositionedSideRowTexture */
	@Nullable
	public static BlockFaceTexture getPositionedSideRowTexture(Object appearanceObject, EDhDirection direction, int row)
	{
		PositionedAppearance appearance = (PositionedAppearance) appearanceObject;
		IIcon[] icons = (appearance.sideIconsByRow != null) ? appearance.sideIconsByRow[row][direction.faceIndex] : null;
		return (icons != null) ? bakePositionedTexture(appearance, icons) : getPositionedFaceTexture(appearance, direction);
	}

	@Nullable
	private static BlockFaceTexture bakePositionedTexture(PositionedAppearance appearance, IIcon[] icons)
	{
		try
		{
			BAKE_LOCK.lock();

			BlockFaceTexture faceTexture = bakeIconTexture(icons);
			if (faceTexture != null)
			{
				// relative to the color the LOD face is drawn with, see getPositionedColor
				faceTexture.ratioReferenceColor = getTexturedColor(appearance);

				// A tile's alpha is how much of its color covers the LOD's, the LOD's own alpha makes glass see-through.
				// Partly see-through pixels (IE stained glass) are fully colored like MC's,
				// fully see-through ones are cut out of see-through LODs
				for (int i = 0; i < faceTexture.argbPixels.length; i++)
				{
					if (ColorUtil.getAlpha(faceTexture.argbPixels[i]) != 0)
					{
						faceTexture.argbPixels[i] = ColorUtil.setAlpha(faceTexture.argbPixels[i], 255);
					}
				}
			}
			return faceTexture;
		}
		finally
		{
			BAKE_LOCK.unlock();
		}
	}

	/**
	 * The LOD color of a block that looks different depending on where it is,
	 * averaged from what MC draws at this position. <br>
	 * A single color per block state would be far off for some positions
	 * (IE a Chisel block with dark connected pieces and a bright frame when it's alone)
	 * and textured LODs can only brighten their color up to twice. <br>
	 * Textured LODs get {@link #getTexturedColor} instead, which their textures fully cover.
	 *
	 * @return null if the block looks the same everywhere
	 */
	@Nullable
	public static Integer getPositionedColor(
		BlockStateWrapper blockStateWrapper, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ)
	{
		// the same appearance as the textures get, see FullDataToRenderDataTransformer.applyTextureSetIds
		boolean textured = ColumnRenderSource.texturedLodsEnabledAtDetailLevel(fullDataSource.getDataDetailLevel());
		PositionedAppearance appearance = getPositionedAppearance(blockStateWrapper, levelWrapper, fullDataSource, blockX, topY, blockZ, textured);
		if (appearance == null)
		{
			return null;
		}
		return textured ? getTexturedColor(appearance) : getAverageColor(appearance);
	}
	private static final ConcurrentHashMap<PositionedAppearance, Integer> AVERAGE_COLOR_BY_APPEARANCE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<PositionedAppearance, Integer> TEXTURED_COLOR_BY_APPEARANCE = new ConcurrentHashMap<>();

	/**
	 * The color textured LODs are drawn with, the average brightened so every pixel's ratio fits
	 * (IE glotek's bright glyph on its dark background, or the gold on Chisel's chinese glass). <br>
	 * The texture covers the whole face so this color isn't seen, up close or in the distance (where it's mipmapped).
	 */
	private static int getTexturedColor(PositionedAppearance appearance)
	{
		Integer cachedColor = TEXTURED_COLOR_BY_APPEARANCE.get(appearance);
		if (cachedColor != null)
		{
			return cachedColor;
		}

		int maxRed = 0;
		int maxGreen = 0;
		int maxBlue = 0;
		try
		{
			BAKE_LOCK.lock();

			ArrayList<IIcon[]> allIcons = new ArrayList<>(Arrays.asList(appearance.iconsByFaceIndex));
			if (appearance.sideIconsByRow != null)
			{
				for (IIcon[][] rowIcons : appearance.sideIconsByRow)
				{
					allIcons.addAll(Arrays.asList(rowIcons));
				}
			}

			for (IIcon[] icons : allIcons)
			{
				BlockFaceTexture faceTexture = (icons != null) ? bakeIconTexture(icons) : null;
				if (faceTexture == null)
				{
					continue;
				}

				for (int argb : faceTexture.argbPixels)
				{
					if (ColorUtil.getAlpha(argb) != 0)
					{
						maxRed = Math.max(maxRed, ColorUtil.getRed(argb));
						maxGreen = Math.max(maxGreen, ColorUtil.getGreen(argb));
						maxBlue = Math.max(maxBlue, ColorUtil.getBlue(argb));
					}
				}
			}
		}
		finally
		{
			BAKE_LOCK.unlock();
		}

		// textured LODs brighten their color at most twice, see BlockTextureRegistry.MAX_RATIO
		int averageColor = getAverageColor(appearance);
		int color = ColorUtil.argbToInt(255,
			Math.max(ColorUtil.getRed(averageColor), (maxRed + 1) / 2),
			Math.max(ColorUtil.getGreen(averageColor), (maxGreen + 1) / 2),
			Math.max(ColorUtil.getBlue(averageColor), (maxBlue + 1) / 2));
		TEXTURED_COLOR_BY_APPEARANCE.put(appearance, color);
		return color;
	}

	/**
	 * averaged over every textured face in linear light, like ClientBlockStateColorCache's colors. <br>
	 * The side rows aren't included, the LOD's color is found without them.
	 */
	private static int getAverageColor(PositionedAppearance appearanceWithRows)
	{
		PositionedAppearance appearance = (appearanceWithRows.sideIconsByRow == null)
			? appearanceWithRows
			: new PositionedAppearance(appearanceWithRows.blockStateWrapper, appearanceWithRows.iconsByFaceIndex, null);
		Integer cachedColor = AVERAGE_COLOR_BY_APPEARANCE.get(appearance);
		if (cachedColor != null)
		{
			return cachedColor;
		}

		double red = 0;
		double green = 0;
		double blue = 0;
		double alphaSum = 0;
		try
		{
			BAKE_LOCK.lock();
			for (IIcon[] icons : appearance.iconsByFaceIndex)
			{
				BlockFaceTexture faceTexture = (icons != null) ? bakeIconTexture(icons) : null;
				if (faceTexture == null)
				{
					continue;
				}

				for (int argb : faceTexture.argbPixels)
				{
					int alpha = ColorUtil.getAlpha(argb);
					red += srgbToLinear(ColorUtil.getRed(argb)) * alpha;
					green += srgbToLinear(ColorUtil.getGreen(argb)) * alpha;
					blue += srgbToLinear(ColorUtil.getBlue(argb)) * alpha;
					alphaSum += alpha;
				}
			}
		}
		finally
		{
			BAKE_LOCK.unlock();
		}

		int color = (alphaSum == 0)
			? ClientBlockStateColorCache.calculateUntintedColor((FakeBlockState) appearance.blockStateWrapper.blockState)
			: ColorUtil.argbToInt(255, linearToSrgb(red / alphaSum), linearToSrgb(green / alphaSum), linearToSrgb(blue / alphaSum));
		AVERAGE_COLOR_BY_APPEARANCE.put(appearance, color);
		return color;
	}
	/** @param channel 0-255 */
	private static double srgbToLinear(int channel)
	{
		double value = channel / 255.0;
		return (value <= 0.04045) ? (value / 12.92) : Math.pow((value + 0.055) / 1.055, 2.4);
	}
	/** @return 0-255 */
	private static int linearToSrgb(double value)
	{
		double srgb = (value <= 0.0031308) ? (value * 12.92) : (1.055 * Math.pow(value, 1.0 / 2.4) - 0.055);
		return (int) Math.round(Math.max(0, Math.min(1, srgb)) * 255.0);
	}

	/**
	 * @param icons either one icon for the whole face,
	 *              or four quarters (bottom left, bottom right, top right, top left) like Chisel's connected textures
	 * @return null if an icon is missing
	 */
	@Nullable
	private static BlockFaceTexture bakeIconTexture(IIcon[] icons)
	{
		for (IIcon icon : icons)
		{
			if (icon == null)
			{
				return null;
			}
		}

		int halfWidth = TEXTURE_WIDTH_AND_HEIGHT / 2;
		int[] pixels = new int[TEXTURE_WIDTH_AND_HEIGHT * TEXTURE_WIDTH_AND_HEIGHT];
		for (int u = 0; u < TEXTURE_WIDTH_AND_HEIGHT; u++)
		{
			for (int v = 0; v < TEXTURE_WIDTH_AND_HEIGHT; v++)
			{
				IIcon icon;
				float fractionU;
				float fractionV;
				if (icons.length == 4)
				{
					boolean right = (u >= halfWidth);
					boolean bottom = (v >= halfWidth);
					icon = bottom ? icons[right ? 1 : 0] : icons[right ? 2 : 3];
					fractionU = ((u % halfWidth) + 0.5f) / halfWidth;
					fractionV = ((v % halfWidth) + 0.5f) / halfWidth;
				}
				else
				{
					icon = icons[0];
					fractionU = (u + 0.5f) / TEXTURE_WIDTH_AND_HEIGHT;
					fractionV = (v + 0.5f) / TEXTURE_WIDTH_AND_HEIGHT;
				}
				pixels[TextureUtil.getPixelIndex(u, v)] = TextureAtlasSpriteWrapper.getIconPixelARGB(icon, fractionU, fractionV);
			}
		}
		return BlockFaceTexture.createTexture(TEXTURE_WIDTH_AND_HEIGHT, TEXTURE_WIDTH_AND_HEIGHT, pixels);
	}

	/** the icons a block state's faces are drawn with at one position, equal icons share a texture set */
	private static final class PositionedAppearance
	{
		private final BlockStateWrapper blockStateWrapper;
		/** indexed by {@link EDhDirection#faceIndex}, null faces use the block state's normal texture */
		private final IIcon[][] iconsByFaceIndex;
		/**
		 * the sides of each row counted down from the top block, then indexed like {@link #iconsByFaceIndex}.
		 * Null if the LOD isn't a wall of this block
		 */
		@Nullable
		private final IIcon[][][] sideIconsByRow;
		private final int hash;

		private PositionedAppearance(BlockStateWrapper blockStateWrapper, IIcon[][] iconsByFaceIndex, @Nullable IIcon[][][] sideIconsByRow)
		{
			this.blockStateWrapper = blockStateWrapper;
			this.iconsByFaceIndex = iconsByFaceIndex;
			this.sideIconsByRow = sideIconsByRow;
			this.hash = 31 * (31 * blockStateWrapper.hashCode() + Arrays.deepHashCode(iconsByFaceIndex)) + Arrays.deepHashCode(sideIconsByRow);
		}

		@Override
		public int hashCode() { return this.hash; }

		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof PositionedAppearance))
			{
				return false;
			}
			PositionedAppearance other = (PositionedAppearance) obj;
			// icons don't override equals, so they're compared by identity
			return this.hash == other.hash
				&& this.blockStateWrapper.equals(other.blockStateWrapper)
				&& Arrays.deepEquals(this.iconsByFaceIndex, other.iconsByFaceIndex)
				&& Arrays.deepEquals(this.sideIconsByRow, other.sideIconsByRow);
		}
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
