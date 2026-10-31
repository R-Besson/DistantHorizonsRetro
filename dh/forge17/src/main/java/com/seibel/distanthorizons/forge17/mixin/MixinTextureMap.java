package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.common.wrappers.interfaces.IMixinTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TextureMap.class)
public class MixinTextureMap
{
	// a plain redirect since the mixin annotation processor only remaps the method name for Mixin's own injectors
	@Redirect(
		method = "loadTextureAtlas",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;load(Lnet/minecraft/client/resources/IResourceManager;Lnet/minecraft/util/ResourceLocation;)Z",
			remap = false))
	private boolean load(TextureAtlasSprite instance, IResourceManager manager, ResourceLocation location)
	{
		boolean returnValue = instance.load(manager, location);

		IMixinTextureAtlasSprite mixinSprite = (IMixinTextureAtlasSprite) instance;
		mixinSprite.distanthorizons$setSpriteData();

		return returnValue;
	}
}
