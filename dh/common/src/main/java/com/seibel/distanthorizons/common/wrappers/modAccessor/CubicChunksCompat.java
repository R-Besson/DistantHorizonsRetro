package com.seibel.distanthorizons.common.wrappers.modAccessor;

import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** World heights with and without Cubic Chunks. */
public final class CubicChunksCompat
{
	private CubicChunksCompat() { }



	/**
	 * Mod accessors are bound during mod init, long before any world loads,
	 * so the lookup is only done once.
	 */
	@Nullable
	private static ICubicChunksCommonAccessor getAccessor()
	{
		if (!accessorLookedUp)
		{
			accessor = ModAccessorInjector.INSTANCE.get(ICubicChunksCommonAccessor.class);
			accessorLookedUp = true;
		}
		return accessor;
	}
	@Nullable
	private static volatile ICubicChunksCommonAccessor accessor = null;
	private static volatile boolean accessorLookedUp = false;

	/** @return null if the world isn't a Cubic Chunks world */
	@Nullable
	public static ICubicChunksCommonAccessor getAccessor(@Nullable World world)
	{
		ICubicChunksCommonAccessor accessor = getAccessor();
		return (accessor != null && world != null && accessor.isCubicWorld(world)) ? accessor : null;
	}

	/** @see ICubicChunksCommonAccessor#getMinHeight */
	public static int getMinHeight(@Nullable World world)
	{
		ICubicChunksCommonAccessor accessor = getAccessor(world);
		return (accessor != null) ? accessor.getMinHeight(world) : 0;
	}

	/** @see ICubicChunksCommonAccessor#getMaxHeight */
	public static int getMaxHeight(@Nullable World world)
	{
		ICubicChunksCommonAccessor accessor = getAccessor(world);
		if (accessor != null)
		{
			return accessor.getMaxHeight(world);
		}
		return (world != null) ? world.getHeight() : 256;
	}

}
