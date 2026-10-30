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

import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;

/**
 * A (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 3 element integer vector.
 *
 * @author James Seibel
 * @version 2022-11-19
 */
public class DhVec3i extends DhApiVec3i // extends the API object so it can be returned through the API
{
	// x,y,z variables are handled in the parent object



	public DhVec3i()
	{
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}

	public DhVec3i(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

}
