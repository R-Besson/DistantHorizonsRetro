package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entities are drawn up to the render distance, measured in chunks like the vanilla terrain,
 * with or without a shader pack. <br><br>
 *
 * Mobs are always drawn inside that, vanilla's range depends on their size
 * (IE 34 blocks for a chicken) which used to be hidden by the fog DH turns off.
 */
@Mixin(Entity.class)
public class MixinEntity
{
	@Shadow
	public double posX;
	@Shadow
	public double posZ;

	@Inject(method = "isInRangeToRender3d", at = @At("HEAD"), cancellable = true)
	private void distanthorizons$matchVanillaTerrainDistance(double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir)
	{
		if (!Config.Client.quickEnableRendering.get())
		{
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// the camera is read directly, renderers replacing vanilla's (IE Angelica) may pass something else
		Entity camera = mc.renderViewEntity;
		if (camera == null)
		{
			return;
		}

		int renderDistance = mc.gameSettings.renderDistanceChunks;
		int chunkDistanceX = Math.abs((MathHelper.floor_double(this.posX) >> 4) - (MathHelper.floor_double(camera.posX) >> 4));
		int chunkDistanceZ = Math.abs((MathHelper.floor_double(this.posZ) >> 4) - (MathHelper.floor_double(camera.posZ) >> 4));
		if (chunkDistanceX > renderDistance || chunkDistanceZ > renderDistance)
		{
			cir.setReturnValue(false);
		}
		else if ((Object) this instanceof EntityLiving)
		{
			cir.setReturnValue(true);
		}
	}

}
