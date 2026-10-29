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

import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * A (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 3 element float vector.
 *
 * @author James Seibel
 * @version 11-11-2021
 */
public class DhVec3f extends DhApiVec3f
{
	//==============//
	// constructors //
	//==============//
	
	public DhVec3f() { this(0,0,0); }
	
	public DhVec3f(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public DhVec3f(DhApiVec3f pos)
	{
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}
	
	public DhVec3f(DhVec3d pos)
	{
		this.x = (float) pos.x;
		this.y = (float) pos.y;
		this.z = (float) pos.z;
	}
	
	
	
	
	//==============//
	// math methods //
	//==============//

	public float dotProduct(DhVec3f vector) { return this.x * vector.x + this.y * vector.y + this.z * vector.z; }
	
	/** @return true if normalization had to be done */
	public boolean normalize()
	{
		float squaredSum = this.x * this.x + this.y * this.y + this.z * this.z;
		if (squaredSum < 1.0E-5D)
		{
			return false;
		}
		else
		{
			float f1 = MathUtil.fastInvSqrt(squaredSum);
			this.x *= f1;
			this.y *= f1;
			this.z *= f1;
			return true;
		}
	}



	//==============//
	// misc methods //
	//==============//
	
	public void set(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public DhVec3f copy() { return new DhVec3f(this.x, this.y, this.z); }
	
}
