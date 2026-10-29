package com.seibel.distanthorizons.common.render.openGl.terrain;

import com.seibel.distanthorizons.common.wrappers.modAccessor.CubicChunksCompat;
import com.seibel.distanthorizons.common.wrappers.modAccessor.ICubicChunksCommonAccessor;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * Which chunks around the camera MC has drawn vanilla terrain for. <br>
 * LODs are always drawn in the chunks MC hasn't built yet (no holes),
 * in the drawn ones they're clipped and dithered by distance like normal
 * so they blend into the vanilla terrain at the edge of the render distance.
 */
public class VanillaChunkMask
{
	public static final VanillaChunkMask INSTANCE = new VanillaChunkMask();
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	/** texture unit the mask is bound to, the lightmap and block atlas use 0 and 1 */
	public static final int GL_BOUND_INDEX = 2;

	/** how often the mask is rebuilt while the camera stays in the same chunk */
	private static final long UPDATE_INTERVAL_MS = 50;
	/** how long after the first update the mask is logged, so MC has had time to build the nearby chunks */
	private static final long DIAGNOSTIC_LOG_DELAY_MS = 10_000;
	/** 1.7.10 chunks are 16 sections tall */
	private static final int SECTION_COUNT = 16;

	/** chunks from the camera's chunk to the mask's edge, the render distance plus one */
	private int radius = 0;
	private int cameraChunkX = 0;
	private int cameraChunkZ = 0;
	/** 1 if MC drew this chunk, indexed by ((z + radius) * width) + (x + radius) */
	private byte[] renderedByChunk = new byte[0];
	private ByteBuffer uploadBuffer = null;
	/** horizontal distance from the camera to the nearest chunk MC can't draw (unloaded or past the render distance) */
	private float vanillaTerrainEndDistance = 0;
	/** if the chunk's terrain is loaded, one chunk wider than the mask on each side */
	private boolean[] terrainLoadedByChunk = new boolean[0];
	private long lastUpdateMs = 0;
	private long firstUpdateMs = 0;
	private boolean diagnosticLogged = false;

	private int textureId = 0;



	//========//
	// update //
	//========//
	//region

	private VanillaChunkMask() { }

	/** called on the render thread before LODs are drawn */
	public void update(DhVec3d cameraPos)
	{
		Minecraft mc = Minecraft.getMinecraft();
		WorldClient level = mc.theWorld;
		if (level == null)
		{
			return;
		}

		int renderDistance = mc.gameSettings.renderDistanceChunks;
		int newRadius = renderDistance + 1;
		int newCameraChunkX = (int) Math.floor(cameraPos.x / 16.0);
		int newCameraChunkZ = (int) Math.floor(cameraPos.z / 16.0);

		long nowMs = System.currentTimeMillis();
		boolean moved = (newRadius != this.radius || newCameraChunkX != this.cameraChunkX || newCameraChunkZ != this.cameraChunkZ);
		if (!moved && nowMs - this.lastUpdateMs < UPDATE_INTERVAL_MS)
		{
			return;
		}
		this.lastUpdateMs = nowMs;

		int width = (newRadius * 2) + 1;
		if (this.renderedByChunk.length != width * width)
		{
			this.renderedByChunk = new byte[width * width];
			this.uploadBuffer = ByteBuffer.allocateDirect(width * width).order(ByteOrder.nativeOrder());
		}
		this.radius = newRadius;
		this.cameraChunkX = newCameraChunkX;
		this.cameraChunkZ = newCameraChunkZ;

		IAngelicaAccessor angelicaAccessor = ModAccessorInjector.INSTANCE.get(IAngelicaAccessor.class);
		ICubicChunksCommonAccessor cubicChunks = CubicChunksCompat.getAccessor(level);
		// which chunks have their terrain loaded, one chunk past the mask so every chunk in it has all its neighbors
		int loadedRadius = newRadius + 1;
		int loadedWidth = (loadedRadius * 2) + 1;
		if (this.terrainLoadedByChunk.length != loadedWidth * loadedWidth)
		{
			this.terrainLoadedByChunk = new boolean[loadedWidth * loadedWidth];
		}
		for (int offsetZ = -loadedRadius; offsetZ <= loadedRadius; offsetZ++)
		{
			for (int offsetX = -loadedRadius; offsetX <= loadedRadius; offsetX++)
			{
				this.terrainLoadedByChunk[((offsetZ + loadedRadius) * loadedWidth) + (offsetX + loadedRadius)]
					= isTerrainLoaded(cubicChunks, level.getChunkFromChunkCoords(newCameraChunkX + offsetX, newCameraChunkZ + offsetZ));
			}
		}

		double nearestUnloadedDistanceSquared = Double.MAX_VALUE;
		for (int offsetZ = -newRadius; offsetZ <= newRadius; offsetZ++)
		{
			for (int offsetX = -newRadius; offsetX <= newRadius; offsetX++)
			{
				int chunkX = newCameraChunkX + offsetX;
				int chunkZ = newCameraChunkZ + offsetZ;

				// MC never draws past the render distance, Cubic Chunks only draws columns with their surface cubes loaded.
				// Angelica only builds a chunk once the chunks around it are loaded too,
				// so the last ring of loaded chunks isn't drawn. Vanilla MC loads a chunk past the render distance,
				// Cubic Chunks doesn't, so with it vanilla terrain ends a chunk before the loaded chunks do
				boolean loaded = Math.abs(offsetX) <= renderDistance
					&& Math.abs(offsetZ) <= renderDistance
					&& this.isTerrainLoaded(offsetX, offsetZ, loadedRadius, loadedWidth)
					&& (angelicaAccessor == null || this.areNeighborsLoaded(offsetX, offsetZ, loadedRadius, loadedWidth));
				if (!loaded)
				{
					double distanceX = Math.max(0.0, Math.max((chunkX * 16.0) - cameraPos.x, cameraPos.x - ((chunkX * 16.0) + 16.0)));
					double distanceZ = Math.max(0.0, Math.max((chunkZ * 16.0) - cameraPos.z, cameraPos.z - ((chunkZ * 16.0) + 16.0)));
					nearestUnloadedDistanceSquared = Math.min(nearestUnloadedDistanceSquared, (distanceX * distanceX) + (distanceZ * distanceZ));
				}

				boolean rendered;
				Boolean angelicaBuilt = (angelicaAccessor != null) ? isChunkBuilt(angelicaAccessor, cubicChunks, level, chunkX, chunkZ) : null;
				if (angelicaBuilt != null)
				{
					// Angelica only builds sections inside the render distance
					rendered = angelicaBuilt;
				}
				else
				{
					// vanilla draws every loaded chunk in a square of the render distance
					rendered = Math.abs(offsetX) <= renderDistance
						&& Math.abs(offsetZ) <= renderDistance
						&& !level.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty();
				}

				this.renderedByChunk[((offsetZ + newRadius) * width) + (offsetX + newRadius)] = (byte) (rendered ? 1 : 0);
			}
		}

		this.vanillaTerrainEndDistance = (float) Math.sqrt(nearestUnloadedDistanceSquared);
		RenderUtil.setVanillaTerrainEndDistanceInBlocks(this.vanillaTerrainEndDistance);

		this.upload(width);
		this.tryLogDiagnostic(nowMs, renderDistance, angelicaAccessor != null);
	}
	private boolean isTerrainLoaded(int offsetX, int offsetZ, int loadedRadius, int loadedWidth)
	{ return this.terrainLoadedByChunk[((offsetZ + loadedRadius) * loadedWidth) + (offsetX + loadedRadius)]; }
	private boolean areNeighborsLoaded(int offsetX, int offsetZ, int loadedRadius, int loadedWidth)
	{
		for (int neighborZ = offsetZ - 1; neighborZ <= offsetZ + 1; neighborZ++)
		{
			for (int neighborX = offsetX - 1; neighborX <= offsetX + 1; neighborX++)
			{
				if (!this.isTerrainLoaded(neighborX, neighborZ, loadedRadius, loadedWidth))
				{
					return false;
				}
			}
		}
		return true;
	}
	/** @return true if MC has the terrain to draw this chunk, whether or not it's been built yet */
	private static boolean isTerrainLoaded(@Nullable ICubicChunksCommonAccessor cubicChunks, Chunk chunk)
	{
		if (chunk.isEmpty())
		{
			return false;
		}
		return cubicChunks == null || cubicChunks.isColumnReady(chunk);
	}
	private static Boolean isChunkBuilt(IAngelicaAccessor angelicaAccessor, @Nullable ICubicChunksCommonAccessor cubicChunks, WorldClient level, int chunkX, int chunkZ)
	{
		// Only the sections holding the chunk's top blocks are checked.
		// Nothing below them can be: Angelica only builds sections the camera can see,
		// so sections under the ground or under water never count as built.
		Chunk chunk = level.getChunkFromChunkCoords(chunkX, chunkZ);
		if (chunk.isEmpty())
		{
			return false;
		}
		// Cubic Chunks sections are cubes, its height map covers the whole world
		int[] surfaceSectionRange = (cubicChunks != null) ? cubicChunks.getSurfaceCubeRange(chunk) : getSurfaceSectionRange(chunk);
		if (surfaceSectionRange == null)
		{
			return false;
		}
		return angelicaAccessor.isChunkBuilt(chunkX, chunkZ, surfaceSectionRange[0], surfaceSectionRange[1]);
	}
	/** @return the lowest and highest section Y holding the chunk's top blocks, null if the chunk has no blocks */
	@Nullable
	private static int[] getSurfaceSectionRange(Chunk chunk)
	{
		int lowestTopY = Integer.MAX_VALUE;
		int highestTopY = Integer.MIN_VALUE;
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				// the height map is one above the highest block that blocks light, water included
				int topY = chunk.getHeightValue(x, z) - 1;
				lowestTopY = Math.min(lowestTopY, topY);
				highestTopY = Math.max(highestTopY, topY);
			}
		}
		if (highestTopY < 0)
		{
			return null;
		}
		lowestTopY = Math.max(lowestTopY, 0);
		return new int[] { lowestTopY >> 4, Math.min(highestTopY >> 4, SECTION_COUNT - 1) };
	}
	/** logged once per game, used to check the mask matches what MC actually drew */
	private void tryLogDiagnostic(long nowMs, int renderDistance, boolean angelicaPresent)
	{
		if (this.diagnosticLogged)
		{
			return;
		}
		if (this.firstUpdateMs == 0)
		{
			this.firstUpdateMs = nowMs;
			return;
		}
		if (nowMs - this.firstUpdateMs < DIAGNOSTIC_LOG_DELAY_MS)
		{
			return;
		}
		this.diagnosticLogged = true;
		
		int renderedCount = 0;
		for (byte rendered : this.renderedByChunk)
		{
			renderedCount += rendered;
		}
		int inRenderDistanceCount = ((renderDistance * 2) + 1) * ((renderDistance * 2) + 1);
		LOGGER.info("LODs are skipped in [" + renderedCount + "] chunks MC drew, out of [" + inRenderDistanceCount + "] chunks in the render distance. Source: [" + (angelicaPresent ? "Angelica built sections" : "loaded chunks") + "].");
	}
	private void upload(int width)
	{
		this.uploadBuffer.clear();
		this.uploadBuffer.put(this.renderedByChunk);
		this.uploadBuffer.flip();

		if (this.textureId == 0)
		{
			this.textureId = LWJGL.glGenTextures();
		}

		LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + GL_BOUND_INDEX);
		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
		LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		LWJGL.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		// rows are 1 byte per texel and an odd width
		int previousAlignment = LWJGL.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
		LWJGL.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		LWJGL.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, width, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, this.uploadBuffer);
		LWJGL.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);

		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0);
	}

	//endregion



	//=========//
	// binding //
	//=========//
	//region

	public void bind()
	{
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + GL_BOUND_INDEX);
		LWJGL.glBindSampler(GL_BOUND_INDEX, 0);
		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0);
	}

	public void unbind()
	{
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + GL_BOUND_INDEX);
		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		LWJGL.glActiveTexture(GL13.GL_TEXTURE0);
	}

	//endregion



	//=========//
	// getters //
	//=========//
	//region

	/** 0 if the mask hasn't been built yet */
	public int getRadius() { return (this.textureId != 0) ? this.radius : 0; }
	public int getCameraChunkX() { return this.cameraChunkX; }
	public int getCameraChunkZ() { return this.cameraChunkZ; }

	//endregion



}
