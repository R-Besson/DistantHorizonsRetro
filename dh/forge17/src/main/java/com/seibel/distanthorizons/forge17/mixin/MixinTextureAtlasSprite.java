package com.seibel.distanthorizons.forge17.mixin;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seibel.distanthorizons.common.wrappers.interfaces.IMixinTextureAtlasSprite;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite implements IMixinTextureAtlasSprite 
{
    @Shadow
    protected List<int[][]> framesTextureData;
	
	/** contains the individual pixel data in ARGB format */
    @Unique
    private int[] distanthorizons$spriteData;
    @Unique
    private int[][] distanthorizons$spriteFrames;
	
	
	
	//=====================//
	// getters and setters //
	//=====================//
	//region
	
	@Inject(method = "loadSprite", at = @At("RETURN"))
	private void injectLoadSprite(BufferedImage[] bufferedImages, AnimationMetadataSection p_147964_2_, boolean p_147964_3_, CallbackInfo ci)
	{ this.distanthorizons$setSpriteData(); }
	
	public void distanthorizons$setSpriteData()
	{
		if (this.framesTextureData.isEmpty())
		{
			return;
		}
		
		// the base mipmap of every frame, animated textures (IE lava) change brightness between frames
		int[][] frames = new int[this.framesTextureData.size()][];
		int frameCount = 0;
		for (int[][] frameData : this.framesTextureData)
		{
			if (frameData != null
				&& frameData[0] != null)
			{
				frames[frameCount++] = frameData[0].clone();
			}
		}
		if (frameCount == 0)
		{
			return;
		}

		this.distanthorizons$spriteFrames = (frameCount == frames.length) ? frames : Arrays.copyOf(frames, frameCount);
		this.distanthorizons$spriteData = this.distanthorizons$spriteFrames[0];
	}

	@Override
	public int[] distanthorizons$getSpriteData() { return this.distanthorizons$spriteData; }

	@Override
	public int[][] distanthorizons$getSpriteFrames() { return this.distanthorizons$spriteFrames; }
	
	//endregion
	
	
	
}
