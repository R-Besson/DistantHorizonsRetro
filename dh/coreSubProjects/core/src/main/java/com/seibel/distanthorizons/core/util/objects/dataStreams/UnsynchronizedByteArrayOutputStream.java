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

package com.seibel.distanthorizons.core.util.objects.dataStreams;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * {@link ByteArrayOutputStream} without the locking, see {@link UnsynchronizedByteArrayInputStream}. <br>
 * LOD data is encoded one byte at a time ({@link java.io.DataOutputStream#writeByte(int)}, varints).
 * These streams are never shared between threads.
 */
public class UnsynchronizedByteArrayOutputStream extends ByteArrayOutputStream
{
	public UnsynchronizedByteArrayOutputStream(int initialCapacity) { super(Math.max(initialCapacity, 32)); }



	@Override
	public void write(int b)
	{
		this.ensureCapacity(this.count + 1);
		this.buf[this.count++] = (byte) b;
	}

	@Override
	public void write(byte[] b, int off, int len)
	{
		if (off < 0 || len < 0 || len > b.length - off)
		{
			throw new IndexOutOfBoundsException();
		}

		this.ensureCapacity(this.count + len);
		System.arraycopy(b, off, this.buf, this.count, len);
		this.count += len;
	}

	/** {@link ByteArrayOutputStream}'s version is private */
	private void ensureCapacity(int minCapacity)
	{
		if (minCapacity > this.buf.length)
		{
			this.buf = Arrays.copyOf(this.buf, Math.max(minCapacity, this.buf.length * 2));
		}
	}

	/** appends the written bytes to {@code output} without the extra copy {@link #toByteArray()} makes */
	public void appendTo(ByteArrayList output) { output.addElements(output.size(), this.buf, 0, this.count); }

}
