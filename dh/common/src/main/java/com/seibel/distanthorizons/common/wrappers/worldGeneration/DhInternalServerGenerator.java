package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IHodgePodgeCommonAccessor;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.GlobalWorldGenParams;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateQueueManager;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.WorldChunkUpdateManager;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.ExceptionUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modLoader.IForgeMain;
import com.seibel.distanthorizons.coreapi.ModInfo;

import org.jetbrains.annotations.Nullable;
import com.seibel.distanthorizons.common.backports.ChunkPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.ForgeChunkManager;


import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import java.lang.reflect.Field;

public class DhInternalServerGenerator
{
	public static final DhLogger LOGGER = new DhLoggerBuilder()
			.name("LOD World Gen - Internal Server")
			.fileLevelConfig(Config.Common.Logging.logWorldGenEventToFile)
			.build();

	public static final DhLogger CHUNK_LOAD_LOGGER = new DhLoggerBuilder()
			.name("LOD Chunk Loading")
			.fileLevelConfig(Config.Common.Logging.logWorldGenChunkLoadEventToFile)
			.build();

	private static final IForgeMain FORGE_MAIN = SingletonInjector.INSTANCE.get(IForgeMain.class);

	private static final IHodgePodgeCommonAccessor HODGE_PODGE_ACCESSOR = ModAccessorInjector.INSTANCE.get(IHodgePodgeCommonAccessor.class);

	/**
	 * Used to revert the ignore logic in {@link SharedApi} so
	 * that a given chunk pos can be handled again.
	 * A timer is used so we don't have to inject into MC's code and it works sell enough
	 * most of the time.
	 * If a chunk does get through due the timeout not being long enough that isn't the end of the world.
	 */
	private static final int MS_TO_IGNORE_CHUNK_AFTER_COMPLETION = 5_000;

	/**
	 * How many times the chunk unload queue is pumped after a generation event finishes.
	 * Each pump frees a limited number of chunks so several are generally needed,
	 * but we want to avoid an endless loop.
	 */
	private static final int MAX_UNLOAD_DRAIN_ATTEMPT_COUNT = 100;


	private final GlobalWorldGenParams params;
	private final IDhServerLevel dhServerLevel;
	@Nullable
	private final ChunkUpdateQueueManager updateManager;
	private final Timer chunkSaveIgnoreTimer = TimerUtil.CreateTimer("ChunkSaveIgnoreTimer");
	private final ForgeChunkManager.Ticket dhServerGenTicket;

	/**
	 * A chunk's neighbors need to be loaded for it to populate.
	 * This map tracks how many in-flight generation events currently need each chunk pos loaded.
	 */
	private final ConcurrentHashMap<DhChunkPos, Integer> generationChunkRefCountMap = new ConcurrentHashMap<>();



	//=============//
	// constructor //
	//=============//

	public DhInternalServerGenerator(GlobalWorldGenParams params, IDhServerLevel dhServerLevel)
	{
		this.params = params;
		this.dhServerLevel = dhServerLevel;
		this.updateManager = WorldChunkUpdateManager.INSTANCE.getByLevelWrapper(this.dhServerLevel.getServerLevelWrapper());

		this.dhServerGenTicket = ForgeChunkManager.requestTicket(FORGE_MAIN, params.mcServerLevel, ForgeChunkManager.Type.NORMAL);
		increaseChunkLimit(this.dhServerGenTicket, 1000);
	}

	private static void increaseChunkLimit(ForgeChunkManager.Ticket ticket, int newMaxDepth)
	{
		try
		{
			Field maxDepthField = ticket.getClass().getDeclaredField("maxDepth");
			maxDepthField.setAccessible(true);
			maxDepthField.setInt(ticket, newMaxDepth);
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to increase Forge chunk ticket limit.", e);
		}
	}



	//===================================//
	// neighbor-chunk reference counting //
	//===================================//

	/**
	 * Marks the given pos as needed by one more generation event. <br>
	 * Must be called from the server thread.
	 *
	 * @return true if this was the first reference, meaning the chunk needs to be loaded.
	 */
	private boolean acquireChunkRef(DhChunkPos chunkPos)
	{
		Integer refCount = this.generationChunkRefCountMap.get(chunkPos);
		int newRefCount = (refCount == null) ? 1 : (refCount + 1);
		this.generationChunkRefCountMap.put(chunkPos, newRefCount);
		return newRefCount == 1;
	}

	/**
	 * Marks the given pos as no longer needed by one generation event. <br>
	 * Must be called from the server thread.
	 *
	 * @return true if this was the last reference, meaning the chunk can be released.
	 */
	private boolean releaseChunkRef(DhChunkPos chunkPos)
	{
		Integer refCount = this.generationChunkRefCountMap.get(chunkPos);
		if (refCount == null)
		{
			// could happen during shutdown
			CHUNK_LOAD_LOGGER.debug("Chunk ["+chunkPos+"] was released without being acquired.");
			return false;
		}

		if (refCount > 1)
		{
			this.generationChunkRefCountMap.put(chunkPos, refCount - 1);
			return false;
		}

		this.generationChunkRefCountMap.remove(chunkPos);
		return true;
	}

	/** @return true if no generation event currently needs the given pos loaded. */
	private boolean chunkRefCountIsZero(DhChunkPos chunkPos) { return !this.generationChunkRefCountMap.containsKey(chunkPos); }




	//============//
	// generation //
	//============//

	public void generateChunksViaInternalServer(ChunkGenEvent genEvent)
	{
		this.runValidation();

		ArrayList<CompletableFuture<ChunkWrapper>> getChunkFutureList = new ArrayList<>();

		try
		{
			//=====================//
			// create gen requests //
			//=====================//

			Iterator<ChunkPos> requestPosIterator = ChunkPosGenStream.getIterator(genEvent.minChunkPos.getX(), genEvent.minChunkPos.getZ(), genEvent.widthInChunks, 0);
			while (requestPosIterator.hasNext())
			{
				ChunkPos chunkPos = requestPosIterator.next();

				CompletableFuture<ChunkWrapper> requestChunkFuture =
					this.requestChunkFromServerAsync(chunkPos)
						// log errors if necessary
						.whenCompleteAsync(
							(chunk, throwable) ->
							{
								// unwrap the CompletionException if necessary
								Throwable actualThrowable = throwable;
								while (actualThrowable instanceof CompletionException)
								{
									actualThrowable = actualThrowable.getCause();
								}

								if (actualThrowable != null)
								{
									// some exceptions don't have a message
									String throwableMessage = (actualThrowable.getMessage() != null)
										? actualThrowable.getMessage()
										: actualThrowable.getClass().getSimpleName();

									// ignore expected shutdown exceptions
									boolean isShutdownException =
										ExceptionUtil.isShutdownException(actualThrowable)
										|| throwableMessage.contains("Unloaded chunk");
									if (!isShutdownException)
									{
										CHUNK_LOAD_LOGGER.warn("DistantHorizons: Couldn't load chunk [" + chunkPos + "] from server, error: [" + throwableMessage + "].", actualThrowable);
									}
								}
							});

				getChunkFutureList.add(requestChunkFuture);
			}



			//==============================//
			// wait for generation requests //
			//==============================//

			// Join-ing each thread will prevent DH from working on anything else
			// but will also prevent over-queuing world gen tasks.

			ArrayList<IChunkWrapper> chunkWrappers = new ArrayList<>();
			for (int i = 0; i < getChunkFutureList.size(); i++)
			{
				ChunkWrapper chunkWrapper = getChunkFutureList.get(i).join();
				if (chunkWrapper != null)
				{
					chunkWrapper.createDhHeightMaps();
					chunkWrappers.add(chunkWrapper);
				}
			}



			//==========================//
			// process generated chunks //
			//==========================//

			int maxSkyLight = this.dhServerLevel.getServerLevelWrapper().hasSkyLight() ? LodUtil.MAX_MC_LIGHT : LodUtil.MIN_MC_LIGHT;
			for (int i = 0; i < chunkWrappers.size(); i++)
			{
				ChunkWrapper chunkWrapper = (ChunkWrapper)chunkWrappers.get(i);

				// pre-generated chunks should have lighting but new ones won't
				if (!chunkWrapper.isDhBlockLightingCorrect())
				{
					DhLightingEngine.INSTANCE.bakeChunkBlockLighting(chunkWrapper, chunkWrappers, maxSkyLight);
				}

				this.dhServerLevel.updateBeaconBeamsForChunk(chunkWrapper, chunkWrappers);
				genEvent.resultConsumer.accept(chunkWrapper);
			}
		}
		finally
		{
			// If one request threw, the requests after it may still be queued.
			// Releases are essential tasks, so they'd run before those requests,
			// leaving their chunks force-loaded and ignored forever.
			// Wait for every request to finish (or fail) before releasing.
			for (int i = 0; i < getChunkFutureList.size(); i++)
			{
				try
				{
					getChunkFutureList.get(i).join();
				}
				catch (RuntimeException ignored)
				{
					// already logged by the request's whenCompleteAsync
				}
			}

			ArrayList<CompletableFuture<Void>> releaseFutures = new ArrayList<>();

			// release all chunks from the server to prevent out of memory issues.
			// each release also covers that chunk's neighbors,
			// which were acquired in requestChunkFromServerAsync.
			Iterator<ChunkPos> chunkPosIterator = ChunkPosGenStream.getIterator(genEvent.minChunkPos.getX(), genEvent.minChunkPos.getZ(), genEvent.widthInChunks, 0);
			while (chunkPosIterator.hasNext())
			{
				ChunkPos chunkPos = chunkPosIterator.next();
				releaseFutures.add(this.releaseChunkFromServerAsync(this.params.mcServerLevel, chunkPos));
			}

			// wait for all release futures to finish to prevent an issue where DH queues
			// tickets faster than MC can clear them out
			for (int i = 0; i < releaseFutures.size(); i++)
			{
				CompletableFuture<Void> releaseFuture = releaseFutures.get(i);
				this.joinServerCleanupFuture(releaseFuture);
			}

			// tick after all unloads are queued so MC actually frees the chunks
			CompletableFuture<Void> tickFuture = ServerThreadTaskHandler.INSTANCE.queueEssentialTask(() ->
			{
				try
				{
					ChunkProviderServer provider = (ChunkProviderServer) this.params.mcServerLevel.getChunkProvider();
					int remainingDrainAttemptCount = MAX_UNLOAD_DRAIN_ATTEMPT_COUNT;
					while (!provider.droppedChunksSet.isEmpty() && remainingDrainAttemptCount-- > 0)
					{
						provider.unloadQueuedChunks();
					}
				}
				catch (Exception e)
				{
					LOGGER.warn("Failed to drain internal server chunk unloads. Error: ["+e.getMessage()+"]", e);
				}

				return null;
			});
			this.joinServerCleanupFuture(tickFuture);
		}
	}
	private void joinServerCleanupFuture(CompletableFuture<Void> future)
	{
		try
		{
			future.join();
		}
		catch (RuntimeException e)
		{
			if (!ExceptionUtil.isShutdownException(e))
			{
				throw e;
			}
		}
	}
	private void runValidation()
	{
		// DH thread check
		if (!DhApi.isDhThread()
			&& ModInfo.IS_DEV_BUILD)
		{
			throw new IllegalStateException("Internal server generation should be called from one of DH's world gen thread. Current thread: ["+Thread.currentThread().getName()+"]");
		}
	}

	private void scheduleRemovePosToIgnore(DhChunkPos chunkPos)
	{
		this.chunkSaveIgnoreTimer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				// a new generation event may have picked this pos back up during the delay,
				// in which case its update events should stay ignored
				if (!DhInternalServerGenerator.this.chunkRefCountIsZero(chunkPos))
				{
					return;
				}

				if (DhInternalServerGenerator.this.updateManager != null)
				{
					DhInternalServerGenerator.this.updateManager.removePosToIgnore(chunkPos);
				}
			}
		}, MS_TO_IGNORE_CHUNK_AFTER_COMPLETION);
	}

	private CompletableFuture<ChunkWrapper> requestChunkFromServerAsync(ChunkPos chunkPos)
	{
		WorldServer level = this.params.mcServerLevel;

		// Ignore chunk update events for this position.
		// Would be added by the loop later, but also here instead of the main server thread.
		if (this.updateManager != null)
		{
			this.updateManager.addPosToIgnore(McObjectConverter.convert(chunkPos));
		}

		return ServerThreadTaskHandler.INSTANCE.queueTask(() ->
		{
			ChunkProviderServer provider = (ChunkProviderServer) level.getChunkProvider();

			// load neighbors first so the target chunk can fully populate.
			// adjacent generation events share these border chunks, so a reference count
			// is used to only load each chunk once and to track who still needs it.
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dz = -1; dz <= 1; dz++)
				{
					int neighborPosX = chunkPos.x + dx;
					int neighborPosZ = chunkPos.z + dz;

					if (!this.acquireChunkRef(new DhChunkPos(neighborPosX, neighborPosZ)))
					{
						// another generation event already has this chunk loaded
						continue;
					}

					if (this.updateManager != null)
					{
						this.updateManager.addPosToIgnore(new DhChunkPos(neighborPosX, neighborPosZ));
					}

					if (HODGE_PODGE_ACCESSOR != null)
					{
						HODGE_PODGE_ACCESSOR.preventChunkSimulation(level, neighborPosX, neighborPosZ);
					}

					ForgeChunkManager.forceChunk(this.dhServerGenTicket, new ChunkCoordIntPair(neighborPosX, neighborPosZ));

					// the target chunk itself is loaded below, at the requested generation step
					if (dx == 0 && dz == 0)
					{
						continue;
					}

					if (!provider.chunkExists(neighborPosX, neighborPosZ))
					{
						provider.loadChunk(neighborPosX, neighborPosZ);
					}
				}
			}

			// load the target chunk itself.
			// the wrapper is created here since it reads the chunk's biomes, which must happen on the server thread
			Chunk chunk = provider.loadChunk(chunkPos.x, chunkPos.z);
			return (chunk != null) ? new ChunkWrapper(chunk, this.dhServerLevel.getLevelWrapper()) : null;
		});
	}
	/**
	 * mitigates out of memory issues in the vanilla chunk system. <br>
	 * See: https://github.com/pop4959/Chunky/pull/383
	 */
	private CompletableFuture<Void> releaseChunkFromServerAsync(WorldServer level, ChunkPos chunkPos)
	{
		return ServerThreadTaskHandler.INSTANCE.queueEssentialTask(() ->
		{
			try
			{
				ChunkProviderServer provider = (ChunkProviderServer) level.getChunkProvider();

				// release the target chunk and the neighbors acquired in requestChunkFromServerAsync.
				// only the chunks that no other generation event needs anymore are actually unloaded.
				for (int dx = -1; dx <= 1; dx++)
				{
					for (int dz = -1; dz <= 1; dz++)
					{
						int neighborPosX = chunkPos.x + dx;
						int neighborPosZ = chunkPos.z + dz;
						DhChunkPos neighborDhPos = new DhChunkPos(neighborPosX, neighborPosZ);

						if (!this.releaseChunkRef(neighborDhPos))
						{
							// another generation event still needs this chunk loaded
							continue;
						}

						// the chunk is only queued for unload here, it's actually
						// freed by the drain once the whole generation event is done
						ForgeChunkManager.unforceChunk(this.dhServerGenTicket, new ChunkCoordIntPair(neighborPosX, neighborPosZ));

						if (HODGE_PODGE_ACCESSOR != null)
						{
							HODGE_PODGE_ACCESSOR.allowChunkSimulation(level, neighborPosX, neighborPosZ);
						}

						// don't unload chunks a player is watching, MC still owns those.
						// this mirrors the same guard vanilla uses in WorldServer.saveAllChunks().
						if (!level.getPlayerManager().func_152621_a(neighborPosX, neighborPosZ))
						{
							provider.dropChunk(neighborPosX, neighborPosZ);
						}

						this.scheduleRemovePosToIgnore(neighborDhPos);
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.warn("Failed to release chunk ["+chunkPos+"] back to internal server. Error: ["+e.getMessage()+"]", e);
			}

			return null;
		});
	}

}
