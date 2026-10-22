package com.seibel.distanthorizons.forge17.mixin.angelica;

import com.seibel.distanthorizons.forge17.modAccessor.ShaderPackLodTextures;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Adds DH's block textures to the shader pack's LOD program, see ShaderPackLodTextures */
@Mixin(value = TransformPatcher.class, remap = false)
public class MixinTransformPatcher
{
	// The method's arguments aren't captured, DH relocates fastutil
	// so the Object2ObjectMap argument wouldn't match Angelica's.
	// require = 0 so a different Angelica version only loses the textures instead of crashing
	@Inject(method = "patchDHTerrain", at = @At("RETURN"), cancellable = true, require = 0)
	private static void distanthorizons$addLodTextures(CallbackInfoReturnable<Map<PatchShaderType, String>> cir)
	{
		cir.setReturnValue(ShaderPackLodTextures.patch(cir.getReturnValue()));
	}

}
