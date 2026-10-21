package com.seibel.distanthorizons.common.wrappers.interfaces;

/**
 * Only needed for MC 1.7.10
 */
public interface IMixinTextureAtlasSprite 
{
	/** the first frame, packed in ARGB format */
    int[] distanthorizons$getSpriteData();

	/**
	 * every animation frame, packed in ARGB format, at least 1 once loaded. <br>
	 * null if the sprite hasn't been loaded
	 */
    int[][] distanthorizons$getSpriteFrames();
	
	/** set from the base texture */
    void distanthorizons$setSpriteData();
	
}
