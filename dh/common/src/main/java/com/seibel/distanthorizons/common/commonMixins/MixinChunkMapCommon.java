package com.seibel.distanthorizons.common.commonMixins;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

public class MixinChunkMapCommon
{
	public static void onChunkSave(WorldServer level, Chunk chunk)
	{
		IServerLevelWrapper levelWrapper = ServerLevelWrapper.getWrapper(level);

		int chunkPosX = chunk.xPosition;
		int chunkPosZ = chunk.zPosition;

		// is this position already being updated?
		if (SharedApi.isChunkAtChunkPosAlreadyUpdating(levelWrapper, chunkPosX, chunkPosZ))
		{
			return;
		}



		// corrupt/incomplete chunk validation //

		// MC has a tendency to try saving incomplete or corrupted chunks (which show up as empty or black chunks)
		// this logic should prevent that from happening
		if (!ChunkWrapper.canSaveChunk(chunk))
		{
			return;
		}

		// Biomes aren't validated: the biome array is always allocated by the Chunk constructor and
		// ChunkWrapper reads biomes via World.getBiomeGenForCoords instead. Touching the array would
		// also crash under EndlessIDs, which replaces it with a short array.



		// submit the update event
		ServerApi.INSTANCE.serverChunkSaveEvent(
			new ChunkWrapper(chunk, levelWrapper),
			levelWrapper
		);
	}
}
