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

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;

/**
 * This is closer to MC's implementation of a
 * 3 element float vector than a 3 element double
 * vector. Hopefully that shouldn't cause any issues.
 *
 * @author James Seibel
 * @version 11-18-2021
 */
public class DhVec3d extends DhApiVec3d
{
	//==============//
	// constructors //
	//==============//

	public DhVec3d() { }

	public DhVec3d(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public DhVec3d(DhApiVec3d that)
	{
		this.x = that.x;
		this.y = that.y;
		this.z = that.z;
	}



	//=========//
	// methods //
	//=========//

	public void set(DhVec3d that)
	{
		this.x = that.x;
		this.y = that.y;
		this.z = that.z;
	}

	public void set(double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public double getDistance(DhApiVec3d other) { return getDistance(this, other); }
	public static double getDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2)
			+ Math.pow(a.y - b.y, 2)
			+ Math.pow(a.z - b.z, 2));
	}

	/** Gets the distance between points A and B, ignoring Y height. */
	public static double getHorizontalDistance(DhApiVec3d a, DhApiVec3d b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2)
			+ Math.pow(a.z - b.z, 2));
	}

}
