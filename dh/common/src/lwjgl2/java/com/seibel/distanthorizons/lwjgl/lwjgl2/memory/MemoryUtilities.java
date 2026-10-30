/*
 * Copyright LWJGL. All rights reserved. License terms: https://www.lwjgl.org/license
 */
package com.seibel.distanthorizons.lwjgl.lwjgl2.memory;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.Sys;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.*;
import java.util.Objects;
import java.util.function.LongPredicate;

import static java.lang.Character.isHighSurrogate;
import static java.lang.Character.toCodePoint;

/**
 * The parts of LWJGL 3's MemoryUtil that DH needs on LWJGL 2,
 * native memory is allocated with {@link sun.misc.Unsafe}.
 */
public final class MemoryUtilities // full "utilities" name is to prevent conflicting with LWGJL's MemoryUtil class
{
    /** Alias for the null pointer address. */
    public static final long NULL = 0L;

    static final sun.misc.Unsafe UNSAFE;

    static final ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();

    static final Class<? extends ByteBuffer> BUFFER_BYTE;
    static final Class<? extends IntBuffer> BUFFER_INT;
    static final Class<? extends FloatBuffer> BUFFER_FLOAT;

    private static final long MARK;
    private static final long LIMIT;
    private static final long CAPACITY;

    private static final long ADDRESS;

    static
    {
        ByteBuffer bb = ByteBuffer.allocateDirect(0).order(NATIVE_ORDER);

        BUFFER_BYTE = bb.getClass();
        BUFFER_INT = bb.asIntBuffer().getClass();
        BUFFER_FLOAT = bb.asFloatBuffer().getClass();

        UNSAFE = getUnsafeInstance();

        try
        {
            MARK = getMarkOffset();
            LIMIT = getLimitOffset();
            CAPACITY = getCapacityOffset();

            ADDRESS = getAddressOffset();
        }
        catch (Throwable t)
        {
	        throw new UnsupportedOperationException(t);
        }
    }

    private MemoryUtilities() {}



    // --- [ memAlloc ] ---

    /**
     * Unsafe version of memAlloc that checks the returned pointer.
     *
     * @return a pointer to the memory block allocated by the function on success. This pointer will never be
     *         {@link #NULL}, even if {@code size} is zero.
     *
     * @throws OutOfMemoryError if the function failed to allocate the requested block of memory
     */
    private static long nmemAllocChecked(long size)
    {
	    long address = UNSAFE.allocateMemory(size != 0 ? size : 1L);
	    if (address == NULL)
	    {
		    throw new OutOfMemoryError();
	    }
	    return address;
    }

    private static long getAllocationSize(int elements, int elementShift) {
        return Integer.toUnsignedLong(elements) << elementShift;
    }

    /**
     * Allocates {@code size} int values, the content isn't initialized.
     * Must be freed with {@link #memFree}.
     */
    public static IntBuffer memAllocInt(int size) {
        return wrapBufferInt(nmemAllocChecked(getAllocationSize(size, 2)), size);
    }

    /**
     * Allocates {@code size} float values, the content isn't initialized.
     * Must be freed with {@link #memFree}.
     */
    public static FloatBuffer memAllocFloat(int size) {
        return wrapBufferFloat(nmemAllocChecked(getAllocationSize(size, 2)), size);
    }

    /** Frees a buffer allocated by this class, does nothing if {@code ptr} is null. */
    public static void memFree(@Nullable Buffer ptr)
    {
	    if (ptr != null)
	    {
		    UNSAFE.freeMemory(UNSAFE.getLong(ptr, ADDRESS));
	    }
    }



    // --- [ Buffer address ] ---

    /**
     * Returns the memory address at the current position of the specified buffer. This is effectively a pointer value
     * that can be used in native function calls.
     */
    public static long memAddress(ByteBuffer buffer) {
        return buffer.position() + UNSAFE.getLong(buffer, ADDRESS);
    }



    // --- [ UTF-8 ] ---

    /**
     * Returns a ByteBuffer containing the specified text UTF-8 encoded and optionally null-terminated.
     * Must be freed with {@link #memFree}.
     *
     * @throws BufferOverflowException if more than {@link Integer#MAX_VALUE} bytes are required to encode the text.
     */
    public static ByteBuffer memUTF8(CharSequence text, boolean nullTerminated) {
        int length = memLengthUTF8(text, nullTerminated);
        long target = nmemAllocChecked(length);
        encodeUTF8Unsafe(text, nullTerminated, target);
        return wrapBufferByte(target, length);
    }

    private static int encodeUTF8Unsafe(CharSequence text, boolean nullTerminated, long target) {
        int p = 0, i = 0, len = text.length();

        while (i < len) {
            char c = text.charAt(i++);
            if (c < 0x80) {
                p = write8(target, p, c);
            } else {
                int cp = c;
                if (c < 0x800) {
                    p = write8(target, p, 0xC0 | cp >> 6);
                } else {
                    if (!isHighSurrogate(c)) {
                        p = write8(target, p, 0xE0 | cp >> 12);
                    } else {
                        cp = toCodePoint(c, text.charAt(i++));

                        p = write8(target, p, 0xF0 | cp >> 18);
                        p = write8(target, p, 0x80 | cp >> 12 & 0x3F);
                    }
                    p = write8(target, p, 0x80 | cp >> 6 & 0x3F);
                }
                p = write8(target, p, 0x80 | cp & 0x3F);
            }
        }

        if (nullTerminated) {
            p = write8(target, p, 0);
        }

        return p;
    }

    private static int write8(long target, int offset, int value) {
        UNSAFE.putByte(null, target + Integer.toUnsignedLong(offset), (byte) value);
        return offset + 1;
    }

    /**
     * Returns the number of bytes required to encode the specified text in the UTF-8 encoding.
     *
     * @throws BufferOverflowException if more than {@link Integer#MAX_VALUE} bytes are required to encode the text.
     */
    private static int memLengthUTF8(CharSequence value, boolean nullTerminated) {
        int len = value.length();
        int bytes = len + (nullTerminated ? 1 : 0); // start with 1:1

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (c < 0x80) {
                // 1 input char -> 1 output byte
            } else {
                if (c < 0x800) {
                    // c <= 127: 0 (1 input char -> 1 output byte)
                    // c >= 128: 1 (1 input char -> 2 output bytes)
                    bytes += (0x7F - c) >>> 31;
                } else {
                    // non-high-surrogate: 1 input char -> 3 output bytes
                    // surrogate-pair: 2 input chars -> 4 output bytes
                    bytes += 2;
                    if (isHighSurrogate(c)) {
                        i++;
                    }
                }
                if (bytes < 0) {
                    throw new BufferOverflowException();
                }
            }
        }

        if (bytes < 0) {
            throw new BufferOverflowException();
        }

        return bytes;
    }



    // --- [ Unsafe setup ] ---

    static sun.misc.Unsafe getUnsafeInstance() {
        java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();

        /*
         * Different runtimes use different names for the Unsafe singleton, so we cannot use .getDeclaredField and we
         * scan instead. For example: Oracle: theUnsafe PERC : m_unsafe_instance Android: THE_ONE
         */
        for (java.lang.reflect.Field field : fields) {
            if (!field.getType().equals(sun.misc.Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers))) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (sun.misc.Unsafe) field.get(null);
            } catch (Exception e) {
                // inaccessible, fall through to the error below
            }
            break;
        }

        throw new UnsupportedOperationException("LWJGL requires sun.misc.Unsafe to be available.");
    }

    private static long getFieldOffset(Class<?> containerType, Class<?> fieldType, LongPredicate predicate) {
        Class<?> c = containerType;
        while (c != Object.class) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                if (!field.getType().isAssignableFrom(fieldType) || Modifier.isStatic(field.getModifiers())
                        || field.isSynthetic()) {
                    continue;
                }

                long offset = UNSAFE.objectFieldOffset(field);
                if (predicate.test(offset)) {
                    return offset;
                }
            }
            c = c.getSuperclass();
        }
        throw new UnsupportedOperationException("Failed to find field offset in class.");
    }

    private static long getFieldOffsetInt(Object container, int value) {
        return getFieldOffset(container.getClass(), int.class, offset -> UNSAFE.getInt(container, offset) == value);
    }

    private static long getAddressOffset() {
        long MAGIC_ADDRESS = 0xDEADBEEF8BADF00DL & (Sys.is64Bit() ? 0xFFFF_FFFF_FFFF_FFFFL : 0xFFFF_FFFFL);

        ByteBuffer bb = Objects.requireNonNull(newDirectByteBuffer(MAGIC_ADDRESS, 0));

        return getFieldOffset(bb.getClass(), long.class, offset -> UNSAFE.getLong(bb, offset) == MAGIC_ADDRESS);
    }

    private static final int MAGIC_CAPACITY = 0x0D15EA5E;
    private static final int MAGIC_POSITION = 0x00FACADE;

    private static long getMarkOffset() {
        ByteBuffer bb = Objects.requireNonNull(newDirectByteBuffer(1L, 0));
        return getFieldOffsetInt(bb, -1);
    }

    private static long getLimitOffset() {
        ByteBuffer bb = Objects.requireNonNull(newDirectByteBuffer(-1L, MAGIC_CAPACITY));
        bb.limit(MAGIC_POSITION);
        return getFieldOffsetInt(bb, MAGIC_POSITION);
    }

    private static long getCapacityOffset() {
        ByteBuffer bb = Objects.requireNonNull(newDirectByteBuffer(-1L, MAGIC_CAPACITY));
        bb.limit(0);
        return getFieldOffsetInt(bb, MAGIC_CAPACITY);
    }

    private static ByteBuffer newDirectByteBuffer(long address, int capacity) {
        try {
            @SuppressWarnings("unchecked")
            final Class<? extends ByteBuffer> dbb = (Class<? extends ByteBuffer>) Class
                    .forName("java.nio.DirectByteBuffer");
            final java.lang.reflect.Constructor<? extends ByteBuffer> newDbb =
                    dbb.getDeclaredConstructor(long.class, int.class);
            newDbb.setAccessible(true);
            return newDbb.newInstance(address, capacity);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }



    // --- [ Buffer wrapping ] ---

    static ByteBuffer wrapBufferByte(long address, int capacity) {
        ByteBuffer buffer;
        try {
            buffer = (ByteBuffer) UNSAFE.allocateInstance(BUFFER_BYTE);
        } catch (InstantiationException e) {
            throw new UnsupportedOperationException(e);
        }

        UNSAFE.putLong(buffer, ADDRESS, address);
        UNSAFE.putInt(buffer, MARK, -1);
        UNSAFE.putInt(buffer, LIMIT, capacity);
        UNSAFE.putInt(buffer, CAPACITY, capacity);

        return buffer.order(NATIVE_ORDER);
    }

    static IntBuffer wrapBufferInt(long address, int capacity) {
        IntBuffer buffer;
        try {
            buffer = (IntBuffer) UNSAFE.allocateInstance(BUFFER_INT);
        } catch (InstantiationException e) {
            throw new UnsupportedOperationException(e);
        }

        UNSAFE.putLong(buffer, ADDRESS, address);
        UNSAFE.putInt(buffer, MARK, -1);
        UNSAFE.putInt(buffer, LIMIT, capacity);
        UNSAFE.putInt(buffer, CAPACITY, capacity);

        return buffer;
    }

    static FloatBuffer wrapBufferFloat(long address, int capacity) {
        FloatBuffer buffer;
        try {
            buffer = (FloatBuffer) UNSAFE.allocateInstance(BUFFER_FLOAT);
        } catch (InstantiationException e) {
            throw new UnsupportedOperationException(e);
        }

        UNSAFE.putLong(buffer, ADDRESS, address);
        UNSAFE.putInt(buffer, MARK, -1);
        UNSAFE.putInt(buffer, LIMIT, capacity);
        UNSAFE.putInt(buffer, CAPACITY, capacity);

        return buffer;
    }

}
