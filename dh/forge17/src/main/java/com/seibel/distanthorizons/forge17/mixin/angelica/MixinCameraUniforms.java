package com.seibel.distanthorizons.forge17.mixin.angelica;

import net.coderbot.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader packs fade vanilla terrain into LODs at the "far" uniform. <br>
 * With DH installed Angelica makes it a chunk shorter than the render distance,
 * so with shaders vanilla terrain would stop before the render distance. <br>
 * Chunks Angelica hasn't drawn yet are filled by LODs (see VanillaChunkMask), so the full distance is used.
 */
@Mixin(value = CameraUniforms.class, remap = false)
public class MixinCameraUniforms
{
	// require = 0 so a different Angelica version only loses this fix instead of crashing
	@Inject(method = "getRenderDistanceInBlocks", at = @At("HEAD"), cancellable = true, require = 0)
	private static void distanthorizons$useFullRenderDistance(CallbackInfoReturnable<Integer> cir)
	{
		cir.setReturnValue(Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16);
	}
	
}
