package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.level.KeyedClientLevelManager;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.*;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

import com.seibel.distanthorizons.coreapi.util.ColorUtil;
import net.minecraft.client.Minecraft;
import com.seibel.distanthorizons.common.backports.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import com.seibel.distanthorizons.core.logging.DhLogger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;



public class ClientLevelWrapper implements IClientLevelWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	/**
	 * weak references are to prevent rare issues
	 * where, upon world closure, some levels aren't shutdown/removed properly
	 * and/or for servers were the level object isn't consistent
	 */
	private static final Map<WorldClient, WeakReference<ClientLevelWrapper>> LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());
	private static final KeyedClientLevelManager KEYED_CLIENT_LEVEL_MANAGER = (KeyedClientLevelManager) SingletonInjector.INSTANCE.get(IKeyedClientLevelManager.class);

	private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

	private static final ThreadLocal<DhBlockPosMutable> MUTABLE_BLOCK_POS_THREAD_LOCAL = ThreadLocal.withInitial(DhBlockPosMutable::new);

	private static final Timer CLIENT_CLEANUP_TIMER = TimerUtil.CreateTimer("ClientLevelTickCleanup");
	private static final TimerTask CLIENT_CLEANUP_TASK = TimerUtil.createTimerTask(ClientLevelWrapper::tickCleanup);

	/** how long in milliseconds can a level be unused before it's automatically unloaded */
	private static final long INACTIVE_TIME_BEFORE_UNLOADED_IN_MS = 30 * 1000;



	private final WorldClient level;
	private final ConcurrentHashMap<IBlockState, ClientBlockStateColorCache> blockColorCacheByBlockState = new ConcurrentHashMap<>();


	/** cached method reference to reduce GC overhead */
	private final Function<IBlockState, ClientBlockStateColorCache> createCachedBlockColorCacheFunc
			= (blockState) -> new ClientBlockStateColorCache(blockState, this);


	private boolean cloudColorFailLogged = false;

	private volatile IDhLevel dhLevel;
	private volatile long lastAccessTime = System.currentTimeMillis();



	//=============//
	// constructor //
	//=============//
	//region

	protected ClientLevelWrapper(WorldClient level) { this.level = level; }

	//endregion



	//======================//
	// inactivity unloading //
	//======================//
	//region

	@Override
	public synchronized void markAccessed() { this.lastAccessTime = System.currentTimeMillis(); }
	public synchronized long getLastAccessTime() { return this.lastAccessTime; }

	static
	{
		// fire 20 times per second (i.e. 50ms interval)
		CLIENT_CLEANUP_TIMER.scheduleAtFixedRate(CLIENT_CLEANUP_TASK, 0, 1000 / 20);
	}

	public static void tickCleanup()
	{
		WorldClient clientLevel = MINECRAFT.theWorld;

		if (clientLevel == null)
		{
			return;
		}



		long currentTime = System.currentTimeMillis();

		ArrayList<ClientLevelWrapper> levelsToUnload = new ArrayList<>();
		synchronized(LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL) // should only be run on one thread at a time, but just in case
		{
			for (WeakReference<ClientLevelWrapper> ref : LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.values())
			{
				ClientLevelWrapper levelWrapper = ref.get();
				if (levelWrapper != null
					&& levelWrapper.level != clientLevel)
				{
					// We use the synchronized getter to prevent race conditions with markAccessed()
					long inactiveTimeMs = currentTime - levelWrapper.getLastAccessTime();
					if (inactiveTimeMs > INACTIVE_TIME_BEFORE_UNLOADED_IN_MS)
					{
						levelsToUnload.add(levelWrapper);
					}
				}
			}
		}

		for (ClientLevelWrapper wrapper : levelsToUnload)
		{
			// Re-verify all conditions inside a synchronized block on the wrapper
			// to ensure atomicity with respect to markAccessed()
			synchronized(wrapper)
			{
				long inactiveTimeMs = currentTime - wrapper.getLastAccessTime();
				if (wrapper.level != clientLevel
					&& inactiveTimeMs > INACTIVE_TIME_BEFORE_UNLOADED_IN_MS)
				{
					LOGGER.debug("Unloading level [" + wrapper.getDhIdentifier() + "] due to inactivity");
					wrapper.tryUnloadFromWorld();
				}
			}
		}
	}

	//endregion



	//================//
	// level handling //
	//================//
	//region

	@Override
	public void setDhLevel(IDhLevel dhLevel) { this.dhLevel = dhLevel; }
	@Override
	public IDhLevel getDhLevel() { return this.dhLevel; }

	/**
	 * can be used when speed is important and the same level is likely to be passed in,
	 * IE rendering.
	 */
	@Nullable
	public static IClientLevelWrapper getWrapperIfDifferent(
		@Nullable IClientLevelWrapper levelWrapper,
		@NotNull WorldClient level)
	{
		if (KEYED_CLIENT_LEVEL_MANAGER.isEnabled())
		{
			IServerKeyedClientLevel keyedLevel = KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel(getWrapper(level, true));
			if (keyedLevel != levelWrapper)
			{
				return getWrapper(level);
			}
		}

		ClientLevelWrapper clientLevelWrapper = (ClientLevelWrapper)levelWrapper;
		if (clientLevelWrapper == null
			|| clientLevelWrapper.level != level)
		{
			return getWrapper(level);
		}

		return clientLevelWrapper;
	}

	@Nullable
	public static IClientLevelWrapper getWrapper(
		@NotNull WorldClient level)
	{ return getWrapper(level, false); }

	@Nullable
	public static IClientLevelWrapper getWrapper(
		@Nullable WorldClient level, boolean bypassLevelKeyManager)
	{
		if (!bypassLevelKeyManager)
		{
			if (level == null)
			{
				return null;
			}

			// used if the client is connected to a server that defines the currently loaded level
			IServerKeyedClientLevel overrideLevel = KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel(getWrapper(level, true));
			if (overrideLevel != null)
			{
				// if the currently loaded level wrapper doesn't match what's incoming, unload it
				WeakReference<ClientLevelWrapper> levelRef = LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.get(level);
				if (levelRef != null
					&& levelRef.get() != overrideLevel)
				{
					ClientLevelWrapper levelWrapper = levelRef.get();
					if (levelWrapper != null)
					{
						levelWrapper.tryUnloadFromWorld();
					}
					levelRef = null;
				}

				if (levelRef == null
					&& overrideLevel instanceof ClientLevelWrapper)
				{
					LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.put(level, new WeakReference<>((ClientLevelWrapper) overrideLevel));
				}

				return overrideLevel;
			}
		}


		WeakReference<ClientLevelWrapper> levelRef = LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.get(level);
		if (levelRef != null)
		{
			ClientLevelWrapper levelWrapper = levelRef.get();
			if (levelWrapper != null)
			{
				return levelWrapper;
			}
		}


		return LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.compute(level, (newLevel, newLevelRef) ->
		{
			if (newLevelRef != null)
			{
				ClientLevelWrapper oldLevelWrapper = newLevelRef.get();
				if (oldLevelWrapper != null)
				{
					return newLevelRef;
				}
			}

			return new WeakReference<>(new ClientLevelWrapper(newLevel));
		}).get();
	}

	@Nullable
	@Override
	public IServerLevelWrapper tryGetServerSideWrapper()
	{
		try
		{
			// this method only makes sense if we are running a single-player server
			if (MINECRAFT.getIntegratedServer() == null)
			{
				return null;
			}

			WorldServer[] serverLevels = MINECRAFT.getIntegratedServer().worldServers;

			// attempt to find the server level with the same dimension type
			// Note: this assumes only one level per dimension type, multiverse servers may not behave correctly
			ServerLevelWrapper foundLevelWrapper = null;
			for (WorldServer serverLevel : serverLevels)
			{
				if (serverLevel.provider.dimensionId == this.level.provider.dimensionId)
				{
					foundLevelWrapper = ServerLevelWrapper.getWrapper(serverLevel);
					break;
				}
			}

			return foundLevelWrapper;
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to get server side wrapper for client level: " + this.level);
			return null;
		}
	}

	//endregion



	//====================//
	// base level methods //
	//====================//
	//region

	@Override
	public int getBlockColor(
		DhBlockPos blockWorldPos, IBiomeWrapper biome, FullDataSourceV2 fullDataSource, IBlockStateWrapper blockWrapper,
		boolean allowApiOverride)
	{
		ClientBlockStateColorCache blockColorCache = this.blockColorCacheByBlockState.get(((BlockStateWrapper) blockWrapper).blockState);
		if (blockColorCache == null)
		{
			blockColorCache = this.blockColorCacheByBlockState.computeIfAbsent(
					((BlockStateWrapper) blockWrapper).blockState,
					this.createCachedBlockColorCacheFunc);
		}

		return blockColorCache.getColor((BiomeWrapper) biome, fullDataSource, blockWorldPos, allowApiOverride);
	}

	/**
	 * The dirt color is the same for every call, but is requested for
	 * the side of every grass quad, so it's cached. <br>
	 * {@link #DIRT_COLOR_NOT_SET} if it hasn't been calculated yet.
	 */
	private volatile long cachedDirtColor = DIRT_COLOR_NOT_SET;
	private static final long DIRT_COLOR_NOT_SET = Long.MIN_VALUE;

	@Override
	public int getDirtBlockColor()
	{
		long cachedColor = this.cachedDirtColor;
		if (cachedColor != DIRT_COLOR_NOT_SET)
		{
			return (int) cachedColor;
		}

		BlockStateWrapper dirtBlockWrapper = BlockStateWrapper.getDirtBlockStateWrapper(this);
		int color = this.getBlockColor(DhBlockPos.ZERO, BiomeWrapper.EMPTY_WRAPPER, null, dirtBlockWrapper);
		this.cachedDirtColor = color;
		return color;
	}

	@Override
	public void clearBlockColorCache()
	{
		this.blockColorCacheByBlockState.clear();
		this.cachedDirtColor = DIRT_COLOR_NOT_SET;
	}

	private IDimensionTypeWrapper dimensionTypeWrapper = null;
	@Override
	public IDimensionTypeWrapper getDimensionType()
	{
		// cached since this call is used in a high traffic location
		if (this.dimensionTypeWrapper != null)
		{
			return this.dimensionTypeWrapper;
		}

		this.dimensionTypeWrapper = DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId);
		return this.dimensionTypeWrapper;
	}

	private String dimensionName = null;
	@Override
	public String getDimensionName()
	{
		// cached since this allocates a new string each time
		// and this call is used in a high traffic location
		if (this.dimensionName != null)
		{
			return this.dimensionName;
		}

		this.dimensionName = LegacyDimensionInfo.fullName(this.level.provider.dimensionId);
		return this.dimensionName;
	}

	@Override
	public long getHashedSeed()
	{
		return this.level.getSeed();
	}

	@Override
	public String getDhIdentifier() { return this.getHashedSeedEncoded() + "@" + this.getDimensionName(); }

	@Override
	public EDhApiLevelType getLevelType() { return EDhApiLevelType.CLIENT_LEVEL; }

	public WorldClient getLevel() { return this.level; }

	@Override
	public boolean hasCeiling()
	{
		return CeilingDetection.hasCeiling(this.level.provider);
	}

	@Override
	public boolean hasSkyLight()
	{
		return !this.level.provider.hasNoSky;
	}

	@Override
	public int getMaxHeight()
	{
		return this.level.getHeight();
	}

	@Override
	public int getMinHeight()
	{
		return 0;
	}

	@Override
	public WorldClient getWrappedMcObject() { return this.level; }

	private void tryUnloadFromWorld()
	{
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null
			|| !world.unloadLevel(this))
		{
			this.onUnload();
		}
	}
	@Override
	public void onUnload()
	{
		LEVEL_WRAPPER_REF_BY_CLIENT_LEVEL.remove(this.level);
		this.dhLevel = null;
	}

	@Override
	public File getDhSaveFolder()
	{
		if (this.dhLevel == null)
		{
			return null;
		}

		return this.dhLevel.getSaveStructure().getSaveFolder(this);
	}

	@Override
	public DhApiResult<Color> getBlockColorPreApi(
		IDhApiBlockStateWrapper blockStateWrapper,
		IDhApiBiomeWrapper biomeWrapper,
		int blockWorldPosX, int blockWorldPosY, int blockWorldPosZ,
		IDhApiFullDataSource dataSource)
	{
		// cast to core objects //
		//region

		if(!(blockStateWrapper instanceof IBlockStateWrapper))
		{
			return DhApiResult.createFail("Unable to cast ["+blockStateWrapper.getClass()+"] to ["+IBlockStateWrapper.class+"]");
		}
		IBlockStateWrapper coreBlockStateWrapper = (IBlockStateWrapper)blockStateWrapper;

		if(!(biomeWrapper instanceof IBiomeWrapper))
		{
			return DhApiResult.createFail("Unable to cast ["+biomeWrapper.getClass()+"] to ["+IBiomeWrapper.class+"]");
		}
		IBiomeWrapper coreBiomeWrapper = (IBiomeWrapper) biomeWrapper;

		if(!(dataSource instanceof FullDataSourceV2))
		{
			return DhApiResult.createFail("Unable to cast ["+dataSource.getClass()+"] to ["+FullDataSourceV2.class+"]");
		}
		FullDataSourceV2 coreDataSource = (FullDataSourceV2) dataSource;

		//endregion



		// use a mutable thread local to reduce allocations slightly
		DhBlockPosMutable blockWorldPos = MUTABLE_BLOCK_POS_THREAD_LOCAL.get();
		blockWorldPos.setX(blockWorldPosX);
		blockWorldPos.setY(blockWorldPosY);
		blockWorldPos.setZ(blockWorldPosZ);

		int color = this.getBlockColor(blockWorldPos, coreBiomeWrapper, coreDataSource, coreBlockStateWrapper, false);
		return DhApiResult.createSuccess(ColorUtil.toColorObjARGB(color));
	}

	//endregion



	//===========//
	// rendering //
	//===========//
	//region

	@Override
	public IDhApiCustomRenderRegister getRenderRegister()
	{
		if (this.dhLevel == null)
		{
			return null;
		}

		return this.dhLevel.getGenericRenderer();
	}

	@Override
	public Color getCloudColor(float tickDelta)
	{

		Vec3 colorVec3 = null;
		try
		{
			colorVec3 = this.level.getCloudColour(tickDelta);

			return new Color((float)colorVec3.xCoord, (float)colorVec3.yCoord, (float)colorVec3.zCoord);
		}
		catch (Exception e)
		{
			// extra logging is due to some mods returning weird values, this way we can track down the issue better
			if (!this.cloudColorFailLogged)
			{
				this.cloudColorFailLogged = true;

				String colorString = "NULL";
				if (colorVec3 != null)
				{
					colorString = "r["+(float)colorVec3.xCoord+"] g["+(float)colorVec3.yCoord+"] b["+(float)colorVec3.zCoord+"]";
				}
				LOGGER.warn("Failed to get cloud color for ["+this.getDhIdentifier()+"]. vec3 ["+colorString+"], error: ["+e.getMessage()+"].", e);
			}

			// default to white if there's an issue
			return Color.WHITE;
		}
	}

	@Override
	public float getShade(EDhDirection lodDirection)
	{
		EDhApiLodShading lodShading = Config.Client.Advanced.Graphics.Quality.lodShading.get();
		switch (lodShading)
		{
			default:
			case AUTO:
				// the level doesn't have a getShade method, fall through to ENABLED
			case ENABLED:
				switch (lodDirection)
				{
					case DOWN:
						return 0.5F;
					default:
					case UP:
						return 1.0F;
					case NORTH:
					case SOUTH:
						return 0.8F;
					case WEST:
					case EAST:
						return 0.6F;
				}

			case DISABLED:
				return 1.0F;
		}
	}

	//endregion



	//================//
	// base overrides //
	//================//
	//region

	@Override
	public String toString()
	{
		if (this.level == null)
		{
			return "Wrapped{null}";
		}

		return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}";
	}

	//endregion



}
