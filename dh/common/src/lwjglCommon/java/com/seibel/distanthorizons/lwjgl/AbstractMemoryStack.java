package com.seibel.distanthorizons.lwjgl;

import java.nio.FloatBuffer;

/**
 * Thread-local stack allocator abstraction. Use with try-with-resources:
 * <pre>{@code
 * try (MemoryStack stack = LWJGL.stackPush()) {
 *     IntBuffer buf = stack.mallocFloat(1);
 *     // use buf...
 * } // automatically freed here
 * }</pre>
 */
public abstract class AbstractMemoryStack implements AutoCloseable
{

    public static AbstractMemoryStack stackPush() { return LWJGLServiceProvider.LWJGL.stackPush();  }



    @Override
    public abstract void close();



    // ===================== MALLOC OPERATIONS =====================
	//region
	
	public abstract FloatBuffer mallocFloat(int count);

	//endregion



}
