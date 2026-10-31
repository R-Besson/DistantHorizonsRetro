package com.seibel.distanthorizons.core.util;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

public class ListUtil
{
	/**
	 * Unlike {@link LongArrayList#ensureCapacity(int)} this method
	 * will populate the list with zeros up to the given size
	 * so get and set methods won't cause {@link IndexOutOfBoundsException}'s.
	 */
	public static void clearAndSetSize(LongArrayList arrayList, int size)
	{
		arrayList.clear();
		arrayList.size(size);
	}

	/** @see ListUtil#clearAndSetSize(LongArrayList, int) */
	public static void clearAndSetSize(ShortArrayList arrayList, int size)
	{
		arrayList.clear();
		arrayList.size(size);
	}

	/** @see ListUtil#clearAndSetSize(LongArrayList, int) */
	public static void clearAndSetSize(ByteArrayList arrayList, int size)
	{
		arrayList.clear();
		arrayList.size(size);
	}

	/** @see ListUtil#clearAndSetSize(LongArrayList, int) */
	public static void clearAndSetSize(CharArrayList arrayList, int size)
	{
		arrayList.clear();
		arrayList.size(size);
	}
}
