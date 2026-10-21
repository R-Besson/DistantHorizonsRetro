package com.seibel.distanthorizons.core.util.objects.dataStreams;

import java.io.ByteArrayInputStream;

/**
 * {@link ByteArrayInputStream} without the locking. <br>
 * Its methods are synchronized, which costs an atomic operation per call since biased locking
 * was removed from Java. LOD data is decoded one byte at a time ({@link java.io.DataInputStream#readByte()}, varints),
 * so that was one of the hottest paths in DH. These streams are never shared between threads.
 */
public class UnsynchronizedByteArrayInputStream extends ByteArrayInputStream
{
	public UnsynchronizedByteArrayInputStream(byte[] buf) { super(buf); }
	public UnsynchronizedByteArrayInputStream(byte[] buf, int offset, int length) { super(buf, offset, length); }



	@Override
	public int read() { return (this.pos < this.count) ? (this.buf[this.pos++] & 0xFF) : -1; }

	@Override
	public int read(byte[] b, int off, int len)
	{
		if (off < 0 || len < 0 || len > b.length - off)
		{
			throw new IndexOutOfBoundsException();
		}

		if (this.pos >= this.count)
		{
			return -1;
		}

		int readCount = Math.min(len, this.count - this.pos);
		System.arraycopy(this.buf, this.pos, b, off, readCount);
		this.pos += readCount;
		return readCount;
	}

	@Override
	public long skip(long n)
	{
		long skipCount = Math.max(0, Math.min(n, this.count - this.pos));
		this.pos += (int) skipCount;
		return skipCount;
	}

	@Override
	public int available() { return this.count - this.pos; }

}
