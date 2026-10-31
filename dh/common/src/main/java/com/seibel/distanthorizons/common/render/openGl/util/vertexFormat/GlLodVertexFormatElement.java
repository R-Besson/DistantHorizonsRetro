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

package com.seibel.distanthorizons.common.render.openGl.util.vertexFormat;

/**
 * This object is used to build LodVertexFormats.
 * <p>
 * A (almost) exact copy of Minecraft's
 * VertexFormatElement class. <br>
 * A number of things were removed from the original
 * object since we didn't need them, specifically "usage".
 *
 * @author James Seibel
 * @version 11-13-2021
 */
public class GlLodVertexFormatElement
{
	private final GlLodVertexFormatElement.DataType dataType;
	/** James isn't sure what index is for */
	private final int index;
	private final int count;
	private final int byteSize;

	public GlLodVertexFormatElement(int newIndex, GlLodVertexFormatElement.DataType newType, int newCount)
	{
		this.dataType = newType;
		this.index = newIndex;
		this.count = newCount;
		this.byteSize = newType.getSize() * this.count;
	}

	public final int getByteSize()
	{
		return this.byteSize;
	}

	public enum DataType
	{
		UBYTE(1, "Unsigned Byte"),
		BYTE(1, "Byte"),
		USHORT(2, "Unsigned Short");

		private final int size;
		private final String name;

		DataType(int sizeInBytes, String newName)
		{
			this.size = sizeInBytes;
			this.name = newName;
		}

		public int getSize()
		{
			return this.size;
		}

		public String getName()
		{
			return this.name;
		}
	}

	@Override
	public int hashCode()
	{
		int i = this.dataType.hashCode();
		i = 31 * i + this.index;
		return 31 * i + this.count;
	}

	@Override
	public String toString()
	{
		return this.count + "," + this.dataType.getName();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj != null && this.getClass() == obj.getClass())
		{
			GlLodVertexFormatElement LodVertexFormatElement = (GlLodVertexFormatElement) obj;
			if (this.count != LodVertexFormatElement.count)
			{
				return false;
			}
			else if (this.index != LodVertexFormatElement.index)
			{
				return false;
			}
			else if (this.dataType != LodVertexFormatElement.dataType)
			{
				return false;
			}
			else
			{
				return false;
			}
		}
		else
		{
			return false;
		}
	}
}
