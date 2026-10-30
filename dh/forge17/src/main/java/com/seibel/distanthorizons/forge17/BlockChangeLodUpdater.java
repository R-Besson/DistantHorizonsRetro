package com.seibel.distanthorizons.forge17;

import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Updates the LODs of chunks whose blocks changed. <br>
 * MC tells every world listener when a block changes, including changes no event is fired for
 * (redstone lamps turning on, pistons, machines, a survival block break finishing).
 * Without this those LODs would keep the old blocks and light until the chunk is saved. <br><br>
 *
 * Only used on the server thread.
 */
public class BlockChangeLodUpdater implements IWorldAccess
{
	/**
	 * Machines mark their block for updates constantly to sync their data,
	 * a chunk is only rebuilt this often
	 */
	private static final long MIN_CHUNK_UPDATE_INTERVAL_MS = 3_000;
	/** spreads the work-out when a lot of chunks change at once (IE an explosion) */
	private static final int MAX_CHUNK_UPDATES_PER_TICK = 16;
	private static final int MAX_TRACKED_UPDATE_TIMES = 4096;

	private final WorldServer world;
	private final LinkedHashSet<Long> changedChunkKeys = new LinkedHashSet<>();
	private final HashMap<Long, Long> lastUpdateMsByChunkKey = new HashMap<>();



	public BlockChangeLodUpdater(WorldServer world) { this.world = world; }



	/** called at the end of every server tick */
	public void updateChangedChunks()
	{
		if (this.changedChunkKeys.isEmpty())
		{
			return;
		}

		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(this.world);
		long nowMs = System.currentTimeMillis();
		int updateCount = 0;

		Iterator<Long> iterator = this.changedChunkKeys.iterator();
		while (iterator.hasNext()
			&& updateCount < MAX_CHUNK_UPDATES_PER_TICK)
		{
			long chunkKey = iterator.next();
			int chunkX = (int) chunkKey;
			int chunkZ = (int) (chunkKey >> 32);

			Long lastUpdateMs = this.lastUpdateMsByChunkKey.get(chunkKey);
			if ((lastUpdateMs != null && nowMs - lastUpdateMs < MIN_CHUNK_UPDATE_INTERVAL_MS)
				// the queued update would miss this change, it's tried again later
				|| SharedApi.isChunkAtChunkPosAlreadyUpdating(levelWrapper, chunkX, chunkZ))
			{
				continue;
			}
			iterator.remove();

			if (!this.world.getChunkProvider().chunkExists(chunkX, chunkZ))
			{
				continue;
			}
			Chunk chunk = this.world.getChunkFromChunkCoords(chunkX, chunkZ);
			if (!ChunkWrapper.canSaveChunk(chunk))
			{
				continue;
			}

			SharedApi.INSTANCE.applyChunkUpdate(new ChunkWrapper(chunk, levelWrapper), levelWrapper, true);
			this.lastUpdateMsByChunkKey.put(chunkKey, nowMs);
			updateCount++;
		}

		if (this.lastUpdateMsByChunkKey.size() > MAX_TRACKED_UPDATE_TIMES)
		{
			this.lastUpdateMsByChunkKey.values().removeIf((updateMs) -> nowMs - updateMs >= MIN_CHUNK_UPDATE_INTERVAL_MS);
		}
	}



	//=================//
	// world listeners //
	//=================//
	//region

	@Override
	public void markBlockForUpdate(int x, int y, int z) { this.changedChunkKeys.add(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4)); }

	/**
	 * Called for every light value that changes,
	 * a light's glow reaches into the chunks next to it which have to be relit too
	 */
	@Override
	public void markBlockForRenderUpdate(int x, int y, int z) { this.changedChunkKeys.add(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4)); }
	@Override public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }
	@Override public void playSound(String soundName, double x, double y, double z, float volume, float pitch) { }
	@Override public void playSoundToNearExcept(EntityPlayer player, String soundName, double x, double y, double z, float volume, float pitch) { }
	@Override public void spawnParticle(String particleName, double x, double y, double z, double velocityX, double velocityY, double velocityZ) { }
	@Override public void onEntityCreate(Entity entity) { }
	@Override public void onEntityDestroy(Entity entity) { }
	@Override public void playRecord(String recordName, int x, int y, int z) { }
	@Override public void broadcastSound(int soundId, int x, int y, int z, int data) { }
	@Override public void playAuxSFX(EntityPlayer player, int sfxType, int x, int y, int z, int data) { }
	@Override public void destroyBlockPartially(int breakerId, int x, int y, int z, int progress) { }
	@Override public void onStaticEntitiesChanged() { }

	//endregion

}
