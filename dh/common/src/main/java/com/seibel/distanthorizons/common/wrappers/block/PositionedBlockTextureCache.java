package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.common.backports.FakeWorld;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IChiselCommonAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateFaceTextureProvider;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.coreapi.util.TextureUtil;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateTextureCache.BAKE_LOCK;
import static com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateTextureCache.FACE_DIRECTIONS;
import static com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateTextureCache.LEAVES_GAP_COLOR;
import static com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateTextureCache.TEXTURE_WIDTH_AND_HEIGHT;

/**
 * Textures and colors of blocks that look different depending on where they are,
 * IE Chisel's connected textures depend on the neighbors and its variants on the position. <br>
 * Blocks that look the same everywhere use {@link ClientBlockStateTextureCache}.
 */
public class PositionedBlockTextureCache
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

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
	/**
	 * Mod accessors are bound during mod init, long before LODs are built,
	 * so the lookup (done for every LOD data point's color) is only done once.
	 */
	@Nullable
	private static IChiselCommonAccessor getChiselAccessor()
	{
		if (!chiselAccessorLookedUp)
		{
			chiselAccessor = ModAccessorInjector.INSTANCE.get(IChiselCommonAccessor.class);
			chiselAccessorLookedUp = true;
		}
		return chiselAccessor;
	}
	@Nullable
	private static volatile IChiselCommonAccessor chiselAccessor = null;
	private static volatile boolean chiselAccessorLookedUp = false;

	/** @param withSideRows false if only the LOD's color is needed */
	@Nullable
	private static PositionedAppearance getPositionedAppearance(
		BlockStateWrapper blockStateWrapper, IClientLevelWrapper levelWrapper, FullDataSourceV2 fullDataSource,
		int blockX, int topY, int blockZ, boolean withSideRows)
	{
		IChiselCommonAccessor chiselAccessor = getChiselAccessor();
		if (chiselAccessor == null
			|| blockStateWrapper.isAir()
			|| !chiselAccessor.hasPositionedTextures(blockStateWrapper.block)
			|| !blockStateWrapper.renderTexture()
			// API texture overrides are per block state
			|| blockStateWrapper.allowApiTextureOverride()
			// user color overrides are final colors
			|| BlockColorOverrides.get(blockStateWrapper.block, blockStateWrapper.meta) != null
			|| BROKEN_POSITIONED_BLOCK_STATES.contains(blockStateWrapper))
		{
			return null;
		}
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
			fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, topY, blockZ, blockStateWrapper);
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

				fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, blockY, blockZ, blockStateWrapper);
				iconsByFaceIndex[direction.faceIndex] = chiselAccessor.getFaceIcons(fakeWorld, blockStateWrapper.block, blockX, blockY, blockZ, McObjectConverter.convert(direction).ordinal());
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
					fakeWorld.updateWithNeighbors(realLevel, fullDataSource, levelWrapper.getMinHeight(), blockX, blockY, blockZ, blockStateWrapper);

					sideIconsByRow[row] = new IIcon[FACE_DIRECTIONS.length][];
					for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
					{
						sideIconsByRow[row][direction.faceIndex] = chiselAccessor.getFaceIcons(fakeWorld, blockStateWrapper.block, blockX, blockY, blockZ, McObjectConverter.convert(direction).ordinal());
					}
				}
			}
		}
		catch (Exception e)
		{
			if (BROKEN_POSITIONED_BLOCK_STATES.add(blockStateWrapper))
			{
				LOGGER.warn("Failed to get the positioned textures for block [" + blockStateWrapper.getSerialString() + "], error: [" + e.getMessage() + "], it will use the same texture everywhere.", e);
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
			return ClientBlockStateTextureCache.getFaceTexture(appearance.blockStateWrapper, direction);
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
				// Partly see-through pixels (IE stained-glass) are fully colored like MC's,
				// fully see-through ones are cut out of see-through LODs, leaves mark them as gaps instead
				boolean isLeaves = appearance.blockStateWrapper.block instanceof BlockLeavesBase;
				for (int i = 0; i < faceTexture.argbPixels.length; i++)
				{
					if (ColorUtil.getAlpha(faceTexture.argbPixels[i]) != 0)
					{
						faceTexture.argbPixels[i] = ColorUtil.setAlpha(faceTexture.argbPixels[i], 255);
					}
					else if (isLeaves)
					{
						faceTexture.argbPixels[i] = LEAVES_GAP_COLOR;
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
			? ClientBlockStateColorCache.calculateUntintedColor(appearance.blockStateWrapper)
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
	 * or four quarters (bottom left, bottom right, top right, top left) like Chisel's connected textures
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

	/** Should be called whenever MC's textures change, IE when resource packs are swapped. */
	public static void clearCache()
	{
		AVERAGE_COLOR_BY_APPEARANCE.clear();
		TEXTURED_COLOR_BY_APPEARANCE.clear();
		// the new textures may not throw
		BROKEN_POSITIONED_BLOCK_STATES.clear();
	}
}
