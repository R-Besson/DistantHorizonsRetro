package com.seibel.distanthorizons.core.render.QuadTree;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.function.LongConsumer;

/**
 * Holds section reload requests until they stop coming in. <br>
 * World gen finishes chunks one after another, each one requests a reload
 * of its section and the 4 next to it, so without this a section is rebuilt
 * and re-uploaded to the GPU many times while it fills in.
 */
public class SectionReloadBatcher
{
	/** a section is released once it hasn't been requested for this long */
	private static final long QUIET_TIME_MS = 750;
	/** and never held longer than this, so sections that keep changing still update */
	private static final long MAX_HOLD_TIME_MS = 3_000;

	private final Long2LongOpenHashMap lastRequestMsByPos = new Long2LongOpenHashMap();
	private final Long2LongOpenHashMap firstRequestMsByPos = new Long2LongOpenHashMap();



	public synchronized void request(long pos)
	{
		long now = System.currentTimeMillis();
		this.lastRequestMsByPos.put(pos, now);
		if (!this.firstRequestMsByPos.containsKey(pos))
		{
			this.firstRequestMsByPos.put(pos, now);
		}
	}

	/** passes every position that's ready to reload to {@code consumer} */
	public synchronized void releaseReady(LongConsumer consumer)
	{
		if (this.lastRequestMsByPos.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		ObjectIterator<Long2LongMap.Entry> iterator = this.lastRequestMsByPos.long2LongEntrySet().fastIterator();
		while (iterator.hasNext())
		{
			Long2LongMap.Entry entry = iterator.next();
			long pos = entry.getLongKey();
			if (now - entry.getLongValue() >= QUIET_TIME_MS
				|| now - this.firstRequestMsByPos.get(pos) >= MAX_HOLD_TIME_MS)
			{
				iterator.remove();
				this.firstRequestMsByPos.remove(pos);
				consumer.accept(pos);
			}
		}
	}

}
