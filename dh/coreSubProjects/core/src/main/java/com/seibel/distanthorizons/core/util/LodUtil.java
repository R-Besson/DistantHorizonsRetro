/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.coreapi.util.ColorUtil;

/**
 * This class holds methods and constants that may be used in multiple places.
 */
public class LodUtil
{
	/**
	 * In order of nearest to farthest: <br>
	 * Red, Orange, Yellow, Green, Cyan, Blue, Magenta, white, gray, black
	 */
	public static final int[] DEBUG_DETAIL_LEVEL_COLORS = new int[]{
		ColorUtil.rgbToInt(255, 0, 0), ColorUtil.rgbToInt(255, 127, 0),
		ColorUtil.rgbToInt(255, 255, 0), ColorUtil.rgbToInt(127, 255, 0),
		ColorUtil.rgbToInt(0, 255, 0), ColorUtil.rgbToInt(0, 255, 127),
		ColorUtil.rgbToInt(0, 255, 255), ColorUtil.rgbToInt(0, 127, 255),
		ColorUtil.rgbToInt(0, 0, 255), ColorUtil.rgbToInt(127, 0, 255),
		ColorUtil.rgbToInt(255, 0, 255), ColorUtil.rgbToInt(255, 127, 255),
		ColorUtil.rgbToInt(255, 255, 255)};


	/** 512 blocks wide */
	public static final byte REGION_DETAIL_LEVEL = 9;
	/** 16 blocks wide */
	public static final byte CHUNK_DETAIL_LEVEL = 4;
	/** 1 block wide */
	public static final byte BLOCK_DETAIL_LEVEL = 0;

	/**
	 * measured in Blocks <br>
	 * detail level 9
	 * 512 x 512 blocks
	 */
	public static final short REGION_WIDTH = 512;
	/**
	 * measured in Blocks <br>
	 * detail level 4
	 * 16 x 16 blocks
	 */
	public static final short CHUNK_WIDTH = 16;


	/** maximum possible light level handled by Minecraft */
	public static final byte MAX_MC_LIGHT = 15;
	/** lowest possible light level handled by Minecraft */
	public static final byte MIN_MC_LIGHT = 0;

	/** the opacity value returned by {@link IBlockStateWrapper#getOpacity()} if a block is fully transparent */
	public static final int BLOCK_FULLY_TRANSPARENT = 0;
	/** the opacity value returned by {@link IBlockStateWrapper#getOpacity()} if a block is fully opaque */
	public static final int BLOCK_FULLY_OPAQUE = 16;

	public static final double ROCKET_ELYTRA_SPEED_IN_BLOCKS_PER_SEC = 30.0;
	public static final double MAX_SPECTATOR_SPEED_IN_BLOCKS_PER_SEC = 100.0;



	//=========//
	// methods //
	//=========//

	/** Returns the chunk int position for the given double position */
	public static int getChunkPosFromDouble(double value) { return (int) Math.floor(value / CHUNK_WIDTH); }
	/** Returns the float position inside the chunk for the given double position */
	public static float getSubChunkPosFromDouble(double value)
	{
		double chunkPos = Math.floor(value / CHUNK_WIDTH);
		return (float) (value - chunkPos * CHUNK_WIDTH);
	}

	public static class AssertFailureException extends RuntimeException
	{
		public AssertFailureException(String message)
		{
			super(message);
		}

	}

	public static void assertTrue(boolean condition)
	{
		if (!condition)
		{
			throw new AssertFailureException("Assertion failed");
		}
	}
	public static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertFailureException("Assertion failed:\n " + message);
		}
	}
	public static void assertNotReach(String message)
	{
		throw new AssertFailureException("Assert Not Reach failed:\n " + message);
	}



}
