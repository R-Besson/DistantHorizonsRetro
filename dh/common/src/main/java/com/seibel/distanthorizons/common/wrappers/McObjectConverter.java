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

package com.seibel.distanthorizons.common.wrappers;

import java.nio.FloatBuffer;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.math.DhMat4f;

import org.jetbrains.annotations.Nullable;

import com.seibel.distanthorizons.common.backports.BlockPos;
import com.seibel.distanthorizons.common.backports.ChunkPos;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * This class converts to and from Minecraft objects (Ex: Matrix4f)
 * and objects we created (Ex: Mat4f).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class McObjectConverter
{


	//========//
	// matrix //
	//========//
	//region

	/** 4x4 float matrix converter */
	public static DhMat4f convert(org.joml.Matrix4f mcMatrix)
	{
		FloatBuffer buffer = FloatBuffer.allocate(16);
		storeMatrix(mcMatrix, buffer);
		return new DhMat4f(buffer);
	}
	private static void storeMatrix(org.joml.Matrix4f matrix, FloatBuffer buffer)
	{
		// JOML's Matrix4f.get(FloatBuffer) routes through MemUtilUnsafe in the newer JOML and segfaults on heap buffers, so put each entry manually.
		buffer.put(bufferIndex(0, 0), matrix.m00());
		buffer.put(bufferIndex(0, 1), matrix.m01());
		buffer.put(bufferIndex(0, 2), matrix.m02());
		buffer.put(bufferIndex(0, 3), matrix.m03());
		buffer.put(bufferIndex(1, 0), matrix.m10());
		buffer.put(bufferIndex(1, 1), matrix.m11());
		buffer.put(bufferIndex(1, 2), matrix.m12());
		buffer.put(bufferIndex(1, 3), matrix.m13());
		buffer.put(bufferIndex(2, 0), matrix.m20());
		buffer.put(bufferIndex(2, 1), matrix.m21());
		buffer.put(bufferIndex(2, 2), matrix.m22());
		buffer.put(bufferIndex(2, 3), matrix.m23());
		buffer.put(bufferIndex(3, 0), matrix.m30());
		buffer.put(bufferIndex(3, 1), matrix.m31());
		buffer.put(bufferIndex(3, 2), matrix.m32());
		buffer.put(bufferIndex(3, 3), matrix.m33());
	}
	private static int bufferIndex(int x, int y) { return y * 4 + x; }

	//endregion



	//===========//
	// direction //
	//===========//
	//region

	/** Can be null since some MC methods need a null direction */
	@Nullable
	public static ForgeDirection convert(EDhDirection dhDirection)
	{
		if (dhDirection == null)
		{
			return null;
		}

		switch (dhDirection)
		{
			case DOWN: return ForgeDirection.DOWN;
			case UP: return ForgeDirection.UP;
			case NORTH: return ForgeDirection.NORTH;
			case SOUTH: return ForgeDirection.SOUTH;
			case WEST: return ForgeDirection.WEST;
			case EAST: return ForgeDirection.EAST;

			default: throw new IllegalArgumentException("No Minecraft direction defined for [" + dhDirection + "].");
		}
	}

	//endregion



	//==================//
	// position objects //
	//==================//
	//region

	public static BlockPos convert(DhBlockPos wrappedPos) { return new BlockPos(wrappedPos.getX(), wrappedPos.getY(), wrappedPos.getZ()); }

	public static ChunkPos convert(DhChunkPos wrappedPos) { return new ChunkPos(wrappedPos.getX(), wrappedPos.getZ()); }
	public static DhChunkPos convert(ChunkPos mcPos)
	{
		return new DhChunkPos(mcPos.x, mcPos.z);
	}

	//endregion



}
