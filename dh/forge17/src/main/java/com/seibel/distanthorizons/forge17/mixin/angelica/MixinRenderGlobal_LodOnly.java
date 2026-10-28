package com.seibel.distanthorizons.forge17.mixin.angelica;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * LOD-only mode skips Angelica's terrain. <br>
 * Without a shader pack the vanilla fade pass hides it instead,
 * shader packs don't get that pass so the terrain is never drawn.
 */
// Angelica overwrites sortAndRender, the higher priority applies this after it
@Mixin(value = RenderGlobal.class, priority = 1100)
public class MixinRenderGlobal_LodOnly
{
	// a plain redirect since the mixin annotation processor only remaps the method name for Mixin's own injectors.
	// require = 0 so a different Angelica version only loses LOD-only mode with shaders instead of crashing
	@Redirect(
		method = "sortAndRender",
		at = @At(
			value = "INVOKE",
			target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/CeleritasWorldRenderer;drawChunkLayer(Lcom/gtnewhorizons/angelica/rendering/celeritas/BlockRenderLayer;DDD)V",
			remap = false),
		require = 0)
	private void distanthorizons$skipTerrainInLodOnlyMode(CeleritasWorldRenderer renderer, BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ)
	{
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get()
			&& Config.Client.quickEnableRendering.get()
			&& Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT)
		{
			return;
		}

		renderer.drawChunkLayer(layer, cameraX, cameraY, cameraZ);
	}

}
