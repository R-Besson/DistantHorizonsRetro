package com.seibel.distanthorizons.core.sql.repo;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers which positions are in a {@link FullDataSourceV2Repo} and their column generation steps,
 * so world gen doesn't run two SQL queries for every child position it checks. <br><br>
 *
 * Every write to a position must call {@link #invalidate(long)} after it commits.
 * Lookups take a {@link #writeCount(long)} before querying and only store the result
 * if nothing was written in the meantime, so a lookup that raced a write can't cache old data.
 */
public class FullDataLookupCache
{
	/** roughly 4,000 positions with generation steps, far more that are only known to be missing */
	private static final long MAX_BYTES = 16L * 1024 * 1024;
	/** map entry, boxed key and entry object */
	private static final int ENTRY_OVERHEAD_BYTES = 96;
	private static final int WRITE_COUNT_STRIPES = 256;

	private final LinkedHashMap<Long, Entry> entryByPos = new LinkedHashMap<>(1024, 0.75f, /*accessOrder*/ true);
	private long usedBytes = 0;
	/** striped by position so writes elsewhere don't stop lookups from being cached */
	private final long[] writeCountByStripe = new long[WRITE_COUNT_STRIPES];



	//=========//
	// lookups //
	//=========//
	//region

	/** @return null if this position isn't cached */
	@Nullable
	public synchronized Boolean exists(long pos)
	{
		Entry entry = this.entryByPos.get(pos);
		return (entry != null) ? entry.exists : null;
	}

	/**
	 * Copies the cached generation steps into {@code output} if the position is in the repo.
	 * @return false if this position's generation steps aren't cached
	 */
	public synchronized boolean getGenerationSteps(long pos, ByteArrayList output)
	{
		Entry entry = this.entryByPos.get(pos);
		if (entry == null)
		{
			return false;
		}

		if (!entry.exists)
		{
			// same as the repo: nothing is written for missing positions
			return true;
		}

		if (entry.generationSteps == null)
		{
			return false;
		}

		output.clear();
		output.addElements(0, entry.generationSteps);
		return true;
	}

	public synchronized long writeCount(long pos) { return this.writeCountByStripe[stripe(pos)]; }

	private static int stripe(long pos) { return (int) (Long.hashCode(pos * 0x9E3779B97F4A7C15L) & (WRITE_COUNT_STRIPES - 1)); }

	//endregion



	//=========//
	// storing //
	//=========//
	//region

	public void putMissing(long pos, long writeCountBeforeQuery) { this.put(pos, false, null, writeCountBeforeQuery); }
	public void putExists(long pos, long writeCountBeforeQuery) { this.put(pos, true, null, writeCountBeforeQuery); }
	public void putGenerationSteps(long pos, ByteArrayList generationSteps, long writeCountBeforeQuery)
	{ this.put(pos, true, generationSteps.toByteArray(), writeCountBeforeQuery); }

	private synchronized void put(long pos, boolean exists, @Nullable byte[] generationSteps, long writeCountBeforeQuery)
	{
		if (this.writeCountByStripe[stripe(pos)] != writeCountBeforeQuery)
		{
			// something was written while this was being queried, the result may be out of date
			return;
		}

		Entry existing = this.entryByPos.get(pos);
		if (existing != null
			&& existing.generationSteps != null
			&& generationSteps == null)
		{
			// don't replace known generation steps with a plain "exists"
			return;
		}

		Entry entry = new Entry(exists, generationSteps);
		Entry replaced = this.entryByPos.put(pos, entry);
		if (replaced != null)
		{
			this.usedBytes -= replaced.sizeInBytes();
		}
		this.usedBytes += entry.sizeInBytes();

		// evict the least recently used
		Iterator<Map.Entry<Long, Entry>> iterator = this.entryByPos.entrySet().iterator();
		while (this.usedBytes > MAX_BYTES && iterator.hasNext())
		{
			this.usedBytes -= iterator.next().getValue().sizeInBytes();
			iterator.remove();
		}
	}

	//endregion



	//==============//
	// invalidation //
	//==============//
	//region

	public synchronized void invalidate(long pos)
	{
		this.writeCountByStripe[stripe(pos)]++;
		Entry removed = this.entryByPos.remove(pos);
		if (removed != null)
		{
			this.usedBytes -= removed.sizeInBytes();
		}
	}

	public synchronized void invalidateAll()
	{
		for (int i = 0; i < WRITE_COUNT_STRIPES; i++)
		{
			this.writeCountByStripe[i]++;
		}
		this.entryByPos.clear();
		this.usedBytes = 0;
	}

	//endregion



	//================//
	// helper classes //
	//================//
	//region

	private static class Entry
	{
		final boolean exists;
		/** null if only the existence is known */
		@Nullable
		final byte[] generationSteps;

		Entry(boolean exists, @Nullable byte[] generationSteps)
		{
			this.exists = exists;
			this.generationSteps = generationSteps;
		}

		long sizeInBytes() { return ENTRY_OVERHEAD_BYTES + ((this.generationSteps != null) ? this.generationSteps.length : 0); }
	}

	//endregion



}
