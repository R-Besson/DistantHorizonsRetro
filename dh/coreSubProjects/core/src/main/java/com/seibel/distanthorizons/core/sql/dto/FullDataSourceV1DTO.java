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

package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;

/**
 * Handles storing{@link FullDataSourceV1}'s in the database.
 */
public class FullDataSourceV1DTO implements IBaseDTO<Long>
{
	public long pos;
	public byte dataDetailLevel;

	public final byte[] dataArray;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV1DTO(long pos, byte dataDetailLevel, byte[] dataArray)
	{
		// the checksum, world gen step, data type and format version
		// are read from the database but not needed by the V1 migration
		this.pos = pos;
		this.dataDetailLevel = dataDetailLevel;

		this.dataArray = dataArray;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public Long getKey() { return this.pos; }
	
	@Override 
	public void close()
	{ /* no closing needed */ }
	
}
