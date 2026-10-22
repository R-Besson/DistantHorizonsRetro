package com.seibel.distanthorizons.forge17.mixin.angelica;

import com.seibel.distanthorizons.forge17.modAccessor.ShaderPackLodTextures;
import net.coderbot.iris.compat.dh.IrisLodRenderProgram;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Binds DH's block textures for the shader pack's LOD program, see ShaderPackLodTextures */
@Mixin(value = IrisLodRenderProgram.class, remap = false)
public class MixinIrisLodRenderProgram
{
	@Shadow
	@Final
	private int id;

	// after Iris has bound the pack's own samplers.
	// The method's arguments aren't captured, DH relocates libraries (IE JOML)
	// so they wouldn't match Angelica's.
	@Inject(method = "fillUniformData", at = @At("TAIL"), require = 0)
	private void distanthorizons$bindLodTextures(CallbackInfo ci)
	{
		ShaderPackLodTextures.bindAtlas(this.id);
	}

}
