package com.seibel.distanthorizons.common.wrappers.interfaces;

/**
 * Only needed for MC 1.7.10
 */
public interface IMixinChunk
{
	/**
	 * counts the blocks and metadata changed in this chunk since it was loaded,
	 * comparing two values tells if the chunk changed in between
	 */
	int distanthorizons$getBlockChangeCount();
}
