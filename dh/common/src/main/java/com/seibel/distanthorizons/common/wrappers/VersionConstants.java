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

import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingEngine;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;

public class VersionConstants implements IVersionConstants
{
	public static final VersionConstants INSTANCE = new VersionConstants();
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	private VersionConstants() { }
	
	//endregion
	
	
	
	//=========//
	// methods //
	//=========//
	//region
	
	@Override
	public String getMinecraftVersion()
	{
		// hard-coded so dedicated servers don't need to load client classes to read it
		return "1.7.10";
	}

	@Override
	public EDhApiRenderingEngine getDefaultRenderingEngine()
	{
		return EDhApiRenderingEngine.OPEN_GL;
	}
	
	//endregion
	
	
	
}