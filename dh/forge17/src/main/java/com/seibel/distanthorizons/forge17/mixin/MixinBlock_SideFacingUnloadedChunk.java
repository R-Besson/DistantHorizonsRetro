package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.common.wrappers.modAccessor.CubicChunksCompat;
import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Block.class)
public class MixinBlock_SideFacingUnloadedChunk
{
	/**
	 * This is necessary for under-water chunks to look
	 * correct on the faded LODs. <br><br>
	 *
	 * If not included the water chunks will render walls when moving
	 * which looks bad. <br><br>
	 *
	 * A plain inject since the mixin annotation processor only remaps the method name for Mixin's own injectors.
	 */
	@Inject(method = "shouldSideBeRendered", at = @At("TAIL"), cancellable = true)
	private void shouldSideBeRenderedFix(
		IBlockAccess blockAccess, int x, int y, int z, int side,
		CallbackInfoReturnable<Boolean> cir)
	{
		if (cir.getReturnValueZ()
			&& !this.isSideFacingLoadedChunk(blockAccess, x, y, z, side))
		{
			cir.setReturnValue(false);
		}
	}

	private boolean isSideFacingLoadedChunk(IBlockAccess blockAccess, int x, int y, int z, int side)
	{
		// Skip invalid orientations, and early exit for UP/DOWN
		if (side < 2
			|| side >= 6)
		{
			return true;
		}
		ForgeDirection direction = ForgeDirection.VALID_DIRECTIONS[side];

		// Subtract to get original block pos, our position is the block the new side faces already!
		int originalChunkX = (x - direction.offsetX) >> 4;
		int originalChunkZ = (z - direction.offsetZ) >> 4;

		x >>= 4;
		z >>= 4;

		if (originalChunkX == x
			&& originalChunkZ == z)
		{
			return true;
		}

		if (blockAccess instanceof ChunkCache)
		{
			IMixinChunkCache_SideFacingUnloaded cache = (IMixinChunkCache_SideFacingUnloaded) blockAccess;
			Chunk[][] chunks = cache.getChunkArray();

			int cacheX = x - cache.getChunkX();
			int cacheZ = z - cache.getChunkZ();

			if (cacheX < 0 || cacheX >= chunks.length
				|| cacheZ < 0 || cacheZ >= chunks[cacheX].length)
			{
				return false;
			}
			else if (chunks[cacheX][cacheZ] instanceof EmptyChunk)
			{
				return false;
			}
		}

		// Renderers with their own block access (IE Angelica's) see unloaded chunks as air,
		// and Cubic Chunks loads a column before its cubes,
		// either way the water next to them would draw walls into the LODs
		try
		{
			World world = Minecraft.getMinecraft().theWorld;
			if (world == null)
			{
				return true;
			}

			Chunk column = world.getChunkFromChunkCoords(x, z);
			if (column.isEmpty())
			{
				return false;
			}

			ICubicChunksCommonAccessor cubicChunks = CubicChunksCompat.getAccessor(world);
			return cubicChunks == null || cubicChunks.isCubeLoaded(column, y >> 4);
		}
		catch (Exception e)
		{
			// Angelica builds chunks off the main thread, a chunk unloading at the same time isn't worth a crash
			return true;
		}
	}



}
