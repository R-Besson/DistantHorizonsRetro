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

package com.seibel.distanthorizons.common.wrappers.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Objects;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;

/**
 * 1.7.10 has no DimensionType class, dimensions are identified by WorldProvider.dimensionId.
 */
public class DimensionTypeWrapper implements IDimensionTypeWrapper
{
	private static final ConcurrentMap<String, DimensionTypeWrapper> DIMENSION_WRAPPER_BY_NAME = new ConcurrentHashMap<>();

	private final int dimensionType;

	// these variables aren't stored anywhere
	// so we have to guess based on the dimension ID
	private final boolean hasCeiling;
	private final boolean hasSkyLight;
	private final double coordinateScale;

	private final String name;



	//=============//
	// Constructor //
	//=============//

	public DimensionTypeWrapper(int dimensionType)
	{
		this.dimensionType = dimensionType;

		this.name = LegacyDimensionInfo.nameOf(dimensionType);
		this.hasCeiling = (dimensionType == LegacyDimensionInfo.NETHER);
		this.hasSkyLight = (dimensionType != LegacyDimensionInfo.NETHER);
		this.coordinateScale = (dimensionType == LegacyDimensionInfo.NETHER) ? 8.0 : 1.0;
	}

	public static DimensionTypeWrapper getDimensionTypeWrapper(int dimensionType)
	{
		String dimName = LegacyDimensionInfo.fullName(dimensionType);

		// check if the dimension has already been wrapped
		if (DIMENSION_WRAPPER_BY_NAME.containsKey(dimName)
			&& DIMENSION_WRAPPER_BY_NAME.get(dimName) != null)
		{
			return DIMENSION_WRAPPER_BY_NAME.get(dimName);
		}


		// create the missing wrapper
		DimensionTypeWrapper dimensionTypeWrapper = new DimensionTypeWrapper(dimensionType);

		DIMENSION_WRAPPER_BY_NAME.put(dimName, dimensionTypeWrapper);
		return dimensionTypeWrapper;
	}

	public static void clearMap() { DIMENSION_WRAPPER_BY_NAME.clear(); }



	//=================//
	// wrapper methods //
	//=================//

	@Override
	public String getName() { return this.name; }

	@Override
	public boolean hasCeiling()
	{
		return this.hasCeiling;
	}

	@Override
	public boolean hasSkyLight()
	{
		return this.hasSkyLight;
	}

	@Override
	public Object getWrappedMcObject() { return this.dimensionType; }

	@Override
	public boolean isTheEnd() { return this.getName().equalsIgnoreCase("the_end"); }

	@Override
	public double getCoordinateScale()
	{
		return this.coordinateScale;
	}



	//================//
	// base overrides //
	//================//

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj == null || obj.getClass() != DimensionTypeWrapper.class)
		{
			return false;
		}
		else
		{
			DimensionTypeWrapper other = (DimensionTypeWrapper) obj;
			return Objects.equals(other.getName(), this.getName());
		}
	}

	@Override
	public int hashCode() { return Objects.hash(this.getName()); }


}
