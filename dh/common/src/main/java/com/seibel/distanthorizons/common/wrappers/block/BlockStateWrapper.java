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
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemDye;
import cpw.mods.fml.common.registry.GameData;
import com.seibel.distanthorizons.common.backports.IBlockState;
import net.minecraftforge.fluids.IFluidBlock;
import com.seibel.distanthorizons.common.backports.FakeBlockState;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jetbrains.annotations.Nullable;



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


	// static blocks //
	//region

	public static final String AIR_STRING = "AIR";
	public static final BlockStateWrapper AIR = new BlockStateWrapper(null, null, null);

	private static final String WATER_RESOURCE_LOCATION_STRING = "minecraft:water";
	private static BlockStateWrapper waterBlock = null;

	private static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";
	private static BlockStateWrapper dirtBlock = null;

	private static final String ICE_RESOURCE_LOCATION_STRING = "minecraft:ice";
	private static BlockStateWrapper iceBlock = null;

	private static final String SNOW_RESOURCE_LOCATION_STRING = "minecraft:snow";
	private static BlockStateWrapper snowBlock = null;

	//endregion


	private static ObjectOpenHashSet<IBlockStateWrapper> rendererIgnoredBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> rendererIgnoredCaveBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> waterSubsurfaceReplacementBlocks = null;
	private static ObjectOpenHashSet<IBlockStateWrapper> waterSurfaceReplacementBlocks = null;



	// properties //

	// TODO why aren't we just storing the block/meta?
	/** cache this to prevent re-allocating the array each time the wrapped objects are needed */
	private final Object[] wrappedMcObject;

	@Nullable
	public final IBlockState blockState;
	/** technically final, but since it requires a method call to generate it can't be marked as such */
	private String serialString;
	private final int hashCode;
	/** Should be between {@link LodUtil#BLOCK_FULLY_OPAQUE} and {@link LodUtil#BLOCK_FULLY_OPAQUE} */
	private final int opacity;
	/** used by the Iris shader mod to determine how each LOD should be rendered */
	private byte blockMaterialId = 0;

	private final boolean isBeaconBlock;
	private final boolean isBeaconBaseBlock;
	private final boolean isIceBlock;
	private final boolean allowsBeaconBeamPassage;
	private final boolean renderTexture;
	private final boolean useBottomTextureForSides;
	private final boolean alwaysRasterizeTexture;
	private final boolean isSolid;
	private final boolean isLiquid;
	private final boolean allowApiColorOverride;
	private final boolean allowApiTextureOverride;
	/** null if this block can't tint beacons */
	private final Color beaconTintColor;
	private final Color mapColor;



	//==============//
	// constructors //
	//==============//
	//region


	public static BlockStateWrapper fromBlockState(IBlockState blockState, ILevelWrapper levelWrapper)
	{ return fromBlockState(blockState.getBlock(), blockState.getMeta(), levelWrapper); }

	/**
	 * Can be faster than BlockStateWrapper#fromBlockState(BlockState, ILevelWrapper)
	 * in cases where the same block state is expected to be referenced multiple times.
	 */
	public static BlockStateWrapper fromBlockState(Block block, int meta, ILevelWrapper levelWrapper, IBlockStateWrapper guess)
	{
		if (guess == null)
		{
			return fromBlockState(block, meta, levelWrapper);
		}


		// guess block state
		BlockStateWrapper wrapperGuess = (BlockStateWrapper) guess;
		// compare the block/meta directly so we don't have to allocate any FakeBlockStates
		if (block == null || block == Blocks.air)
		{
			return isAir(wrapperGuess.blockState) ? wrapperGuess : AIR;
		}
		else if (wrapperGuess.blockState != null
			&& wrapperGuess.blockState.getBlock() == block
			&& wrapperGuess.blockState.getMeta() == meta)
		{
			return wrapperGuess;
		}
		return fromBlockState(block, meta, levelWrapper);
	}

	public static BlockStateWrapper fromBlockState(Block block, int meta, ILevelWrapper levelWrapper)
	{
		// air is a special case
		if (block == null
			|| block == Blocks.air)
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

		final Integer packedIdMeta = FakeBlockState.packIdAndMeta(blockId, meta);
		existingWrapper = WRAPPER_BY_BLOCK_ID_AND_META.get(packedIdMeta);
		if (existingWrapper != null && fastCacheIndex != -1)
		{
			// can happen if the wrapper was created via deserialization
			WRAPPER_BY_SMALL_BLOCK_ID_AND_META.compareAndSet(fastCacheIndex, null, existingWrapper);
		}
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

			// only allocated when a new wrapper is needed
			FakeBlockState blockState = new FakeBlockState(block, meta, blockId);


			// create a wrapper specifically for the API event to use
			BlockStateWrapper apiWrapper = new BlockStateWrapper(blockState, levelWrapper, null);
			DhApiBlockStateWrapperCreatedEvent.EventParam eventParam = new DhApiBlockStateWrapperCreatedEvent.EventParam(apiWrapper);
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBlockStateWrapperCreatedEvent.class, eventParam);

			if (!eventParam.getOverridesSet())
			{
				// no API changes needed, use the existing object
				WRAPPER_BY_BLOCK_ID_AND_META.putIfAbsent(packedIdMeta, apiWrapper);
				if (fastCacheIndex != -1)
				{
					WRAPPER_BY_SMALL_BLOCK_ID_AND_META.compareAndSet(fastCacheIndex, null, apiWrapper);
				}
				return apiWrapper;
			}
			else
			{
				// create a new wrapper using whatever overrides the API user set
				BlockStateWrapper returnWrapper = new BlockStateWrapper(blockState, levelWrapper, eventParam);
				WRAPPER_BY_BLOCK_ID_AND_META.putIfAbsent(packedIdMeta, returnWrapper);
				if (fastCacheIndex != -1)
				{
					WRAPPER_BY_SMALL_BLOCK_ID_AND_META.compareAndSet(fastCacheIndex, null, returnWrapper);
				}
				return returnWrapper;
			}
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

	private BlockStateWrapper(@Nullable IBlockState blockState, ILevelWrapper levelWrapper, @Nullable DhApiBlockStateWrapperCreatedEvent.EventParam overrideEventParam)
	{
		this.blockState = blockState;
		if (blockState != null && !isAir()) {
			this.wrappedMcObject = new Object[] {
				blockState.getBlock(),
				blockState.getMeta()
			};
		} else {
			this.wrappedMcObject = null;
		}
		this.serialString = serialize(blockState, levelWrapper);
		this.hashCode = Objects.hash(this.serialString);
		String lowerCaseSerial = this.serialString.toLowerCase();



		// is liquid //
		{
			if (this.isAir()
				|| this.blockState == null) // == null isn't necessary since its handled in isAir() but is here to prevent intellij from complaining
			{
				this.isLiquid = false;
			}
			else
			{
				this.isLiquid = this.blockState.getMaterial().isLiquid() || this.blockState.getBlock() instanceof IFluidBlock;
			}
		}


		// API overriding //
		{
			if (overrideEventParam != null
				&& overrideEventParam.getBlockMaterial() != null)
			{
				this.blockMaterialId = overrideEventParam.getBlockMaterial().index;
			}
			else
			{
				// no API override, use the base logic
				this.blockMaterialId = calculateEDhApiBlockMaterialId(this.blockState, lowerCaseSerial, this.isLiquid).index;
			}

			// allow overriding if present
			if (overrideEventParam != null
				&& overrideEventParam.getOpacity() != null)
			{
				this.opacity = overrideEventParam.getOpacity();
			}
			else
			{
				this.opacity = calculateOpacity(this.blockState, isAir(this.blockState), this.isLiquid);
			}

			// allow overriding if present
			if (overrideEventParam != null
				&& overrideEventParam.getAllowApiColorOverride() != null)
			{
				this.allowApiColorOverride = overrideEventParam.getAllowApiColorOverride();
			}
			else
			{
				this.allowApiColorOverride = false;
			}

			// allow overriding if present
			if (overrideEventParam != null
				&& overrideEventParam.getAllowApiTextureOverride() != null)
			{
				this.allowApiTextureOverride = overrideEventParam.getAllowApiTextureOverride();
			}
			else
			{
				this.allowApiTextureOverride = false;
			}
		}


		// beacon handling //
		{
			// beacon base blocks

			// MC has no block tags, so a static list is used to determine beacon base blocks
			// (netherite comes from mods like Et Futurum)
			List<String> beaconBaseBlockNameList = Arrays.asList(
				"iron_block",
				"gold_block",
				"diamond_block",
				"emerald_block",
				"netherite_block"
			);

			boolean isBeaconBaseBlock = false;
			for (int i = 0; i < beaconBaseBlockNameList.size(); i++)
			{
				String baseBlockName = beaconBaseBlockNameList.get(i);
				if (lowerCaseSerial.contains(baseBlockName))
				{
					isBeaconBaseBlock = true;
					break;
				}
			}
			this.isBeaconBaseBlock = isBeaconBaseBlock;

			// beacon block
			this.isBeaconBlock = lowerCaseSerial.contains("minecraft:beacon");


			// beacon tint color
			Color beaconTintColor = null;
			if (this.blockState != null
				// beacon blocks also show up here, but since they block the beacon beam we don't want their color
				&& !this.isBeaconBlock)
			{
				int colorInt;
				Block block = this.blockState.getBlock();
				if (block instanceof BlockStainedGlass
					|| block instanceof BlockStainedGlassPane)
				{
					colorInt = ItemDye.dyeColors[BlockColored.func_150032_b(this.blockState.getMeta())];
					beaconTintColor = ColorUtil.toColorObjRGB(colorInt);
				}
			}
			this.beaconTintColor = beaconTintColor;


			// allow/deny beacon beam passage
			boolean allowsBeaconBeamPassage;
			if (this.blockState != null)
			{
				// get block properties (defaults to the values used by air)
				boolean canOcclude = getCanOcclude(this.blockState);
				boolean propagatesSkyLightDown = getPropagatesSkyLightDown(this.blockState);

				if (lowerCaseSerial.contains("minecraft:bedrock"))
				{
					// bedrock is a special case fully opaque block that does allow beacons through
					allowsBeaconBeamPassage = true;
				}
				else if (lowerCaseSerial.contains("minecraft:tinted_glass"))
				{
					// tinted glass is a special case where it isn't fully opaque,
					// but should block beacons
					allowsBeaconBeamPassage = false;
				}
				else if (propagatesSkyLightDown || !canOcclude)
				{
					// stairs, cake, fences, etc.
					allowsBeaconBeamPassage = true;
				}
				else
				{
					// non-opaque blocks (glass, mob spawners, etc.)
					// all allow beacons through
					allowsBeaconBeamPassage = (this.opacity != LodUtil.BLOCK_FULLY_OPAQUE);
				}
			}
			else
			{
				// air allows beacons through
				allowsBeaconBeamPassage = true;
			}
			this.allowsBeaconBeamPassage = allowsBeaconBeamPassage;
		}


		// texture handling //
		{
			// texture ignoring //
			{
				String dontTextureNamesCsv = Config.Client.Advanced.Graphics.Texture.blocksDontRenderTextureCsv.get();
				this.renderTexture = !blockSerialInCsv(lowerCaseSerial, dontTextureNamesCsv);
			}


			// side texture ignoring //
			{
				// block tags aren't available, so only the block name list is checked
				String sideBlockNamesCsv = Config.Client.Advanced.Graphics.Texture.blocksDontUseSideTextureCsv.get();
				this.useBottomTextureForSides = blockSerialInCsv(lowerCaseSerial, sideBlockNamesCsv);
			}


			// always raster texture //
			{
				String alwaysRasterNamesCsv = Config.Client.Advanced.Graphics.Texture.blocksAlwaysRasterizeTextureCsv.get();
				this.alwaysRasterizeTexture = blockSerialInCsv(lowerCaseSerial, alwaysRasterNamesCsv);
			}
		}


		// map color //
		{
			if (this.blockState != null)
			{
				int mcColor = this.blockState.getMaterial().getMaterialMapColor().colorValue;
				this.mapColor = ColorUtil.toColorObjRGB(mcColor);
			}
			else
			{
				this.mapColor = new Color(0, 0, 0, 0);
			}
		}


		// is solid //
		{
			if (this.isAir()
				|| this.blockState == null) // "== null" isn't necessary since its handled in isAir() but is here to prevent IntelliJ from complaining
			{
				this.isSolid = false;
			}
			else
			{
				this.isSolid = this.blockState.getMaterial().isSolid();
			}
		}


		// ice //
		{
			this.isIceBlock = lowerCaseSerial.contains("minecraft:ice");
		}

	}

	// static constructor helpers //
	//region

	private static EDhApiBlockMaterial calculateEDhApiBlockMaterialId(@Nullable IBlockState blockState, String lowercaseSerialString, boolean isLiquid)
	{
		if (isAir(blockState))
		{
			return EDhApiBlockMaterial.AIR;
		}



		//========//
		// leaves //
		//========//
		//region

		if (blockState.getBlock() instanceof BlockLeavesBase
			|| lowercaseSerialString.contains("bamboo")
			|| lowercaseSerialString.contains("cactus")
			|| lowercaseSerialString.contains("chorus_flower")
			|| lowercaseSerialString.contains("mushroom")
			)
		{
			return EDhApiBlockMaterial.LEAVES;
		}

		//endregion



		//======//
		// lava //
		//======//
		//region

		if (blockState.getBlock() == Blocks.lava
			|| blockState.getBlock() == Blocks.flowing_lava)
		{
			return EDhApiBlockMaterial.LAVA;
		}

		//endregion



		//=======//
		// water //
		//=======//
		//region

		boolean isWaterBlock = blockState.getBlock() == Blocks.water
			|| blockState.getBlock() == Blocks.flowing_water;
		if (isLiquid
			|| isWaterBlock)
		{
			return EDhApiBlockMaterial.WATER;
		}

		//endregion



		//======//
		// wood //
		//======//
		//region

		if (blockState.getBlock().stepSound == Block.soundTypeWood
			|| lowercaseSerialString.contains("root")
			)
		{
			return EDhApiBlockMaterial.WOOD;
		}

		//endregion



		//=======//
		// metal //
		//=======//
		//region

		if (blockState.getBlock().stepSound == Block.soundTypeMetal)
		{
			return EDhApiBlockMaterial.METAL;
		}

		//endregion



		//=======//
		// grass //
		//=======//
		//region

		if (blockState.getBlock() instanceof BlockGrass
			|| lowercaseSerialString.contains("grass_slab")
			)
		{
			return EDhApiBlockMaterial.GRASS;
		}

		//endregion



		//======//
		// dirt //
		//======//
		//region

		if (
			lowercaseSerialString.contains("dirt")
			|| lowercaseSerialString.contains("gravel")
			|| lowercaseSerialString.contains("mud")
			|| lowercaseSerialString.contains("podzol")
			|| lowercaseSerialString.contains("mycelium")
			)
		{
			return EDhApiBlockMaterial.DIRT;
		}

		//endregion



		//============//
		// netherrack //
		//============//
		//region

		if (blockState.getBlock() == Blocks.netherrack
			|| blockState.getBlock() == Blocks.nether_brick)
		{
			return EDhApiBlockMaterial.NETHER_STONE;
		}

		//endregion



		//=============//
		// misc/simple //
		//=============//
		//region

		if (lowercaseSerialString.contains("snow"))
		{
			return EDhApiBlockMaterial.SNOW;
		}

		if (lowercaseSerialString.contains("sand"))
		{
			return EDhApiBlockMaterial.SAND;
		}

		if (lowercaseSerialString.contains("terracotta"))
		{
			return EDhApiBlockMaterial.TERRACOTTA;
		}

		if (lowercaseSerialString.contains("stone")
			|| lowercaseSerialString.contains("ore"))
		{
			return EDhApiBlockMaterial.STONE;
		}

		if (getLightEmission(blockState) > 0)
		{
			return EDhApiBlockMaterial.ILLUMINATED;
		}

		//endregion



		return EDhApiBlockMaterial.UNKNOWN;
	}

	private static int calculateOpacity(@Nullable IBlockState blockState, boolean isAir, boolean isLiquid)
	{
		// get block properties (defaults to the values used by air)
		boolean canOcclude = getCanOcclude(blockState);
		boolean propagatesSkyLightDown = getPropagatesSkyLightDown(blockState);



		// this method isn't perfect, but works well enough for our use case
		int opacity;
		if (isAir)
		{
			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		else if (isLiquid && !canOcclude)
		{
			// +1 to indicate that the block is translucent (in between transparent and opaque)
			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT + 1;
		}
		else if (propagatesSkyLightDown && !canOcclude)
		{
			// probably glass or some other fully transparent block

			// !canOcclude is required to ignore stairs and slabs since
			// propagateSkyLightDown is true for them, but they're solid and don't actually let light through

			opacity = LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		else
		{
			// default for all other blocks
			opacity = LodUtil.BLOCK_FULLY_OPAQUE;
		}


		return opacity;
	}

	private static boolean getCanOcclude(@Nullable IBlockState blockState)
	{
		// defaults to the value used by air
		boolean canOcclude = false;
		if (blockState != null)
		{
			canOcclude = blockState.getMaterial().isSolid();
		}

		return canOcclude;
	}

	private static boolean getPropagatesSkyLightDown(@Nullable IBlockState blockState)
	{
		// defaults to the value used by air
		boolean propagatesSkyLightDown = true;
		if (blockState != null)
		{
			propagatesSkyLightDown = blockState.getBlock().getLightOpacity() == 0;
		}

		return propagatesSkyLightDown;
	}

	private static boolean blockSerialInCsv(String lowerCaseSerial, String blockNameCsv)
	{
		boolean blockMatches = false;

		// get block resource names
		blockNameCsv = blockNameCsv.toLowerCase(); // lowercase to allow for case-insensitive checking
		List<String> blockNameList = Arrays.asList(blockNameCsv.split(",")); // duplicates could happen, but that isn't a problem since we'd just end up checking the same block twice, not a big deal

		// check this block against the expected list
		for (int i = 0; i < blockNameList.size(); i++)
		{
			String baseBlockName = blockNameList.get(i);
			if (baseBlockName.trim().isEmpty())
			{
				continue;
			}

			if (lowerCaseSerial.contains(baseBlockName))
			{
				blockMatches = true;
				break;
			}
		}

		return blockMatches;
	}

	//endregion
	//endregion



	//====================//
	// LodBuilder methods //
	//====================//
	//region

	/**
	 * Each of the following methods require
	 * a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one.
	 * This way the method won't accidentally be called before the deserialization can be completed.
	 */

	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (rendererIgnoredBlocks != null)
		{
			return rendererIgnoredBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlock = new ObjectOpenHashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderBlockCsv, baseIgnoredBlock, levelWrapper);
		return rendererIgnoredBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (rendererIgnoredCaveBlocks != null)
		{
			return rendererIgnoredCaveBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlock = new ObjectOpenHashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredCaveBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderCaveBlockCsv, baseIgnoredBlock, levelWrapper);
		return rendererIgnoredCaveBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSurfaceReplacementBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (waterSurfaceReplacementBlocks != null)
		{
			return waterSurfaceReplacementBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlockResourceSet = new ObjectOpenHashSet<>();
		waterSurfaceReplacementBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSurfaceBlockReplacementCsv, baseIgnoredBlockResourceSet, levelWrapper);
		waterSurfaceReplacementBlocks.remove(AIR);

		return waterSurfaceReplacementBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSubsurfaceReplacementBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (waterSubsurfaceReplacementBlocks != null)
		{
			return waterSubsurfaceReplacementBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlockResourceSet = new ObjectOpenHashSet<>();
		waterSubsurfaceReplacementBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSubSurfaceBlockReplacementCsv, baseIgnoredBlockResourceSet, levelWrapper);
		// air will be present if any invalid resource locations are present
		// but we don't want to replace air with water, that'll cause monoliths
		waterSubsurfaceReplacementBlocks.remove(AIR);

		return waterSubsurfaceReplacementBlocks;
	}

	//endregion



	//======================//
	// static block getters //
	//======================//
	//region

	public static BlockStateWrapper getWaterBlockStateWrapper(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (waterBlock != null)
		{
			return waterBlock;
		}

		waterBlock = BlockStateWrapper.deserializeOrDefault(WATER_RESOURCE_LOCATION_STRING, levelWrapper);
		if (waterBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+WATER_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return waterBlock;
	}

	public static BlockStateWrapper getDirtBlockStateWrapper(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (dirtBlock != null)
		{
			return dirtBlock;
		}

		dirtBlock = BlockStateWrapper.deserializeOrDefault(DIRT_RESOURCE_LOCATION_STRING, levelWrapper);
		if (dirtBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+DIRT_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return dirtBlock;
	}

	public static BlockStateWrapper getIceBlockStateWrapper(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (iceBlock != null)
		{
			return iceBlock;
		}

		iceBlock = BlockStateWrapper.deserializeOrDefault(ICE_RESOURCE_LOCATION_STRING, levelWrapper);
		if (iceBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+ICE_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return iceBlock;
	}

	public static BlockStateWrapper getSnowBlockStateWrapper(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (snowBlock != null)
		{
			return snowBlock;
		}

		snowBlock = BlockStateWrapper.deserializeOrDefault(SNOW_RESOURCE_LOCATION_STRING, levelWrapper);
		if (snowBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+SNOW_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return snowBlock;
	}

	//endregion



	//=====================//
	// lod builder helpers //
	//=====================//
	//region

	private static ObjectOpenHashSet<IBlockStateWrapper> getAllBlockWrappers(ConfigEntry<String> config, ObjectOpenHashSet<String> baseResourceLocations, ILevelWrapper levelWrapper)
	{
		// get the base blocks
		ObjectOpenHashSet<String> blockStringList = new ObjectOpenHashSet<>();
		if (baseResourceLocations != null)
		{
			blockStringList.addAll(baseResourceLocations);
		}

		// get the config blocks
		String ignoreBlockCsv = config.get();
		if (ignoreBlockCsv != null)
		{
			blockStringList.addAll(Arrays.asList(ignoreBlockCsv.split(",")));
		}

		return getAllBlockWrappers(blockStringList, levelWrapper);
	}
	private static ObjectOpenHashSet<IBlockStateWrapper> getAllBlockWrappers(ObjectOpenHashSet<String> blockResourceLocationSet, ILevelWrapper levelWrapper)
	{
		// deserialize each of the given resource locations
		ObjectOpenHashSet<IBlockStateWrapper> blockStateWrappers = new ObjectOpenHashSet<>();
		for (String blockResourceLocation : blockResourceLocationSet)
		{
			try
			{
				if (blockResourceLocation == null)
				{
					// shouldn't happen, but just in case
					continue;
				}
				String cleanedResourceLocation = blockResourceLocation.trim();
				if (cleanedResourceLocation.length() == 0)
				{
					continue;
				}


				BlockStateWrapper defaultBlockStateToIgnore = (BlockStateWrapper) deserialize(cleanedResourceLocation, levelWrapper);
				blockStateWrappers.add(defaultBlockStateToIgnore);

				if (defaultBlockStateToIgnore != AIR)
				{
					BlockStateWrapper newBlockToIgnore = BlockStateWrapper.fromBlockState(defaultBlockStateToIgnore.blockState, levelWrapper);
					blockStateWrappers.add(newBlockToIgnore);
				}
				else
				{
					// air is a special case so it must be handled separately
					blockStateWrappers.add(AIR);
				}
			}
			catch (IOException e)
			{
				LOGGER.warn("Unable to deserialize block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
			}
			catch (Exception e)
			{
				LOGGER.warn("Unexpected error deserializing block with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
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
		iceBlock = null;
		snowBlock = null;
	}

	//endregion



	//=================//
	// wrapper methods //
	//=================//
	//region

	@Override
	public int getOpacity() { return this.opacity; }

	@Override
	public int getLightEmission() { return getLightEmission(this.blockState); }
	public static int getLightEmission(IBlockState blockState)
	{
		if (blockState == null)
		{
			return 0;
		}

		return blockState.getLightValue();
	}


	@Override
	public String getSerialString() { return this.serialString; }

	@Override
	public Object getWrappedMcObject()
	{
		return this.wrappedMcObject;
	}

	@Override
	public boolean isAir() { return isAir(this.blockState); }
	public static boolean isAir(IBlockState blockState)
	{
		if (blockState == null)
		{
			return true;
		}

		return blockState.getBlock() == Blocks.air;
	}

	@Override public boolean isSolid() { return this.isSolid; }
	@Override public boolean isLiquid() { return this.isLiquid; }
	@Override public boolean isBeaconBlock() { return this.isBeaconBlock; }
	@Override public boolean isBeaconBaseBlock() { return this.isBeaconBaseBlock; }
	@Override public boolean isIceBlock() { return this.isIceBlock; }
	@Override public boolean isBeaconTintBlock() { return this.beaconTintColor != null; }
	@Override public boolean allowsBeaconBeamPassage() { return this.allowsBeaconBeamPassage; }
	@Override public boolean allowApiColorOverride() { return this.allowApiColorOverride; }
	@Override public boolean allowApiTextureOverride() { return this.allowApiTextureOverride; }
	@Override public boolean renderTexture() { return this.renderTexture; }
	@Override public boolean useBottomTextureForSides() { return this.useBottomTextureForSides; }
	@Override public boolean alwaysRasterizeTexture() { return this.alwaysRasterizeTexture; }

	@Override public Color getMapColor() { return this.mapColor; }
	@Override public Color getBeaconTintColor() { return this.beaconTintColor; }

	@Override public byte getMaterialId() { return this.blockMaterialId; }

	//endregion



	//=======================//
	// serialization methods //
	//=======================//
	//region

	private static String serialize(IBlockState blockState, ILevelWrapper levelWrapper)
	{
		if (blockState == null)
		{
			return AIR_STRING;
		}

		String serialString = GameData.getBlockRegistry().getNameForObject(blockState.getBlock());
		if (blockState.getMeta() != 0)
		{
			serialString += RESOURCE_LOCATION_SEPARATOR + blockState.getMeta();
		}
		return serialString;
	}


	/** returns AIR if the given string can't be deserialized */
	public static BlockStateWrapper deserializeOrDefault(String resourceStateString, ILevelWrapper levelWrapper)
	{
		try
		{
			return deserialize(resourceStateString, levelWrapper);
		}
		catch (IOException e)
		{
			return BlockStateWrapper.AIR;
		}
	}

	/** will only work if a level is currently loaded */
	public static BlockStateWrapper deserialize(String resourceStateString, ILevelWrapper levelWrapper) throws IOException
	{
		// we need the final string for the concurrent hash map later
		final String finalResourceStateString = resourceStateString;

		if (finalResourceStateString.equals(AIR_STRING)
			|| finalResourceStateString.equals("")) // the empty string shouldn't normally happen, but just in case
		{
			return AIR;
		}

		// attempt to use the existing wrapper
		if (WRAPPER_BY_RESOURCE_LOCATION.containsKey(finalResourceStateString))
		{
			return WRAPPER_BY_RESOURCE_LOCATION.get(finalResourceStateString);
		}



		// if no wrapper is found, default to air
		BlockStateWrapper foundWrapper = AIR;
		try
		{
			String metaString = null; // will be null if no meta was included
			int stateSeparatorIndex = resourceStateString.indexOf(STATE_STRING_SEPARATOR);
			if (stateSeparatorIndex != -1)
			{
				metaString = resourceStateString.substring(stateSeparatorIndex + STATE_STRING_SEPARATOR.length());
				resourceStateString = resourceStateString.substring(0, stateSeparatorIndex);
			}

			int separatorOne = resourceStateString.indexOf(RESOURCE_LOCATION_SEPARATOR);
			if (separatorOne != -1)
			{
				stateSeparatorIndex = resourceStateString.indexOf(RESOURCE_LOCATION_SEPARATOR, separatorOne + 1);
				if (stateSeparatorIndex != -1)
				{
					metaString = resourceStateString.substring(stateSeparatorIndex + 1);
					resourceStateString = resourceStateString.substring(0, stateSeparatorIndex);
				}
			}

			try
			{
				Block block = GameData.getBlockRegistry().getObject(resourceStateString);
				int meta = 0;
				if (metaString != null)
				{
					meta = Integer.parseInt(metaString);
				}

				foundWrapper = fromBlockState(block, meta, levelWrapper);
				return foundWrapper;
			}
			catch (Exception e)
			{
				throw new IOException("Failed to deserialize the string [" + finalResourceStateString + "] into a BlockStateWrapper: " + e.getMessage(), e);
			}

		}
		finally
		{
			// put if absent in case two threads deserialize at the same time
			// unfortunately we can't put everything in a computeIfAbsent() since we also throw exceptions
			WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(finalResourceStateString, foundWrapper);

			if (foundWrapper != AIR)
			{
				int blockIdAndMeta = 0;
				// should always be true (the only exception should be air),
				// but just in case
				if (foundWrapper.blockState instanceof FakeBlockState)
				{
					blockIdAndMeta = ((FakeBlockState)foundWrapper.blockState).getIdAndMeta();
				}

				WRAPPER_BY_BLOCK_ID_AND_META.putIfAbsent(blockIdAndMeta, foundWrapper);
			}
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

		if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}

		BlockStateWrapper that = (BlockStateWrapper) obj;
		// the serialized value is used so we can test the contents instead of the references
		return Objects.equals(this.getSerialString(), that.getSerialString());
	}

	@Override
	public int hashCode() { return this.hashCode; }

	@Override
	public String toString() { return this.getSerialString(); }

	//endregion



}
