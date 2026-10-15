package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Expected to only be used in MC 1.7.10
 */
public interface IBiomeHandler extends IBindable
{
	BiomeGenBase getBiomeByName(String name);
}
