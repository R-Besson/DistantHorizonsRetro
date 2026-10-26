package com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList;

import com.seibel.distanthorizons.core.util.ListUtil;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This keeps track of all the poolable
 * arrays that can be retrieved via the {@link PhantomArrayListPool}.
 * 
 * @see AbstractPhantomArrayList
 * @see PhantomArrayListPool
 */
public class PhantomArrayListCheckout implements AutoCloseable
{
	/** defines which pool the arrays should be returned too */
	@NotNull
	private final PhantomArrayListPool owningPool;

	/** 
	 * soft reference used by the {@link PhantomArrayListPool} so this checkout can be
	 * freed if there isn't enough memory.
	 */
	@NotNull
	public final SoftReference<PhantomArrayListCheckout> ownerSoftReference;
	
	/** Will be null if the parent pool doesn't want leak stack tracing */
	@Nullable
	public String allocationStackTrace = null;
	@Nullable
	public String lastSeenStackTrace = null;
	
	private final ArrayList<ByteArrayList> byteArrayLists = new ArrayList<>();
	private final ArrayList<ShortArrayList> shortArrayLists = new ArrayList<>();
	private final ArrayList<LongArrayList> longArrayLists = new ArrayList<>();
	private final ArrayList<CharArrayList> charArrayLists = new ArrayList<>();
	private final ArrayList<ByteBufferCheckoutWrapper> byteBufferWrapperList = new ArrayList<>();

	/** see {@link #getEmptyLongArrays}, kept since checkouts are reused */
	private LongArrayList[] longArrayListArray = null;


	
	//=============//
	// constructor //
	//=============//
	//region
	
	public PhantomArrayListCheckout(@NotNull PhantomArrayListPool owningPool)
	{
		this.owningPool = owningPool;
		this.ownerSoftReference = new SoftReference<>(this);
	}
	
	//endregion
	
	
	
	//=========//
	// setters //
	//=========//
	//region
	
	public void addByteArrayList(ByteArrayList list) 
	{
		this.owningPool.bytePoolStatTracker.totalArrayCountRef.getAndIncrement();
		this.byteArrayLists.add(list); 
	}
	public void addShortArrayList(ShortArrayList list) 
	{
		this.owningPool.shortPoolStatTracker.totalArrayCountRef.getAndIncrement();
		this.shortArrayLists.add(list); 
	}
	public void addLongArrayList(LongArrayList list) 
	{
		this.owningPool.longPoolStatTracker.totalArrayCountRef.getAndIncrement();
		this.longArrayLists.add(list); 
	}
	public void addCharArrayList(CharArrayList list) 
	{
		this.owningPool.charPoolStatTracker.totalArrayCountRef.getAndIncrement();
		this.charArrayLists.add(list); 
	}
	public void addByteBufferWrapper(ByteBufferCheckoutWrapper wrapper) 
	{
		this.owningPool.byteBufferPoolStatTracker.totalArrayCountRef.getAndIncrement();
		this.byteBufferWrapperList.add(wrapper); 
	}
	
	//endregion
	
	
	
	//=========//
	// getters //
	//=========//
	//region
	
	public int getByteArrayCount() { return this.byteArrayLists.size(); }
	public int getShortArrayCount() { return this.shortArrayLists.size(); }
	public int getLongArrayCount() { return this.longArrayLists.size(); }
	public int getCharArrayCount() { return this.charArrayLists.size(); }
	public int getByteBufferWrapperCount() { return this.byteBufferWrapperList.size(); }
	
	
	
	public ByteArrayList getByteArray(int index, int size)
	{
		ByteArrayList list = this.byteArrayLists.get(index);
		ListUtil.clearAndSetSize(list, size);
		return list;
	}
	public ShortArrayList getShortArray(int index, int size)
	{
		ShortArrayList list = this.shortArrayLists.get(index);
		ListUtil.clearAndSetSize(list, size);
		return list;
	}
	public LongArrayList getLongArray(int index, int size)
	{
		LongArrayList list = this.longArrayLists.get(index);
		ListUtil.clearAndSetSize(list, size);
		return list;
	}
	/**
	 * Like {@link #getLongArray}, but only the elements past the previous size are zeroed,
	 * the rest keep whatever the last holder left in them. <br>
	 * For holders that only use part of a large array and clear that part themselves.
	 */
	public LongArrayList getLongArrayWithoutClearing(int index, int size)
	{
		LongArrayList list = this.longArrayLists.get(index);
		list.size(size);
		return list;
	}
	/** @see #getLongArrayWithoutClearing */
	public ByteArrayList getByteArrayWithoutClearing(int index, int size)
	{
		ByteArrayList list = this.byteArrayLists.get(index);
		list.size(size);
		return list;
	}

	/**
	 * The first count long arrays, cleared, as an array. <br>
	 * The array is reused by this checkout, so holders that need many arrays at once
	 * (IE FullDataSourceV2's 4096 columns) don't allocate a new one each time.
	 * It's only valid until this checkout is closed.
	 */
	public LongArrayList[] getEmptyLongArrays(int count)
	{
		if (this.longArrayListArray == null
			|| this.longArrayListArray.length != count)
		{
			this.longArrayListArray = new LongArrayList[count];
		}

		for (int i = 0; i < count; i++)
		{
			this.longArrayListArray[i] = this.getLongArray(i, 0);
		}
		return this.longArrayListArray;
	}
	public CharArrayList getCharArray(int index, int size)
	{
		CharArrayList list = this.charArrayLists.get(index);
		ListUtil.clearAndSetSize(list, size);
		return list;
	}
	public ByteBuffer getByteBuffer(int index, int size)
	{
		ByteBufferCheckoutWrapper wrapper = this.byteBufferWrapperList.get(index);
		wrapper.clearAndSetSize(size);
		return wrapper.bufferSlice;
	}
	
	public ArrayList<ByteArrayList> getAllByteArrays() { return this.byteArrayLists; }
	public ArrayList<ShortArrayList> getAllShortArrays() { return this.shortArrayLists; }
	public ArrayList<LongArrayList> getAllLongArrays() { return this.longArrayLists; }
	public ArrayList<CharArrayList> getAllCharArrays() { return this.charArrayLists; }
	public ArrayList<ByteBufferCheckoutWrapper> getAllByteBufferWrappers() { return this.byteBufferWrapperList; }
	
	//endregion
	
	
	
	//===============//
	// leak tracking //
	//===============//
	//region
	
	public void onCheckout()
	{
		if (this.owningPool.logGarbageCollectedStacks)
		{
			this.allocationStackTrace = getStackTraceString();
		}
		
		this.recordLastSeen();
	}
	
	/** 
	 * Can be added to different methods to track
	 * how far an object gets through a given system. <br><br>
	 * 
	 * For example with DH's world gen Full data sources
	 * cross several different Future boundaries,
	 * which means we can easily lose track of
	 * where the objects end up or should be.
	 * By adding these record calls we can see where
	 * the data source was last seen before being garbage collected.
	 */
	public void recordLastSeen()
	{
		if (this.owningPool.logGarbageCollectedStacks)
		{
			this.lastSeenStackTrace = getStackTraceString();
		}
	}
	
	private static String getStackTraceString()
	{
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		StackTraceElement[] trimmedElements = Arrays.copyOfRange(stackTraceElements, 4, stackTraceElements.length);
		return StringUtil.join("\n", trimmedElements).intern();
	}
	
	//endregion
	
	
	
	//================//
	// base overrides //
	//================//
	//region
	
	@Override 
	public void close() { this.owningPool.returnCheckout(this); }
	
	//endregion
	
	
	
}
