/*
 * Copyright LWJGL. All rights reserved. License terms: https://www.lwjgl.org/license
 */

package com.seibel.distanthorizons.lwjgl.lwjgl2.memory;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryUtilities.memAddress;
import static com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryUtilities.wrapBufferFloat;

/**
 * An off-heap memory stack, the parts of LWJGL 3's MemoryStack that DH needs on LWJGL 2.
 *
 * <p>
 * This class should be used in a thread-local manner for stack allocations.
 * </p>
 */
public class MemoryStack implements AutoCloseable
{

	private static final int DEFAULT_STACK_SIZE = 64 * 1024;
	private static final int DEFAULT_STACK_FRAMES = 8;

	private static final ThreadLocal<MemoryStack> TLS = ThreadLocal.withInitial(MemoryStack::create);

	/** keeps the backing memory from being garbage collected */
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private final ByteBuffer container;

	private final long address;

	private int pointer;

	private int[] frames;
	private int frameIndex;

	/**
	 * Creates a new {@code MemoryStack} backed by the specified memory buffer.
	 *
	 * <p>
	 * In the initial state, there is no active stack frame. The {@link #push} method must be used before any
	 * allocations.
	 * </p>
	 */
	private MemoryStack(ByteBuffer container)
	{
		this.container = container;
		this.address = memAddress(container);

		this.pointer = container.remaining();

		this.frames = new int[DEFAULT_STACK_FRAMES];
	}

	private static MemoryStack create()
	{
		return new MemoryStack(BufferUtils.createByteBuffer(DEFAULT_STACK_SIZE));
	}

	/**
	 * Stores the current stack pointer and pushes a new frame to the stack.
	 *
	 * <p>
	 * Pairs of push/pop calls may be nested. Care must be taken to:
	 * </p>
	 * <ul>
	 * <li>match every push with a pop</li>
	 * <li>not call pop before push has been called at least once</li>
	 * <li>not nest push calls to more than the maximum supported depth</li>
	 * </ul>
	 *
	 * @return this stack
	 */
	private MemoryStack push()
	{
		if (frameIndex == frames.length)
		{
			frameOverflow();
		}

		frames[frameIndex++] = pointer;
		return this;
	}

	private void frameOverflow()
	{
		System.err.println("[WARNING] Out of frame stack space (" + frames.length + ") in thread: " + Thread.currentThread());
		frames = Arrays.copyOf(frames, frames.length * 3 / 2);
	}

	/**
	 * Pops the current stack frame and moves the stack pointer to the end of the previous stack frame.
	 * Called automatically when the {@code MemoryStack} is used as a resource in a try-with-resources statement.
	 */
	@Override
	public void close()
	{
		pointer = frames[--frameIndex];
	}

	/**
	 * Allocates a block of {@code size} bytes of memory on the stack. The content of the newly allocated block of
	 * memory is not initialized, remaining with indeterminate values.
	 *
	 * @param alignment the required alignment
	 * @param size the allocation size
	 * @return the memory address on the stack for the requested allocation
	 */
	private long nmalloc(int alignment, int size)
	{
		// Align address to the specified alignment
		long address = (this.address + pointer - size) & ~Integer.toUnsignedLong(alignment - 1);

		pointer = (int) (address - this.address);
		if (pointer < 0)
		{
			throw new OutOfMemoryError("Out of stack space.");
		}

		return address;
	}

	/** Allocates {@code size} float values on the stack, the content isn't initialized. */
	public FloatBuffer mallocFloat(int size)
	{
		return wrapBufferFloat(nmalloc(4, size << 2), size);
	}

	/** Pushes a new frame onto the calling thread's stack. */
	public static MemoryStack stackPush()
	{
		return TLS.get().push();
	}
}
