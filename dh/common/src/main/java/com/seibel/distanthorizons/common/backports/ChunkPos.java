package com.seibel.distanthorizons.common.backports;

/**
 * Recreation of MC's 1.12+ ChunkPos object.
 */
public class ChunkPos
{
	public final int x;
	public final int z;

	//==============//
	// constructors //
	//==============//
	//region

	public ChunkPos(int x, int z)
	{
		this.x = x;
		this.z = z;
	}

	//endregion

	//================//
	// base overrides //
	//================//
	//region

	@Override
	public int hashCode()
	{
		final int i = 1664525 * this.x + 1013904223;
		final int j = 1664525 * (this.z ^ -559038737) + 1013904223;
		return i ^ j;
	}

	@Override
	public boolean equals(Object object)
	{
		if (this == object)
		{
			return true;
		}
		else if (object instanceof ChunkPos)
		{
			ChunkPos pos = (ChunkPos) object;
			return this.x == pos.x
				&& this.z == pos.z;
		}
		else
		{
			return false;
		}
	}

	//endregion
}
