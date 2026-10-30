package com.seibel.distanthorizons.lwjgl.lwjgl3;

import com.seibel.distanthorizons.lwjgl.AbstractMemoryStack;

import java.nio.FloatBuffer;

/**
 * LWJGL3 implementation of MemoryStack.
 * Wraps {@code org.lwjgl.system.MemoryStack}.
 */
public class LWJGL3MemoryStack extends AbstractMemoryStack
{
	private final org.lwjgl.system.MemoryStack delegate;

	public LWJGL3MemoryStack(org.lwjgl.system.MemoryStack delegate)
	{
		this.delegate = delegate;
	}

	@Override
	public void close()
	{
		delegate.close();
	}

	// ===================== MALLOC OPERATIONS =====================

	@Override
	public FloatBuffer mallocFloat(int count)
	{
		return delegate.mallocFloat(count);
	}

}
