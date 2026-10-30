package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
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
 * (IE 34 blocks for a chicken) which used to be hidden by the fog DH turns off. <br><br>
 *
 * Shader packs that dither vanilla terrain out before the render distance (IE Complementary)
 * show LODs past that point, entities there aren't drawn like modern MC's in chunks it doesn't draw.
 */
@Mixin(Entity.class)
public class MixinEntity
{
	@Shadow
	public double posX;
	@Shadow
	public double posY;
	@Shadow
	public double posZ;

	@Inject(method = "isInRangeToRender3d", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("ConstantValue") // IntelliJ doesn't know this mixin's "this" is an Entity
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
		else if (this.isPastShaderPackVanillaTerrain(camera))
		{
			// the shader pack shows LODs there, which can't hide anything,
			// so entities would be seen through them (IE mobs in caves)
			cir.setReturnValue(false);
		}
		else if ((Object) this instanceof EntityLiving)
		{
			cir.setReturnValue(true);
		}
	}

	/** measured like the shader pack does, see IAngelicaAccessor#getShaderPackVanillaTerrainDistance */
	@SuppressWarnings("ConstantValue") // IntelliJ doesn't know this mixin's "this" is an Entity
	private boolean isPastShaderPackVanillaTerrain(Entity camera)
	{
		IAngelicaAccessor angelicaAccessor = ModAccessorInjector.INSTANCE.get(IAngelicaAccessor.class);
		if (angelicaAccessor == null
			|| (Object) this == camera
			|| camera.ridingEntity == (Object) this)
		{
			return false;
		}

		float vanillaTerrainDistance = angelicaAccessor.getShaderPackVanillaTerrainDistance();
		if (vanillaTerrainDistance < 0)
		{
			return false;
		}

		double offsetX = this.posX - camera.posX;
		double offsetZ = this.posZ - camera.posZ;
		double distance = Math.max(Math.sqrt((offsetX * offsetX) + (offsetZ * offsetZ)), Math.abs(this.posY - camera.posY));
		return distance > vanillaTerrainDistance;
	}

}
