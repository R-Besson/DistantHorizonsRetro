package com.seibel.distanthorizons.common.backports;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * A block + metadata pair, standing in for the "IBlockState" newer MC versions have.
 */
public interface IBlockState
{
	Material getMaterial();

	Block getBlock();

	/** kinda like the state on MC 1.16+ blocks, but stored as an int */
	int getMeta();

	int getLightValue();
}
