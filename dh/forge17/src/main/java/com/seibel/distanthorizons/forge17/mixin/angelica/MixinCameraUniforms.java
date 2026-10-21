package com.seibel.distanthorizons.forge17.mixin.angelica;

import com.seibel.distanthorizons.forge17.modAccessor.AngelicaAccessor;
import net.coderbot.iris.uniforms.CameraUniforms;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader packs fade vanilla terrain into LODs at the "far" uniform. <br>
 * With DH installed Angelica always makes it a chunk shorter than the render distance,
 * which at low render distances puts the LODs half as far away as without shaders.
 */
@Mixin(value = CameraUniforms.class, remap = false)
public class MixinCameraUniforms
{
	// require = 0 so a different Angelica version only loses this fix instead of crashing
	@Inject(method = "getRenderDistanceInBlocks", at = @At("HEAD"), cancellable = true, require = 0)
	private static void distanthorizons$useRenderedTerrainDistance(CallbackInfoReturnable<Integer> cir)
	{
		cir.setReturnValue(AngelicaAccessor.getRenderedTerrainDistanceInBlocks());
	}

}
