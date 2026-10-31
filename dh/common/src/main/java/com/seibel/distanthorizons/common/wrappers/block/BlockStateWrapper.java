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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockStateWrapperCreatedEvent;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IRpleCommonAccessor;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import cpw.mods.fml.common.registry.GameData;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemDye;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A 1.7.10 block + metadata pair. <br>
 * Wrappers are pooled, so each block/meta has one instance (unless the API overrode it while it was created).
 */
public class BlockStateWrapper implements IBlockStateWrapper
{
	/** example "minecraft:water" */
	public static final String RESOURCE_LOCATION_SEPARATOR = ":";
	/** example "minecraft:water_STATE_{level:0}" */
	public static final String STATE_STRING_SEPARATOR = "_STATE_";

	// must be defined before AIR, otherwise a null pointer will be thrown
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	public static final ConcurrentHashMap<Integer, BlockStateWrapper> WRAPPER_BY_BLOCK_ID_AND_META = new ConcurrentHashMap<>();
	/**
	 * Flat lookup for vanilla-range block IDs (0-4095) and metas (0-15),
	 * avoids boxing and hashing on the per-block hot path.
	 * IDs outside that range (IE from extended ID mods) fall back to {@link #WRAPPER_BY_BLOCK_ID_AND_META}.
	 */
	private static final int FAST_CACHE_MAX_BLOCK_ID = 4096;
	private static final AtomicReferenceArray<BlockStateWrapper> WRAPPER_BY_SMALL_BLOCK_ID_AND_META = new AtomicReferenceArray<>(FAST_CACHE_MAX_BLOCK_ID * 16);
	public static final ConcurrentHashMap<String, BlockStateWrapper> WRAPPER_BY_RESOURCE_LOCATION = new ConcurrentHashMap<>();

	private static final String WATER_RESOURCE_LOCATION_STRING = "minecraft:water";
	private static BlockStateWrapper waterBlock = null;
	private static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";
	private static BlockStateWrapper dirtBlock = null;

	private static ObjectOpenHashSet<IBlockStateWrapper> rendererIgnoredBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> rendererIgnoredCaveBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> waterSubsurfaceReplacementBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> waterSurfaceReplacementBlocks = null;

	/** MC has no block tags, so a static list is used (netherite comes from mods like Et Futurum) */
	private static final String[] BEACON_BASE_BLOCK_NAMES = {"iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block"};
	/** block names since MC 1.13 to their 1.7.10 names */
	private static final Map<String, String> MODERN_BLOCK_NAME_ALIASES = Collections.singletonMap("minecraft:lily_pad", "minecraft:waterlily");

	private static final int CROSSED_SQUARES_RENDER_TYPE = 1;
	private static final int TORCH_RENDER_TYPE = 2;
	private static final int FIRE_RENDER_TYPE = 3;
	private static final int DOUBLE_PLANT_RENDER_TYPE = 40;
	/** vanilla's render types end at 41, mod renderers get the ids after it (see RenderingRegistry.getNextAvailableRenderId) */
	private static final int FIRST_MOD_RENDER_TYPE = 42;
	private static final int DOUBLE_PLANT_TOP_META = 8;

	public static final String AIR_STRING = "AIR";
	/** must be defined after every static the constructor reads (IE BEACON_BASE_BLOCK_NAMES), otherwise they're still null */
	public static final BlockStateWrapper AIR = new BlockStateWrapper(null, 0, null);

	// properties //

	/** null for air */
	@Nullable
	public final Block block;
	/** kinda like the state on MC 1.16+ blocks, but stored as an int */
	public final int meta;
	/** cache this to prevent re-allocating the array each time the wrapped objects are needed */
	private final Object[] wrappedMcObject;
	private final String serialString;
	private final int hashCode;
	/** Should be between {@link LodUtil#BLOCK_FULLY_OPAQUE} and {@link LodUtil#BLOCK_FULLY_OPAQUE} */
	private final int opacity;
	/** MC's light opacity, clamped to [0, 15] */
	private final int lightOpacity;
	/** used by the Iris shader mod to determine how each LOD should be rendered */
	private final byte blockMaterialId;
	/** {@link RenderDataPointUtil#SHAPE_NONE} etc. */
	private final byte lodShape;

	private final boolean isBeaconBlock;
	private final boolean isBeaconBaseBlock;
	private final boolean isIceBlock;
	private final boolean allowsBeaconBeamPassage;
	private final boolean renderTexture;
	private final boolean isSolid;
	private final boolean isLiquid;
	private final boolean allowApiColorOverride;
	private final boolean allowApiTextureOverride;
	/** null if this block can't tint beacons */
	private final Color beaconTintColor;

	//==============//
	// constructors //
	//==============//
	//region

	/**
	 * Can be faster than {@link #fromBlockState(Block, int)}
	 * in cases where the same block state is expected to be referenced multiple times.
	 */
	public static BlockStateWrapper fromBlockState(Block block, int meta, IBlockStateWrapper guess)
	{
		if (guess == null)
		{
			return fromBlockState(block, meta);
		}

		BlockStateWrapper wrapperGuess = (BlockStateWrapper) guess;
		if (block == null || block == Blocks.air)
		{
			return wrapperGuess.isAir() ? wrapperGuess : AIR;
		}
		else if (wrapperGuess.block == block && wrapperGuess.meta == meta)
		{
			return wrapperGuess;
		}
		return fromBlockState(block, meta);
	}

	public static BlockStateWrapper fromBlockState(Block block, int meta)
	{
		// air is a special case
		if (block == null || block == Blocks.air)
		{
			return AIR;
		}

		// pooling wrappers significantly improves chunk->LOD processing speed
		// and also reduces GC pressure
		final int blockId = Block.getIdFromBlock(block);
		final int fastCacheIndex = getFastCacheIndex(blockId, meta);
		BlockStateWrapper existingWrapper;
		if (fastCacheIndex != -1)
		{
			existingWrapper = WRAPPER_BY_SMALL_BLOCK_ID_AND_META.get(fastCacheIndex);
			if (existingWrapper != null)
			{
				return existingWrapper;
			}
		}

		final Integer packedIdMeta = packIdAndMeta(blockId, meta);
		existingWrapper = WRAPPER_BY_BLOCK_ID_AND_META.get(packedIdMeta);
		if (existingWrapper != null)
		{
			return existingWrapper;
		}

		// synchronized so the API event only fires once per block
		synchronized (WRAPPER_BY_BLOCK_ID_AND_META)
		{
			// if another thread already finished this block, use that wrapper
			existingWrapper = WRAPPER_BY_BLOCK_ID_AND_META.get(packedIdMeta);
			if (existingWrapper != null)
			{
				return existingWrapper;
			}

			// create a wrapper specifically for the API event to use
			BlockStateWrapper newWrapper = new BlockStateWrapper(block, meta, null);
			DhApiBlockStateWrapperCreatedEvent.EventParam eventParam = new DhApiBlockStateWrapperCreatedEvent.EventParam(newWrapper);
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBlockStateWrapperCreatedEvent.class, eventParam);
			if (eventParam.getOverridesSet())
			{
				// create a new wrapper using whatever overrides the API user set
				newWrapper = new BlockStateWrapper(block, meta, eventParam);
			}

			WRAPPER_BY_BLOCK_ID_AND_META.put(packedIdMeta, newWrapper);
			if (fastCacheIndex != -1)
			{
				WRAPPER_BY_SMALL_BLOCK_ID_AND_META.set(fastCacheIndex, newWrapper);
			}
			return newWrapper;
		}
	}

	/** @return -1 if the ID/meta is outside the range of {@link #WRAPPER_BY_SMALL_BLOCK_ID_AND_META} */
	private static int getFastCacheIndex(int blockId, int meta)
	{
		if (blockId < 0 || blockId >= FAST_CACHE_MAX_BLOCK_ID
			|| meta < 0 || meta >= 16)
		{
			return -1;
		}
		return (blockId << 4) | meta;
	}

	private static int packIdAndMeta(int blockId, int meta) { return (blockId << 16) + meta; }

	private BlockStateWrapper(@Nullable Block block, int meta, @Nullable DhApiBlockStateWrapperCreatedEvent.EventParam apiOverrides)
	{
		this.block = block;
		this.meta = meta;
		boolean isAir = (block == null || block == Blocks.air);
		this.wrappedMcObject = isAir ? null : new Object[]{block, meta};
		this.serialString = (block == null) ? AIR_STRING : serialize(block, meta);
		this.hashCode = this.serialString.hashCode();
		String lowerCaseSerial = this.serialString.toLowerCase();

		this.isLiquid = !isAir && (block.getMaterial().isLiquid() || block instanceof IFluidBlock);
		// snow layers use the non-solid snow material, so thick snow (4+ layers) would be skipped like grass.
		// 1-3 layers are still flattened onto the block below by the LOD builder
		this.isSolid = !isAir && (block.getMaterial().isSolid() || block == Blocks.snow_layer);
		this.isIceBlock = lowerCaseSerial.contains("minecraft:ice");
		this.lodShape = isAir ? RenderDataPointUtil.SHAPE_NONE : calculateLodShape(block, meta);
		this.renderTexture = !blockSerialInCsv(lowerCaseSerial, Config.Client.Advanced.Graphics.Texture.blocksDontRenderTextureCsv.get());

		// API overrides
		boolean hasApiOpacity = (apiOverrides != null && apiOverrides.getOpacity() != null);
		this.blockMaterialId = (apiOverrides != null && apiOverrides.getBlockMaterial() != null)
			? apiOverrides.getBlockMaterial().index
			: calculateBlockMaterial(isAir ? null : block, meta, lowerCaseSerial, this.isLiquid).index;
		this.opacity = hasApiOpacity ? apiOverrides.getOpacity() : calculateOpacity(isAir ? null : block, this.isLiquid);
		// an opacity override from the API is used for lighting too
		// IE leaves are 1, so light under a tree is only a little darker like in MC
		this.lightOpacity = hasApiOpacity ? this.opacity
			: isAir ? LodUtil.BLOCK_FULLY_TRANSPARENT
			: Math.max(LodUtil.BLOCK_FULLY_TRANSPARENT, Math.min(block.getLightOpacity(), LodUtil.BLOCK_FULLY_OPAQUE));
		this.allowApiColorOverride = (apiOverrides != null && apiOverrides.getAllowApiColorOverride() != null) && apiOverrides.getAllowApiColorOverride();
		this.allowApiTextureOverride = (apiOverrides != null && apiOverrides.getAllowApiTextureOverride() != null) && apiOverrides.getAllowApiTextureOverride();

		// beacons
		boolean isBeaconBaseBlock = false;
		for (String baseBlockName : BEACON_BASE_BLOCK_NAMES)
		{
			if (lowerCaseSerial.contains(baseBlockName))
			{
				isBeaconBaseBlock = true;
				break;
			}
		}
		this.isBeaconBaseBlock = isBeaconBaseBlock;
		this.isBeaconBlock = lowerCaseSerial.contains("minecraft:beacon");
		// beacon blocks are stained glass too in some mods, but since they block the beam we don't want their color
		this.beaconTintColor = (!this.isBeaconBlock && (block instanceof BlockStainedGlass || block instanceof BlockStainedGlassPane))
			? ColorUtil.toColorObjRGB(ItemDye.dyeColors[BlockColored.func_150032_b(meta)])
			: null;
		this.allowsBeaconBeamPassage = calculateAllowsBeaconBeamPassage(block, lowerCaseSerial, this.opacity);
	}

	//endregion

	//=====================//
	// property calculation //
	//=====================//
	//region

	/** @param block null for air */
	private static EDhApiBlockMaterial calculateBlockMaterial(@Nullable Block block, int meta, String lowerCaseSerial, boolean isLiquid)
	{
		if (block == null)
		{
			return EDhApiBlockMaterial.AIR;
		}
		else if (block instanceof BlockLeavesBase
			|| lowerCaseSerial.contains("bamboo")
			|| lowerCaseSerial.contains("cactus")
			|| lowerCaseSerial.contains("chorus_flower")
			|| lowerCaseSerial.contains("mushroom"))
		{
			return EDhApiBlockMaterial.LEAVES;
		}
		else if (block == Blocks.lava || block == Blocks.flowing_lava)
		{
			return EDhApiBlockMaterial.LAVA;
		}
		else if (isLiquid || block == Blocks.water || block == Blocks.flowing_water)
		{
			return EDhApiBlockMaterial.WATER;
		}
		// checked before the step sounds and names, otherwise torches and jack o'lanterns (wood sound)
		// or glowstone (stone in its name) wouldn't glow with shader packs
		else if (getLightEmission(block, meta) > 0)
		{
			return EDhApiBlockMaterial.ILLUMINATED;
		}
		else if (block.stepSound == Block.soundTypeWood || lowerCaseSerial.contains("root"))
		{
			return EDhApiBlockMaterial.WOOD;
		}
		else if (block.stepSound == Block.soundTypeMetal)
		{
			return EDhApiBlockMaterial.METAL;
		}
		else if (block instanceof BlockGrass || lowerCaseSerial.contains("grass_slab"))
		{
			return EDhApiBlockMaterial.GRASS;
		}
		else if (lowerCaseSerial.contains("dirt")
			|| lowerCaseSerial.contains("gravel")
			|| lowerCaseSerial.contains("mud")
			|| lowerCaseSerial.contains("podzol")
			|| lowerCaseSerial.contains("mycelium"))
		{
			return EDhApiBlockMaterial.DIRT;
		}
		else if (block == Blocks.netherrack || block == Blocks.nether_brick)
		{
			return EDhApiBlockMaterial.NETHER_STONE;
		}
		else if (lowerCaseSerial.contains("snow"))
		{
			return EDhApiBlockMaterial.SNOW;
		}
		else if (lowerCaseSerial.contains("sand"))
		{
			return EDhApiBlockMaterial.SAND;
		}
		else if (lowerCaseSerial.contains("terracotta"))
		{
			return EDhApiBlockMaterial.TERRACOTTA;
		}
		else if (lowerCaseSerial.contains("stone") || lowerCaseSerial.contains("ore"))
		{
			return EDhApiBlockMaterial.STONE;
		}
		return EDhApiBlockMaterial.UNKNOWN;
	}

	/** @param block null for air */
	private static int calculateOpacity(@Nullable Block block, boolean isLiquid)
	{
		// this method isn't perfect, but works well enough for our use case
		if (block == null)
		{
			return LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		else if (isLiquid && !canOcclude(block))
		{
			// +1 to indicate that the block is translucent (in between transparent and opaque)
			return LodUtil.BLOCK_FULLY_TRANSPARENT + 1;
		}
		else if (propagatesSkyLightDown(block) && !canOcclude(block))
		{
			// probably glass or some other fully transparent block.
			// !canOcclude is required to ignore stairs and slabs since
			// propagateSkyLightDown is true for them, but they're solid and don't actually let light through
			return LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		return LodUtil.BLOCK_FULLY_OPAQUE;
	}

	private static byte calculateLodShape(Block block, int meta)
	{
		if (block instanceof BlockFence)
		{
			return RenderDataPointUtil.SHAPE_THIN_POST;
		}
		else if (block instanceof BlockWall)
		{
			return RenderDataPointUtil.SHAPE_THICK_POST;
		}
		else if (block instanceof BlockSlab)
		{
			// double slabs are opaque cubes, the 8 bit is set for top slabs
			boolean bottomSlab = !block.isOpaqueCube() && (meta & 8) == 0;
			return bottomSlab ? RenderDataPointUtil.SHAPE_BOTTOM_HALF : RenderDataPointUtil.SHAPE_NONE;
		}
		else if (block == Blocks.snow_layer)
		{
			// the meta is the layer count minus one.
			// 1-3 layers are flattened onto the block below by the LOD builder, 6-8 are closer to a full block
			int layers = (meta & 7) + 1;
			return (layers == 4 || layers == 5) ? RenderDataPointUtil.SHAPE_BOTTOM_HALF : RenderDataPointUtil.SHAPE_NONE;
		}
		return RenderDataPointUtil.SHAPE_NONE;
	}

	/** @param block null for air */
	private static boolean calculateAllowsBeaconBeamPassage(@Nullable Block block, String lowerCaseSerial, int opacity)
	{
		if (block == null
			// bedrock is a special case fully opaque block that does allow beacons through
			|| lowerCaseSerial.contains("minecraft:bedrock"))
		{
			return true;
		}
		else if (lowerCaseSerial.contains("minecraft:tinted_glass"))
		{
			// tinted glass is a special case where it isn't fully opaque, but should block beacons
			return false;
		}
		else if (propagatesSkyLightDown(block) || !canOcclude(block))
		{
			// stairs, cake, fences, etc.
			return true;
		}
		// non-opaque blocks (glass, mob spawners, etc.) all allow beacons through
		return opacity != LodUtil.BLOCK_FULLY_OPAQUE;
	}

	/** material.isSolid() is true for glass, fences and other blocks light and vision can pass through */
	private static boolean canOcclude(Block block) { return block.isOpaqueCube(); }

	private static boolean propagatesSkyLightDown(Block block) { return block.getLightOpacity() == 0; }

	private static boolean blockSerialInCsv(String lowerCaseSerial, String blockNameCsv)
	{
		// duplicates could happen, but that isn't a problem since we'd just end up checking the same block twice
		for (String blockName : blockNameCsv.toLowerCase().split(","))
		{
			if (!blockName.trim().isEmpty()
				&& lowerCaseSerial.contains(blockName))
			{
				return true;
			}
		}
		return false;
	}

	public static int getLightEmission(Block block, int meta)
	{
		IRpleCommonAccessor rpleAccessor = RpleAccessorHolder.RPLE_ACCESSOR;
		if (rpleAccessor != null)
		{
			return rpleAccessor.getColor(block, meta);
		}
		return Math.min(block.getLightValue(), LodUtil.MAX_MC_LIGHT);
	}
	/** looked up when first needed, after the mod accessors are registered */
	private static final class RpleAccessorHolder
	{
		static final IRpleCommonAccessor RPLE_ACCESSOR = ModAccessorInjector.INSTANCE.get(IRpleCommonAccessor.class);
	}

	//endregion

	//====================//
	// LodBuilder methods //
	//====================//
	//region

	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredBlocks()
	{
		if (rendererIgnoredBlocks == null)
		{
			rendererIgnoredBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderBlockCsv, AIR_STRING);
		}
		return rendererIgnoredBlocks;
	}

	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks()
	{
		if (rendererIgnoredCaveBlocks == null)
		{
			rendererIgnoredCaveBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderCaveBlockCsv, AIR_STRING);
		}
		return rendererIgnoredCaveBlocks;
	}

	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSurfaceReplacementBlocks()
	{
		if (waterSurfaceReplacementBlocks == null)
		{
			ObjectOpenHashSet<IBlockStateWrapper> blocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSurfaceBlockReplacementCsv, null);
			blocks.remove(AIR);
			waterSurfaceReplacementBlocks = blocks;
		}
		return waterSurfaceReplacementBlocks;
	}

	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSubsurfaceReplacementBlocks()
	{
		if (waterSubsurfaceReplacementBlocks == null)
		{
			ObjectOpenHashSet<IBlockStateWrapper> blocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSubSurfaceBlockReplacementCsv, null);
			// air will be present if any invalid resource locations are present,
			// but we don't want to replace air with water, that'll cause monoliths
			blocks.remove(AIR);
			waterSubsurfaceReplacementBlocks = blocks;
		}
		return waterSubsurfaceReplacementBlocks;
	}

	public static BlockStateWrapper getWaterBlockStateWrapper()
	{
		if (waterBlock == null)
		{
			waterBlock = deserializeOrWarn(WATER_RESOURCE_LOCATION_STRING);
		}
		return waterBlock;
	}

	public static BlockStateWrapper getDirtBlockStateWrapper()
	{
		if (dirtBlock == null)
		{
			dirtBlock = deserializeOrWarn(DIRT_RESOURCE_LOCATION_STRING);
		}
		return dirtBlock;
	}

	private static BlockStateWrapper deserializeOrWarn(String resourceLocation)
	{
		BlockStateWrapper wrapper = deserializeOrDefault(resourceLocation);
		if (wrapper.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize [" + resourceLocation + "], issues may occur.");
		}
		return wrapper;
	}

	/** @param baseResourceLocation added to the config's blocks, can be null */
	private static ObjectOpenHashSet<IBlockStateWrapper> getAllBlockWrappers(ConfigEntry<String> config, @Nullable String baseResourceLocation)
	{
		ObjectOpenHashSet<String> resourceLocations = new ObjectOpenHashSet<>();
		if (baseResourceLocation != null)
		{
			resourceLocations.add(baseResourceLocation);
		}
		String configCsv = config.get();
		if (configCsv != null)
		{
			Collections.addAll(resourceLocations, configCsv.split(","));
		}

		// deserialize each of the given resource locations,
		// invalid ones become air
		ObjectOpenHashSet<IBlockStateWrapper> blockStateWrappers = new ObjectOpenHashSet<>();
		for (String resourceLocation : resourceLocations)
		{
			String cleanedResourceLocation = resourceLocation.trim();
			if (cleanedResourceLocation.isEmpty())
			{
				continue;
			}
			// configs can still have block names from newer MC versions (IE the old defaults)
			cleanedResourceLocation = MODERN_BLOCK_NAME_ALIASES.getOrDefault(cleanedResourceLocation, cleanedResourceLocation);

			try
			{
				blockStateWrappers.add(deserialize(cleanedResourceLocation));
			}
			catch (Exception e)
			{
				LOGGER.warn("Unable to deserialize block with the resource location: [" + resourceLocation + "]. Error: " + e.getMessage(), e);
			}
		}
		return blockStateWrappers;
	}

	public static void clearCachedIgnoreBlocks()
	{
		rendererIgnoredBlocks = null;
		rendererIgnoredCaveBlocks = null;
		waterSurfaceReplacementBlocks = null;
		waterSubsurfaceReplacementBlocks = null;

		waterBlock = null;
		dirtBlock = null;
	}

	//endregion

	//=================//
	// wrapper methods //
	//=================//
	//region

	/** MC's crossed squares (IE tall grass, flowers, saplings) and double plants */
	@Override
	public boolean isCrossPlant()
	{
		if (this.block == null)
		{
			return false;
		}
		int renderType = this.block.getRenderType();
		return renderType == CROSSED_SQUARES_RENDER_TYPE
			|| renderType == DOUBLE_PLANT_RENDER_TYPE
			// torches and fire are thin sprites too, as crosses they still show where the lights are at night.
			// Only when textured, untextured crosses fill their block and a torch would be a big brown X
			|| ((renderType == TORCH_RENDER_TYPE || renderType == FIRE_RENDER_TYPE)
			&& Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get())
			// mod plants with their own renderer, which almost always draws crossed squares too
			// (IE Biomes O' Plenty's grasses and flowers). Vanilla's other bushes (crops, lily pads)
			// have vanilla render types and aren't crosses
			|| (this.block instanceof BlockBush && renderType >= FIRST_MOD_RENDER_TYPE)
			// vines are flat on a wall, a LOD can't hold that but from a distance
			// crosses read as the same hanging green (IE swamp trees), skipped they'd be gone entirely
			|| this.block instanceof BlockVine;
	}

	/**
	 * 1.7.10 double plant tops are always meta 8, their type is the bottom's meta.
	 * The returned top's meta is 8 plus the type, which MC never stores
	 * but is understood by {@link TextureAtlasSpriteWrapper#resolveFaceSprite} and FakeWorld.
	 */
	@Override
	public IBlockStateWrapper getPlantTopWithBottom(IBlockStateWrapper blockBelow)
	{
		if (!(this.block instanceof BlockDoublePlant)
			|| this.meta != DOUBLE_PLANT_TOP_META
			|| !(blockBelow instanceof BlockStateWrapper))
		{
			return this;
		}

		BlockStateWrapper below = (BlockStateWrapper) blockBelow;
		if (below.block != this.block
			|| (below.meta & DOUBLE_PLANT_TOP_META) != 0)
		{
			return this;
		}
		return fromBlockState(this.block, DOUBLE_PLANT_TOP_META | below.meta);
	}

	@Override public int getOpacity() { return this.opacity; }
	@Override public byte getLodShape() { return this.lodShape; }
	@Override public int getLightOpacity() { return this.lightOpacity; }
	@Override public int getLightEmission() { return (this.block == null) ? 0 : getLightEmission(this.block, this.meta); }
	@Override public String getSerialString() { return this.serialString; }
	@Override public Object getWrappedMcObject() { return this.wrappedMcObject; }
	@Override public boolean isAir() { return this.block == null || this.block == Blocks.air; }
	@Override public boolean isSolid() { return this.isSolid; }
	@Override public boolean isLiquid() { return this.isLiquid; }
	@Override public boolean isBeaconBlock() { return this.isBeaconBlock; }
	@Override public boolean isBeaconBaseBlock() { return this.isBeaconBaseBlock; }
	@Override public boolean isIceBlock() { return this.isIceBlock; }
	@Override public boolean isBeaconTintBlock() { return this.beaconTintColor != null; }
	@Override public boolean allowsBeaconBeamPassage() { return this.allowsBeaconBeamPassage; }
	/** if true this block can have its color overridden by the API's block color override event */
	public boolean allowApiColorOverride() { return this.allowApiColorOverride; }
	/** if true this block can have its texture overridden by the API's block texture override event */
	public boolean allowApiTextureOverride() { return this.allowApiTextureOverride; }
	/**
	 * Some blocks don't pull their texture properly (like bamboo).
	 * In those cases it's best to just render their base color.
	 */
	public boolean renderTexture() { return this.renderTexture; }
	@Override public Color getBeaconTintColor() { return this.beaconTintColor; }
	@Override public byte getMaterialId() { return this.blockMaterialId; }

	//endregion

	//=======================//
	// serialization methods //
	//=======================//
	//region

	/** example: "minecraft:wool:14", the meta is left out when it's 0 */
	private static String serialize(Block block, int meta)
	{
		String serialString = GameData.getBlockRegistry().getNameForObject(block);
		return (meta != 0) ? serialString + RESOURCE_LOCATION_SEPARATOR + meta : serialString;
	}

	/** returns AIR if the given string can't be deserialized */
	public static BlockStateWrapper deserializeOrDefault(String resourceStateString)
	{
		try
		{
			return deserialize(resourceStateString);
		}
		catch (IOException e)
		{
			return AIR;
		}
	}

	/** will only work if a level is currently loaded */
	public static BlockStateWrapper deserialize(String resourceStateString) throws IOException
	{
		if (resourceStateString.equals(AIR_STRING)
			|| resourceStateString.isEmpty()) // the empty string shouldn't normally happen, but just in case
		{
			return AIR;
		}

		BlockStateWrapper existingWrapper = WRAPPER_BY_RESOURCE_LOCATION.get(resourceStateString);
		if (existingWrapper != null)
		{
			return existingWrapper;
		}

		// if no wrapper is found, default to air
		BlockStateWrapper foundWrapper = AIR;
		try
		{
			// "minecraft:wool:14" or the older "minecraft:wool_STATE_14"
			String blockName = resourceStateString;
			String metaString = null; // null if no meta was included
			int stateSeparatorIndex = blockName.indexOf(STATE_STRING_SEPARATOR);
			if (stateSeparatorIndex != -1)
			{
				metaString = blockName.substring(stateSeparatorIndex + STATE_STRING_SEPARATOR.length());
				blockName = blockName.substring(0, stateSeparatorIndex);
			}

			int namespaceSeparatorIndex = blockName.indexOf(RESOURCE_LOCATION_SEPARATOR);
			int metaSeparatorIndex = (namespaceSeparatorIndex != -1) ? blockName.indexOf(RESOURCE_LOCATION_SEPARATOR, namespaceSeparatorIndex + 1) : -1;
			if (metaSeparatorIndex != -1)
			{
				metaString = blockName.substring(metaSeparatorIndex + 1);
				blockName = blockName.substring(0, metaSeparatorIndex);
			}

			try
			{
				Block block = GameData.getBlockRegistry().getObject(blockName);
				int meta = (metaString != null) ? Integer.parseInt(metaString) : 0;
				foundWrapper = fromBlockState(block, meta);
				return foundWrapper;
			}
			catch (Exception e)
			{
				throw new IOException("Failed to deserialize the string [" + resourceStateString + "] into a BlockStateWrapper: " + e.getMessage(), e);
			}
		}
		finally
		{
			// put if absent in case two threads deserialize at the same time
			// unfortunately we can't put everything in a computeIfAbsent() since we also throw exceptions
			WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(resourceStateString, foundWrapper);
		}
	}

	//endregion

	//================//
	// base overrides //
	//================//
	//region

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (!(obj instanceof BlockStateWrapper))
		{
			return false;
		}

		// API overrides can create a second wrapper for the same block,
		// so the block is compared instead of the reference
		BlockStateWrapper that = (BlockStateWrapper) obj;
		return this.block == that.block
			&& this.meta == that.meta;
	}

	@Override
	public int hashCode() { return this.hashCode; }

	@Override
	public String toString() { return this.serialString; }

	//endregion
}
