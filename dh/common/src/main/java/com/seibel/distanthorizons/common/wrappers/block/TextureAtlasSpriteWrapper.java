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

import com.seibel.distanthorizons.common.backports.IBlockState;
import com.seibel.distanthorizons.common.wrappers.interfaces.IMixinTextureAtlasSprite;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IGregTechCommonAccessor;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IconFlipped;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;

/**
 * For wrapping/utilizing around TextureAtlasSprite
 *
 * @author Ran
 */
public class TextureAtlasSpriteWrapper
{
	private static final IGregTechCommonAccessor GREG_TECH_ACCESSOR = ModAccessorInjector.INSTANCE.get(IGregTechCommonAccessor.class);

	/** meta bit MC sets on the top half of double plants */
	private static final int DOUBLE_PLANT_TOP_BIT = 8;
	/** the order of BlockDoublePlant's types (its meta), used in its icon names */
	private static final String[] DOUBLE_PLANT_NAMES = { "sunflower", "syringa", "grass", "fern", "rose", "paeonia" };
	private static final int TALL_GRASS_TYPE = 2;
	
	/** @param topMeta 8 for an untyped top, otherwise 8 + the bottom's meta */
	public static int getDoublePlantTopType(int topMeta)
	{
		int type = topMeta & 7;
		// sunflower tops (8 + 0) can't be told apart from untyped tops
		return (type == 0 || type >= DOUBLE_PLANT_NAMES.length) ? TALL_GRASS_TYPE : type;
	}

	/**
	 * Anisotropic sprites are stored with an eight-pixel wrapping border on
	 * every edge. The border is atlas padding, not part of the block texture.
	 */
	private static final int ANISOTROPIC_SPRITE_BORDER = 8;



	public static int getPixelARGB(TextureAtlasSprite sprite, int frameIndex, int x, int y)
	{
		// The sprite's pixel array isn't publicly accessible, so we rely on a Mixin
		// (see forge17/.../MixinTextureAtlasSprite) which caches the base mipmap level.
		IMixinTextureAtlasSprite spriteExt = (IMixinTextureAtlasSprite) sprite;
		int[][] frames = spriteExt.distanthorizons$getSpriteFrames();
		int[] spriteData = (frames != null && frameIndex < frames.length) ? frames[frameIndex] : null;
		if (spriteData == null)
		{
			// missing texture sentinel (matches the pink "missing" color used elsewhere)
			return ColorUtil.HOT_PINK;
		}
		if (sprite.useAnisotropicFiltering)
		{
			x += ANISOTROPIC_SPRITE_BORDER;
			y += ANISOTROPIC_SPRITE_BORDER;
		}

		return spriteData[sprite.getIconWidth() * y + x];
	}



	/** at least 1 */
	public static int getFrameCount(TextureAtlasSprite sprite)
	{
		int[][] frames = ((IMixinTextureAtlasSprite) sprite).distanthorizons$getSpriteFrames();
		return (frames != null && frames.length > 0) ? frames.length : 1;
	}

	public static int getWidth(TextureAtlasSprite texture)
	{
		return texture.getIconWidth() - (texture.useAnisotropicFiltering ? ANISOTROPIC_SPRITE_BORDER * 2 : 0);
	}
	public static int getHeight(TextureAtlasSprite texture)
	{
		return texture.getIconHeight() - (texture.useAnisotropicFiltering ? ANISOTROPIC_SPRITE_BORDER * 2 : 0);
	}

	public static float getMinU(TextureAtlasSprite sprite)
	{
		return sprite.getMinU();
	}
	public static float getMaxU(TextureAtlasSprite sprite)
	{
		return sprite.getMaxU();
	}

	public static float getMinV(TextureAtlasSprite sprite)
	{
		return sprite.getMinV();
	}
	public static float getMaxV(TextureAtlasSprite sprite)
	{
		return sprite.getMaxV();
	}



	/**
	 * Resolves the {@link TextureAtlasSprite} for the given block face. <br>
	 * 1.7.10 predates the baked model system, so there are no quads to rasterize;
	 * textures are fetched directly via {@link IIcon} using the same mod-compat
	 * handling {@link ClientBlockStateColorCache} uses
	 * (GregTech, {@link IconFlipped}, TwilightForest, IC2).
	 *
	 * @param sideOrdinal the {@link net.minecraftforge.common.util.ForgeDirection}/vanilla side
	 *                    index passed to {@link Block#getIcon(int, int)}
	 * @return the resolved sprite, or null if none could be found
	 */
	@Nullable
	public static TextureAtlasSprite resolveFaceSprite(IBlockState blockstate, int sideOrdinal)
	{
		// The top half of a double plant only knows it's a top, the type is stored in the bottom half
		// and without the world getIcon() returns the sunflower's bottom.
		// LODs give tops their type as 8 + the bottom's meta (see BlockStateWrapper.getPlantTopWithBottom),
		// a plain top (meta 8) is assumed to be tall grass, the most common one.
		if (blockstate.getBlock() instanceof BlockDoublePlant
			&& (blockstate.getMeta() & DOUBLE_PLANT_TOP_BIT) != 0)
		{
			int type = getDoublePlantTopType(blockstate.getMeta());
			TextureAtlasSprite topSprite = findBlockAtlasSprite("double_plant_" + DOUBLE_PLANT_NAMES[type] + "_top");
			if (topSprite != null)
			{
				return topSprite;
			}
		}

		IIcon icon = null;

		// GregTech
		if (GREG_TECH_ACCESSOR != null)
		{
			// GregTech icons are resolved per block/meta, not per face
			icon = GREG_TECH_ACCESSOR.resolveIcon(blockstate);
		}
		if (icon == null)
		{
			icon = blockstate.getBlock().getIcon(sideOrdinal, blockstate.getMeta());
		}

		if (icon instanceof IconFlipped)
		{
			icon = ((IconFlipped) icon).baseIcon;
		}

		// twilight forest
		if (icon != null
			&& icon.getClass().getName().equals("twilightforest.block.GiantBlockIcon"))
		{
			icon = unwrapIcon(icon, "baseIcon");
		}
		// Industrial Craft 2
		if (icon != null
			&& icon.getClass().getName().equals("ic2.core.block.BlockTextureStitched"))
		{
			icon = unwrapIcon(icon, "mappedTexture");
		}

		// other wrapped or copied icons (IE AE2's FlippableIcon, MalisisDoors' ClippedIcon) don't have pixel data,
		// the atlas sprite registered under the same name does
		if (icon != null && !hasSpriteData(icon))
		{
			ITextureObject blockAtlas = Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture);
			if (blockAtlas instanceof TextureMap)
			{
				// unknown names return the missing sprite, which has no sprite data
				TextureAtlasSprite atlasSprite = ((TextureMap) blockAtlas).getAtlasSprite(icon.getIconName());
				if (hasSpriteData(atlasSprite))
				{
					icon = atlasSprite;
				}
			}
		}

		return (icon instanceof TextureAtlasSprite) ? (TextureAtlasSprite) icon : null;
	}

	/** @return the block atlas sprite with this name, or null if there isn't one with pixel data */
	@Nullable
	public static TextureAtlasSprite findBlockAtlasSprite(String iconName)
	{
		ITextureObject blockAtlas = Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture);
		if (!(blockAtlas instanceof TextureMap))
		{
			return null;
		}

		// unknown names return the missing sprite, which has no sprite data
		TextureAtlasSprite atlasSprite = ((TextureMap) blockAtlas).getAtlasSprite(iconName);
		return hasSpriteData(atlasSprite) ? atlasSprite : null;
	}

	private static boolean hasSpriteData(@Nullable IIcon icon)
	{
		return icon instanceof TextureAtlasSprite
			&& ((IMixinTextureAtlasSprite) icon).distanthorizons$getSpriteData() != null;
	}

	/**
	 * Some mods wrap their real atlas sprite inside an {@link IIcon} field
	 * (IE TwilightForest's GiantBlockIcon, IC2's BlockTextureStitched). <br>
	 * This returns the icon stored in the named field, or the original {@code icon}
	 * unchanged if the field is missing, inaccessible, or null.
	 */
	private static IIcon unwrapIcon(IIcon icon, String fieldName)
	{
		try
		{
			Field field = icon.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			IIcon innerIcon = (IIcon) field.get(icon);
			return innerIcon != null ? innerIcon : icon;
		}
		catch (NoSuchFieldException | IllegalAccessException e)
		{
			return icon;
		}
	}



}
