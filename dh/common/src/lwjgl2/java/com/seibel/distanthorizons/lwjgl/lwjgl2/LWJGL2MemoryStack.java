package com.seibel.distanthorizons.lwjgl.lwjgl2;

import com.seibel.distanthorizons.lwjgl.AbstractMemoryStack;

import java.nio.FloatBuffer;

public class LWJGL2MemoryStack extends AbstractMemoryStack
{
    private final com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryStack delegate;

    public LWJGL2MemoryStack(com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryStack delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ===================== MALLOC OPERATIONS =====================
	
	@Override
    public FloatBuffer mallocFloat(int count) {
        return delegate.mallocFloat(count);
    }
}
