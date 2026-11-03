package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.common.wrappers.interfaces.IMixinChunk;
import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets world generation tell if a chunk changed after its LOD was built,
 * MC doesn't tell world listeners about changes in chunks that haven't ticked yet (IE during world gen).
 */
@Mixin(Chunk.class)
public class MixinChunk_BlockChangeCount implements IMixinChunk
{
	/** only written on the server thread, read by DH's world gen threads */
	@Unique
	private volatile int distanthorizons$blockChangeCount = 0;

	@Inject(method = "func_150807_a", at = @At("RETURN"))
	private void distanthorizons$countBlockChange(int x, int y, int z, Block block, int meta, CallbackInfoReturnable<Boolean> cir)
	{
		if (cir.getReturnValueZ())
		{
			//noinspection NonAtomicOperationOnVolatileField
			this.distanthorizons$blockChangeCount++;
		}
	}

	@Inject(method = "setBlockMetadata", at = @At("RETURN"))
	private void distanthorizons$countMetadataChange(int x, int y, int z, int meta, CallbackInfoReturnable<Boolean> cir)
	{
		if (cir.getReturnValueZ())
		{
			//noinspection NonAtomicOperationOnVolatileField
			this.distanthorizons$blockChangeCount++;
		}
	}

	@Override
	public int distanthorizons$getBlockChangeCount() { return this.distanthorizons$blockChangeCount; }
}
