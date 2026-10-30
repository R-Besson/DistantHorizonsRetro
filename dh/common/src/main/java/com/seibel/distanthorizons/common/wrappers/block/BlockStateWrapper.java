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
import com.seibel.distanthorizons.common.backports.FakeBlockState;
import com.seibel.distanthorizons.common.backports.IBlockState;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import cpw.mods.fml.common.registry.GameData;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemDye;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;



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
	public static final BlockStateWrapper AIR = new BlockStateWrapper(null, null);

	private static final String WATER_RESOURCE_LOCATION_STRING = "minecraft:water";
	private static BlockStateWrapper waterBlock = null;

	private static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";
	private static BlockStateWrapper dirtBlock = null;

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
	private final String serialString;
	private final int hashCode;
	/** Should be between {@link LodUtil#BLOCK_FULLY_OPAQUE} and {@link LodUtil#BLOCK_FULLY_OPAQUE} */
	private final int opacity;
	/** MC's light opacity, clamped to [0, 15] */
	private final int lightOpacity;
	/** used by the Iris shader mod to determine how each LOD should be rendered */
	private byte blockMaterialId = 0;
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


	public static BlockStateWrapper fromBlockState(IBlockState blockState)
	{ return fromBlockState(blockState.getBlock(), blockState.getMeta()); }

	/**
	 * Can be faster than BlockStateWrapper#fromBlockState(BlockState, ILevelWrapper)
	 * in cases where the same block state is expected to be referenced multiple times.
	 */
	public static BlockStateWrapper fromBlockState(Block block, int meta, IBlockStateWrapper guess)
	{
		if (guess == null)
		{
			return fromBlockState(block, meta);
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
		return fromBlockState(block, meta);
	}

	public static BlockStateWrapper fromBlockState(Block block, int meta)
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
			BlockStateWrapper apiWrapper = new BlockStateWrapper(blockState, null);
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
				BlockStateWrapper returnWrapper = new BlockStateWrapper(blockState, eventParam);
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

	private BlockStateWrapper(@Nullable IBlockState blockState, @Nullable DhApiBlockStateWrapperCreatedEvent.EventParam overrideEventParam)
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
		this.serialString = serialize(blockState);
		this.hashCode = Objects.hash(this.serialString);
		String lowerCaseSerial = this.serialString.toLowerCase();



		// is liquid //
		{
			if (this.isAir()
				|| this.blockState == null) // == null isn't necessary since it's handled in isAir() but is here to prevent intellij from complaining
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
			
			this.lodShape = calculateLodShape(this.blockState);
			
			// an opacity override from the API is used for lighting too
			this.lightOpacity = (overrideEventParam != null && overrideEventParam.getOpacity() != null)
				? this.opacity
				: calculateLightOpacity(this.blockState, isAir(this.blockState));

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


		// texture ignoring //
		{
			String dontTextureNamesCsv = Config.Client.Advanced.Graphics.Texture.blocksDontRenderTextureCsv.get();
			this.renderTexture = !blockSerialInCsv(lowerCaseSerial, dontTextureNamesCsv);
		}


		// is solid //
		{
			if (this.isAir()
				|| this.blockState == null) // "== null" isn't necessary since it's handled in isAir() but is here to prevent IntelliJ from complaining
			{
				this.isSolid = false;
			}
			else
			{
				// snow layers use the non-solid snow material, so thick snow (4+ layers) would be skipped like grass.
				// 1-3 layers are still flattened onto the block below by the LOD builder
				this.isSolid = this.blockState.getMaterial().isSolid()
					|| this.blockState.getBlock() == Blocks.snow_layer;
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



		//=================//
		// light emitting  //
		//=================//
		//region

		// checked before the step sounds and names, otherwise torches and jack o'lanterns (wood sound)
		// or glowstone (stone in its name) wouldn't glow with shader packs
		if (getLightEmission(blockState) > 0)
		{
			return EDhApiBlockMaterial.ILLUMINATED;
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

	private static int calculateLightOpacity(@Nullable IBlockState blockState, boolean isAir)
	{
		if (isAir || blockState == null)
		{
			return LodUtil.BLOCK_FULLY_TRANSPARENT;
		}
		
		// IE leaves are 1, so light under a tree is only a little darker like in MC
		return Math.max(LodUtil.BLOCK_FULLY_TRANSPARENT, Math.min(blockState.getBlock().getLightOpacity(), LodUtil.BLOCK_FULLY_OPAQUE));
	}
	
	private static byte calculateLodShape(@Nullable IBlockState blockState)
	{
		if (blockState == null)
		{
			return RenderDataPointUtil.SHAPE_NONE;
		}
		
		Block block = blockState.getBlock();
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
			boolean bottomSlab = !block.isOpaqueCube() && (blockState.getMeta() & 8) == 0;
			return bottomSlab ? RenderDataPointUtil.SHAPE_BOTTOM_HALF : RenderDataPointUtil.SHAPE_NONE;
		}
		else if (block == Blocks.snow_layer)
		{
			// the meta is the layer count minus one.
			// 1-3 layers are flattened onto the block below by the LOD builder, 6-8 are closer to a full block
			int layers = (blockState.getMeta() & 7) + 1;
			return (layers == 4 || layers == 5) ? RenderDataPointUtil.SHAPE_BOTTOM_HALF : RenderDataPointUtil.SHAPE_NONE;
		}
		
		return RenderDataPointUtil.SHAPE_NONE;
	}
	
	private static boolean getCanOcclude(@Nullable IBlockState blockState)
	{
		// defaults to the value used by air
		boolean canOcclude = false;
		if (blockState != null)
		{
			// material.isSolid() is true for glass, fences and other blocks light and vision can pass through
			canOcclude = blockState.getBlock().isOpaqueCube();
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

	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredBlocks()
	{
		// use the cached version if possible
		if (rendererIgnoredBlocks != null)
		{
			return rendererIgnoredBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlock = new ObjectOpenHashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderBlockCsv, baseIgnoredBlock);
		return rendererIgnoredBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getRendererIgnoredCaveBlocks()
	{
		// use the cached version if possible
		if (rendererIgnoredCaveBlocks != null)
		{
			return rendererIgnoredCaveBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlock = new ObjectOpenHashSet<>();
		baseIgnoredBlock.add(AIR_STRING);
		rendererIgnoredCaveBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.ignoredRenderCaveBlockCsv, baseIgnoredBlock);
		return rendererIgnoredCaveBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSurfaceReplacementBlocks()
	{
		// use the cached version if possible
		if (waterSurfaceReplacementBlocks != null)
		{
			return waterSurfaceReplacementBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlockResourceSet = new ObjectOpenHashSet<>();
		waterSurfaceReplacementBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSurfaceBlockReplacementCsv, baseIgnoredBlockResourceSet);
		waterSurfaceReplacementBlocks.remove(AIR);

		return waterSurfaceReplacementBlocks;
	}
	public static ObjectOpenHashSet<IBlockStateWrapper> getWaterSubsurfaceReplacementBlocks()
	{
		// use the cached version if possible
		if (waterSubsurfaceReplacementBlocks != null)
		{
			return waterSubsurfaceReplacementBlocks;
		}

		ObjectOpenHashSet<String> baseIgnoredBlockResourceSet = new ObjectOpenHashSet<>();
		waterSubsurfaceReplacementBlocks = getAllBlockWrappers(Config.Client.Advanced.Graphics.Culling.waterSubSurfaceBlockReplacementCsv, baseIgnoredBlockResourceSet);
		// air will be present if any invalid resource locations are present,
		// but we don't want to replace air with water, that'll cause monoliths
		waterSubsurfaceReplacementBlocks.remove(AIR);

		return waterSubsurfaceReplacementBlocks;
	}

	//endregion



	//======================//
	// static block getters //
	//======================//
	//region

	public static BlockStateWrapper getWaterBlockStateWrapper()
	{
		// use the cached version if possible
		if (waterBlock != null)
		{
			return waterBlock;
		}

		waterBlock = BlockStateWrapper.deserializeOrDefault(WATER_RESOURCE_LOCATION_STRING);
		if (waterBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+WATER_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return waterBlock;
	}

	public static BlockStateWrapper getDirtBlockStateWrapper()
	{
		// use the cached version if possible
		if (dirtBlock != null)
		{
			return dirtBlock;
		}

		dirtBlock = BlockStateWrapper.deserializeOrDefault(DIRT_RESOURCE_LOCATION_STRING);
		if (dirtBlock.equals(AIR))
		{
			LOGGER.warn("Failed to deserialize ["+DIRT_RESOURCE_LOCATION_STRING+"], issues may occur.");
		}

		return dirtBlock;
	}

	//endregion



	//=====================//
	// lod builder helpers //
	//=====================//
	//region

	private static ObjectOpenHashSet<IBlockStateWrapper> getAllBlockWrappers(ConfigEntry<String> config, ObjectOpenHashSet<String> baseResourceLocations)
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

		return getAllBlockWrappers(blockStringList);
	}
	/** block names since MC 1.13 to their 1.7.10 names */
	private static final Map<String, String> MODERN_BLOCK_NAME_ALIASES = Collections.singletonMap("minecraft:lily_pad", "minecraft:waterlily");
	private static ObjectOpenHashSet<IBlockStateWrapper> getAllBlockWrappers(ObjectOpenHashSet<String> blockResourceLocationSet)
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
				if (cleanedResourceLocation.isEmpty())
				{
					continue;
				}
				// configs can still have block names from newer MC versions (IE the old defaults)
				cleanedResourceLocation = MODERN_BLOCK_NAME_ALIASES.getOrDefault(cleanedResourceLocation, cleanedResourceLocation);


				BlockStateWrapper defaultBlockStateToIgnore = deserialize(cleanedResourceLocation);
				blockStateWrappers.add(defaultBlockStateToIgnore);

				if (defaultBlockStateToIgnore != AIR)
				{
					BlockStateWrapper newBlockToIgnore = BlockStateWrapper.fromBlockState(defaultBlockStateToIgnore.blockState);
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
	}

	//endregion



	//=================//
	// wrapper methods //
	//=================//
	//region

	@Override
	public int getOpacity() { return this.opacity; }
	@Override
	public byte getLodShape() { return this.lodShape; }

	/** MC's crossed squares (IE tall grass, flowers, saplings) and double plants */
	@Override
	public boolean isCrossPlant()
	{
		if (this.blockState == null)
		{
			return false;
		}
		Block block = this.blockState.getBlock();
		int renderType = block.getRenderType();
		return renderType == CROSSED_SQUARES_RENDER_TYPE
			|| renderType == DOUBLE_PLANT_RENDER_TYPE
			// torches and fire are thin sprites too, as crosses they still show where the lights are at night.
			// Only when textured, untextured crosses fill their block and a torch would be a big brown X
			|| ((renderType == TORCH_RENDER_TYPE || renderType == FIRE_RENDER_TYPE)
				&& Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get())
			// mod plants with their own renderer, which almost always draws crossed squares too
			// (IE Biomes O' Plenty's grasses and flowers). Vanilla's other bushes (crops, lily pads)
			// have vanilla render types and aren't crosses
			|| (block instanceof BlockBush && renderType >= FIRST_MOD_RENDER_TYPE)
			// vines are flat on a wall, a LOD can't hold that but from a distance
			// crosses read as the same hanging green (IE swamp trees), skipped they'd be gone entirely
			|| block instanceof BlockVine;
	}
	private static final int CROSSED_SQUARES_RENDER_TYPE = 1;
	private static final int TORCH_RENDER_TYPE = 2;
	private static final int FIRE_RENDER_TYPE = 3;
	private static final int DOUBLE_PLANT_RENDER_TYPE = 40;
	/** vanilla's render types end at 41, mod renderers get the ids after it (see RenderingRegistry.getNextAvailableRenderId) */
	private static final int FIRST_MOD_RENDER_TYPE = 42;

	/**
	 * 1.7.10 double plant tops are always meta 8, their type is the bottom's meta.
	 * The returned top's meta is 8 plus the type, which MC never stores
	 * but is understood by {@link TextureAtlasSpriteWrapper#resolveFaceSprite} and FakeWorld.
	 */
	@Override
	public IBlockStateWrapper getPlantTopWithBottom(IBlockStateWrapper blockBelow)
	{
		if (this.blockState == null
			|| !(this.blockState.getBlock() instanceof BlockDoublePlant)
			|| this.blockState.getMeta() != DOUBLE_PLANT_TOP_META
			|| !(blockBelow instanceof BlockStateWrapper))
		{
			return this;
		}

		IBlockState below = ((BlockStateWrapper) blockBelow).blockState;
		if (below == null
			|| below.getBlock() != this.blockState.getBlock()
			|| (below.getMeta() & DOUBLE_PLANT_TOP_META) != 0)
		{
			return this;
		}

		return fromBlockState(this.blockState.getBlock(), DOUBLE_PLANT_TOP_META | below.getMeta());
	}
	private static final int DOUBLE_PLANT_TOP_META = 8;

	@Override
	public int getLightOpacity() { return this.lightOpacity; }

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

	private static String serialize(IBlockState blockState)
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
	public static BlockStateWrapper deserializeOrDefault(String resourceStateString)
	{
		try
		{
			return deserialize(resourceStateString);
		}
		catch (IOException e)
		{
			return BlockStateWrapper.AIR;
		}
	}

	/** will only work if a level is currently loaded */
	public static BlockStateWrapper deserialize(String resourceStateString) throws IOException
	{
		// we need the final string for the concurrent hash map later
		final String finalResourceStateString = resourceStateString;

		if (finalResourceStateString.equals(AIR_STRING)
			|| finalResourceStateString.isEmpty()) // the empty string shouldn't normally happen, but just in case
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

				foundWrapper = fromBlockState(block, meta);
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
