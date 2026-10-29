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

package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;

/**
 * An (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 4x4 float matrix.
 *
 * @author James Seibel
 * @version 11-11-2021
 */
public class DhMat4f extends DhApiMat4f
{
	/**
	 * A matrix containing all 0's. <br><br>
	 *
	 * Should not be modified. <br>
	 * Can be used for comparison testing.
	 */
	public static final DhApiMat4f EMPTY = new DhApiMat4f();
	/**
	 * The 4x4 identity matrix. <br><br>
	 *
	 * Should not be modified. <br>
	 * Can be used for comparison testing.
	 */
	public static final DhApiMat4f IDENTITY = new DhApiMat4f();
	static
	{
		IDENTITY.setIdentity();
	}
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** all values are 0 */
	public DhMat4f() { }
	
	public DhMat4f(DhApiMat4f sourceMatrix) { super(sourceMatrix); }
	
	/** Expects the values of the input buffer to be in row major order (AKA rows then columns) */
	public DhMat4f(FloatBuffer buffer) { this(buffer.array()); }
	/** Expects the values of the input array to be in row major order (AKA rows then columns) */
	public DhMat4f(float[] values) { super(values); }

	public void set(Matrix4fc sourceMatrix)
	{
		// JOML matricies are stored transposed vs DH's matricies
		this.m00 = sourceMatrix.m00();
		this.m01 = sourceMatrix.m10();
		this.m02 = sourceMatrix.m20();
		this.m03 = sourceMatrix.m30();
		
		this.m10 = sourceMatrix.m01();
		this.m11 = sourceMatrix.m11();
		this.m12 = sourceMatrix.m21();
		this.m13 = sourceMatrix.m31();
		
		this.m20 = sourceMatrix.m02();
		this.m21 = sourceMatrix.m12();
		this.m22 = sourceMatrix.m22();
		this.m23 = sourceMatrix.m32();
		
		this.m30 = sourceMatrix.m03();
		this.m31 = sourceMatrix.m13();
		this.m32 = sourceMatrix.m23();
		this.m33 = sourceMatrix.m33();
	}
	
	public static Matrix4f createJomlMatrix(DhApiMat4f matrix)
	{
		return new Matrix4f(
				matrix.m00, matrix.m10, matrix.m20, matrix.m30,
				matrix.m01, matrix.m11, matrix.m21, matrix.m31,
				matrix.m02, matrix.m12, matrix.m22, matrix.m32,
				matrix.m03, matrix.m13, matrix.m23, matrix.m33
		);
	}
	public Matrix4f createJomlMatrix()
	{
		return new Matrix4f(
				this.m00, this.m10, this.m20, this.m30,
				this.m01, this.m11, this.m21, this.m31,
				this.m02, this.m12, this.m22, this.m32,
				this.m03, this.m13, this.m23, this.m33
		);
	}
	
	
	
	//=========//
	// methods //
	//=========//

	public static DhMat4f createScaleMatrix(float x, float y, float z)
	{
		DhMat4f matrix = new DhMat4f();
		matrix.m00 = x;
		matrix.m11 = y;
		matrix.m22 = z;
		matrix.m33 = 1.0F;
		return matrix;
	}
	
	public static DhMat4f createTranslateMatrix(float x, float y, float z)
	{
		DhMat4f matrix = new DhMat4f();
		matrix.m00 = 1.0F;
		matrix.m11 = 1.0F;
		matrix.m22 = 1.0F;
		matrix.m33 = 1.0F;
		matrix.m03 = x;
		matrix.m13 = y;
		matrix.m23 = z;
		return matrix;
	}
	
	
	
	//===============//
	// Forge methods //
	//===============//

	public DhMat4f copy() { return new DhMat4f(this); }
	
	
	
}
