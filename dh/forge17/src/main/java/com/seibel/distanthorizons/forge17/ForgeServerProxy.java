package com.seibel.distanthorizons.forge17;

import com.seibel.distanthorizons.common.commonMixins.MixinChunkMapCommon;
import com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.HashMap;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ForgeServerProxy implements AbstractModInitializer.IEventProxy
{
    private final boolean isDedicated;
	
	
	
	//=============//
    // constructor //
    //=============//
	//region

    public ForgeServerProxy(boolean isDedicated) 
    { 
		this.isDedicated = isDedicated;
	    
    }
	
	@Override
	public void registerEvents()
	{
		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance()
			.bus()
			.register(this);
		
		if (this.isDedicated)
		{
			ForgePluginPacketSender.setPacketHandler(ServerApi.INSTANCE::pluginMessageReceived);
		}
	}
	
	//endregion
	
	
	
    //========//
    // events //
    //========//
	//region
	
	public static void serverStopping()
	{
		ServerThreadTaskHandler.INSTANCE.cancelPendingTasks();
	}

    // ServerTickEvent (at end)
    @SubscribeEvent
	public void serverTickEvent(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.START)
		{
			ServerThreadTaskHandler.INSTANCE.onTickStart();
		}
		else
		{
			ServerThreadTaskHandler.INSTANCE.runTasks();

			for (BlockChangeLodUpdater blockChangeLodUpdater : this.blockChangeLodUpdaterByWorld.values())
			{
				blockChangeLodUpdater.updateChangedChunks();
			}
		}
	}
	/** only used on the server thread */
	private final HashMap<WorldServer, BlockChangeLodUpdater> blockChangeLodUpdaterByWorld = new HashMap<>();

    // ServerLevelLoadEvent
    @SubscribeEvent 
    public void serverLevelLoadEvent(WorldEvent.Load event)
    {
		if (GetEventLevel(event) instanceof WorldServer)
	    {
			WorldServer world = (WorldServer) GetEventLevel(event);
			ServerApi.INSTANCE.serverLevelLoadEvent(getServerLevelWrapper(world));

			BlockChangeLodUpdater blockChangeLodUpdater = new BlockChangeLodUpdater(world);
			world.addWorldAccess(blockChangeLodUpdater);
			BlockChangeLodUpdater previousUpdater = this.blockChangeLodUpdaterByWorld.put(world, blockChangeLodUpdater);
			if (previousUpdater != null)
			{
				world.removeWorldAccess(previousUpdater);
			}
		}
	}

    // ServerLevelUnloadEvent
    @SubscribeEvent
	public void serverLevelUnloadEvent(WorldEvent.Unload event)
    {
		if (GetEventLevel(event) instanceof WorldServer)
	    {
			BlockChangeLodUpdater blockChangeLodUpdater = this.blockChangeLodUpdaterByWorld.remove((WorldServer) GetEventLevel(event));
			if (blockChangeLodUpdater != null)
			{
				((WorldServer) GetEventLevel(event)).removeWorldAccess(blockChangeLodUpdater);
			}

			// the levels are looked up by their wrapper instance (it has no equals()),
			// so this must be the cached wrapper the level was loaded with,
			// otherwise the DH level stays open after MC unloads the dimension
			// and a second one is opened on the same database when it's loaded again
			ServerApi.INSTANCE.serverLevelUnloadEvent(getServerLevelWrapper((WorldServer) GetEventLevel(event)));
		}
	}

    @SubscribeEvent
	public void serverChunkLoadEvent(ChunkEvent.Load event)
    {
		if (!(event.world instanceof WorldServer))
	    {
			return;
		}
		
		Chunk chunk = event.getChunk();
		// Only handle event if chunk is ready, otherwise drop the update - we'll get it later during save
		if (!ChunkWrapper.canSaveChunk(chunk))
	    {
			return;
		}
		
		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));
		ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, levelWrapper);
	    ServerApi.INSTANCE.serverChunkLoadEvent(chunkWrapper, levelWrapper);
	}

    @SubscribeEvent
    public void serverChunkSaveEvent(ChunkDataEvent.Save event) 
    {
        if (!(event.world instanceof WorldServer)) 
		{
            return;
        }
	    MixinChunkMapCommon.onChunkSave((WorldServer) event.world, event.getChunk());
    }

    @SubscribeEvent
    public void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) 
    {
	    IServerPlayerWrapper serverPlayerWrapper = getServerPlayerWrapper(event);
	    if (serverPlayerWrapper == null)
	    {
		    return;
	    }
		ServerApi.INSTANCE.serverPlayerJoinEvent(serverPlayerWrapper);
	}

    @SubscribeEvent
    public void playerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) 
    {
	    IServerPlayerWrapper serverPlayerWrapper = getServerPlayerWrapper(event);
	    if (serverPlayerWrapper == null)
	    {
		    return;
	    }
		ServerApi.INSTANCE.serverPlayerDisconnectEvent(serverPlayerWrapper);
	}

    @SubscribeEvent
    public void playerChangedDimensionEvent(PlayerEvent.PlayerChangedDimensionEvent event) 
    {
        ServerApi.INSTANCE.serverPlayerLevelChangeEvent(
            getServerPlayerWrapper(event),
            getServerLevelWrapper(event.fromDim, event),
            getServerLevelWrapper(event.toDim, event));
    }

    @SubscribeEvent
	public void clickBlockEvent(PlayerInteractEvent event)
    {
		// this event also fires for the client world in singleplayer,
		// and RIGHT_CLICK_AIR has no block position (x/z are always 0)
		if (event.world.isRemote
			|| event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR)
		{
			return;
		}

		ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(event.world);
		if (SharedApi.isChunkAtBlockPosAlreadyUpdating(wrappedLevel, event.x, event.z))
	    {
			return;
		}
		
		ServerThreadTaskHandler.INSTANCE.queueTask(() ->
		{
			Chunk chunk = event.world.getChunkFromBlockCoords(event.x, event.z);
			ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, wrappedLevel);
			SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, wrappedLevel, true);
			return null;
		});
	}
	
	//endregion
	
	
	
    //================//
    // helper methods //
    //================//
	//region
	
	private static World GetEventLevel(WorldEvent e) { return e.world; }
	
	private static IServerLevelWrapper getServerLevelWrapper(WorldServer level) 
    { return ServerLevelWrapper.getWrapper(level); }
	
    private static IServerLevelWrapper getServerLevelWrapper(int dim, PlayerEvent event) 
    {
        WorldServer world = (WorldServer) event.player.worldObj;
        WorldServer worldDim = world.func_73046_m()
            .worldServerForDimension(dim);
        return getServerLevelWrapper(worldDim);
    }
	
    private static IServerPlayerWrapper getServerPlayerWrapper(PlayerEvent event) 
    { return ServerPlayerWrapper.getWrapper((EntityPlayerMP) event.player); }
	
	//endregion
	
	
	
	//================//
	// helper classes //
	//================//
	//region
	
	private static class ChunkLoadEvent
	{
		public final ChunkWrapper chunkWrapper;
		public final ILevelWrapper level;
		
		/** 
		 * used to track how long this event has
		 * been queued so we can clean up old tasks.
		 */
		public int numberOfTicksSinceQueue = 0;
		
		
		
		private ChunkLoadEvent(ChunkWrapper chunkWrapper, ILevelWrapper level)
		{
			this.chunkWrapper = chunkWrapper;
			this.level = level;
		}
	}
	
}
