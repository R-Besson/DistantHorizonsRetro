package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.common.wrappers.interfaces.IMixinTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite implements IMixinTextureAtlasSprite
{
	@Unique
	private static final String MISSING_SPRITE_NAME = "missingno";

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

	/**
	 * Sprites with a custom loader (IE IC2's BlockTextureStitched) fill their frames themselves
	 * without calling loadSprite, MC clears the frames of every still sprite after uploading the atlas
	 * so they're captured here instead
	 */
	@Inject(method = "clearFramesTextureData", at = @At("HEAD"))
	private void injectClearFramesTextureData(CallbackInfo ci)
	{ this.distanthorizons$captureCustomLoadedFrames(); }

	public void distanthorizons$setSpriteData()
	{
		if (this.framesTextureData == null
			|| this.framesTextureData.isEmpty())
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
	public int[] distanthorizons$getSpriteData()
	{
		this.distanthorizons$captureCustomLoadedFrames();
		return this.distanthorizons$spriteData;
	}

	@Override
	public int[][] distanthorizons$getSpriteFrames()
	{
		this.distanthorizons$captureCustomLoadedFrames();
		return this.distanthorizons$spriteFrames;
	}

	/**
	 * Animated sprites with a custom loader keep their frames, so they're captured when first needed. <br>
	 * The missing sprite is skipped, it's what the atlas returns for unknown names
	 * and those are told apart by not having sprite data.
	 */
	@SuppressWarnings("ConstantValue") @Unique
	private void distanthorizons$captureCustomLoadedFrames()
	{
		if (this.distanthorizons$spriteFrames == null
			&& !MISSING_SPRITE_NAME.equals(((TextureAtlasSprite) (Object) this).getIconName()))
		{
			this.distanthorizons$setSpriteData();
		}
	}

	//endregion
}
