/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2021  Tom Lee (TomTheFurry)
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.GlobalWorldGenParams;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.ExceptionUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.IChunkGenerator;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.seibel.distanthorizons.coreapi.ModInfo;



public final class DhChunkGenerator implements IChunkGenerator
{
	public static final DhLogger LOGGER = new DhLoggerBuilder()
		.name("LOD World Gen")
		.fileLevelConfig(Config.Common.Logging.logWorldGenEventToFile)
		.build();

	public static final long EXCEPTION_TIMER_RESET_TIME = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
	public static final int EXCEPTION_COUNTER_TRIGGER = 20;



	public final DhInternalServerGenerator internalServerGenerator;

	/**
	 * World gen tasks wait on the server thread without holding a world gen thread
	 * (see ChunkGenEvent's SERVER_GEN_WAIT_POOL), so more can be in flight to fill the server's world gen budget. <br>
	 * Each task force-loads up to 36 chunks (4x4 + neighbors), and Forge silently un-forces the oldest chunks
	 * once the 1000 chunk ticket limit set in DhInternalServerGenerator is passed,
	 * so the total number of tasks is capped to stay under that.
	 */
	@Override
	public int getInProgressTasksPerThread()
	{
		// +1 since the queue allows one task over its limit
		int maxTaskCount = (FORGE_TICKET_CHUNK_LIMIT / MAX_FORCED_CHUNKS_PER_TASK) - 1;
		int threadCount = Math.max(Config.Common.MultiThreading.numberOfThreads.get(), 1);
		return Math.max(1, Math.min(MAX_IN_PROGRESS_TASKS_PER_THREAD, maxTaskCount / threadCount));
	}
	private static final int MAX_IN_PROGRESS_TASKS_PER_THREAD = 3;
	private static final int FORGE_TICKET_CHUNK_LIMIT = 1000;
	private static final int MAX_FORCED_CHUNKS_PER_TASK = 6 * 6;



	public final LinkedBlockingQueue<ChunkGenEvent> generationEventQueue = new LinkedBlockingQueue<>();


	public int unknownExceptionCount = 0;
	public long lastExceptionTriggerTime = 0;



	//==============//
	// constructors //
	//==============//

	public DhChunkGenerator(IDhServerLevel dhServerLevel)
	{
		GlobalWorldGenParams globalParams = new GlobalWorldGenParams(dhServerLevel);
		this.internalServerGenerator = new DhInternalServerGenerator(globalParams, dhServerLevel);
	}



	//=================//
	// synchronization //
	//=================//

	public void updateAllFutures()
	{
		if (this.unknownExceptionCount > 0)
		{
			if (System.nanoTime() - this.lastExceptionTriggerTime >= EXCEPTION_TIMER_RESET_TIME)
			{
				this.unknownExceptionCount = 0;
			}
		}


		// Update all current out standing jobs
		Iterator<ChunkGenEvent> iter = this.generationEventQueue.iterator();
		while (iter.hasNext())
		{
			ChunkGenEvent event = iter.next();
			if (event.future.isDone())
			{
				if (event.future.isCompletedExceptionally() && !event.future.isCancelled())
				{
					try
					{
						event.future.get(); // Should throw exception
						LodUtil.assertNotReach("Exceptionally completed world gen Future should have thrown an exception.");
					}
					catch (Exception e)
					{
						// get() wraps the cause in an ExecutionException
						if (ExceptionUtil.isShutdownException(e)
							|| (e.getCause() != null && ExceptionUtil.isShutdownException(e.getCause())))
						{
							// expected when tasks are interrupted or cancelled, not a generator problem
							iter.remove();
							continue;
						}

						this.unknownExceptionCount++;
						this.lastExceptionTriggerTime = System.nanoTime();
						LOGGER.error("Batching World Generator event ["+event+"] threw an exception: "+e.getMessage(), e);
					}
				}

				iter.remove();
			}
		}

		if (this.unknownExceptionCount > EXCEPTION_COUNTER_TRIGGER)
		{
			LOGGER.error("Too many exceptions in Batching World Generator! Disabling the generator.");
			this.unknownExceptionCount = 0;
			Config.Common.WorldGenerator.generatorPlan.set(EDhApiGeneratorPlan.DISABLED);
		}
	}



	//==================//
	// world generation //
	//==================//

	/** @throws RejectedExecutionException if the given {@link Executor} is cancelled. */
	public void generateChunks(ChunkGenEvent genEvent) throws RejectedExecutionException
	{
		// Minecraft's generation events expect odd chunk width areas (3x3, 7x7, or 11x11),
		// but DH submits square generation events (4x4).
		// We handle this later, although that handling would need to change if the gen size ever changes.
		LodUtil.assertTrue(genEvent.widthInChunks % 2 == 0, "Generation events are expected to be an evan number of chunks wide.");

		if (!DhApi.isDhThread()
			&& ModInfo.IS_DEV_BUILD)
		{
			throw new IllegalStateException("Batch world generation should be called from one of DH's world gen thread. Current thread: ["+Thread.currentThread().getName()+"]");
		}

		// MC can only run world gen on the main thread
		this.internalServerGenerator.generateChunksViaInternalServer(genEvent);
	}



	// queue task //

	@Override
	public CompletableFuture<Void> queueGenEvent(
		int chunkPosMinX, int chunkPosMinZ, int chunkWidthCount,
		EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetStep,
		ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer)
	{
		ChunkGenEvent genEvent = ChunkGenEvent.start(
				new DhChunkPos(chunkPosMinX, chunkPosMinZ), chunkWidthCount, this,
				generatorMode, targetStep, resultConsumer,
				worldGeneratorThreadPool);
		this.generationEventQueue.add(genEvent);
		return genEvent.future;
	}



	//================//
	// base overrides //
	//================//
	//region

	@Override
	public void close()
	{
		LOGGER.info("Closing [" + DhChunkGenerator.class.getSimpleName() + "]");


		// cancel in-progress tasks
		Iterator<ChunkGenEvent> genEventIter = this.generationEventQueue.iterator();
		while (genEventIter.hasNext())
		{
			ChunkGenEvent event = genEventIter.next();
			event.future.cancel(true);
			genEventIter.remove();
		}
	}

	//endregion



}
