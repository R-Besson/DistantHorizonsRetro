/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import java.io.File;

import com.seibel.distanthorizons.common.wrappers.gui.NativeDialogUtil;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.logging.DhLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import org.jetbrains.annotations.Nullable;



/**
 * A singleton that wraps the Minecraft object.
 *
 * @author James Seibel
 */
public class MinecraftClientWrapper extends AbstractMinecraftSharedWrapper implements IMinecraftClientWrapper
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

	public static final MinecraftClientWrapper INSTANCE = new MinecraftClientWrapper();


	private ProfilerWrapper profilerWrapper;



	//======================//
	// multiplayer handling //
	//======================//
	//region

	@Override
	public boolean hasSinglePlayerServer()
	{
		return MINECRAFT.isSingleplayer();
	}
	@Override
	public boolean clientConnectedToDedicatedServer()
	{
		return this.hasServerConnection()
			&& !this.hasSinglePlayerServer();
	}
	@Override
	public boolean connectedToReplay()
	{
		return !this.hasServerConnection()
			&& !this.hasSinglePlayerServer() ;
	}

	private boolean hasServerConnection()
	{
		return MINECRAFT.getCurrentServerData() != null;
	}


	@Override
	public String getCurrentServerName()
	{
		if (this.connectedToReplay())
		{
			return ClientOnlySaveStructure.REPLAY_SERVER_FOLDER_NAME;
		}
		else
		{
			ServerData server = MINECRAFT.getCurrentServerData();
			return (server != null && server.serverName != null) ? server.serverName : "NULL";
		}
	}
	@Override
	public String getCurrentServerIp()
	{
		if (this.connectedToReplay())
		{
			return "";
		}
		else
		{
			ServerData server = getCurrentServerData();
			return getServerIp(server);
		}
	}
	@Override
	public String getCurrentServerVersion()
	{
		ServerData server = getCurrentServerData();
		return getServerVersion(server);
	}

	private ServerData getCurrentServerData()
	{
		return MINECRAFT.getCurrentServerData();
	}
	private String getServerIp(ServerData server)
	{
		if (server == null) { return "NA"; }

		return server.serverIP;
	}
	private String getServerVersion(ServerData server)
	{
		if (server == null) { return "UNKOWN"; }

		return server.gameVersion;
	}

	//endregion



	//=================//
	// player handling //
	//=================//
	//region

	@Nullable
	public EntityPlayerSP getPlayer() { return MINECRAFT.thePlayer; }

	@Override
	public boolean playerExists() { return this.getPlayer() != null; }

	@Override
	public DhBlockPos getPlayerBlockPos()
	{
		EntityPlayerSP player = this.getPlayer();
		if (player == null)
		{
			return new DhBlockPos(0, 0, 0);
		}

		return new DhBlockPos(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY), MathHelper.floor_double(player.posZ));
	}

	/** DhChunkPos is immutable so it can be safely shared */
	private volatile DhChunkPos lastPlayerChunkPos = null;

	@Override
	public DhChunkPos getPlayerChunkPos()
	{
		EntityPlayerSP player = this.getPlayer();
		if (player == null)
		{
			return new DhChunkPos(0, 0);
		}

		// reuse the last position while the player stays in the same chunk,
		// this is polled in a tight loop by the chunk update queue
		DhChunkPos lastPos = this.lastPlayerChunkPos;
		if (lastPos != null
			&& lastPos.getX() == player.chunkCoordX
			&& lastPos.getZ() == player.chunkCoordZ)
		{
			return lastPos;
		}
		lastPos = new DhChunkPos(player.chunkCoordX, player.chunkCoordZ);
		this.lastPlayerChunkPos = lastPos;
		return lastPos;
	}

	//endregion



	//================//
	// level handling //
	//================//
	//region

	@Nullable
	@Override
	public IClientLevelWrapper getWrappedClientLevel() { return this.getWrappedClientLevel(false); }

	@Override
	@Nullable
	public IClientLevelWrapper getWrappedClientLevel(boolean bypassLevelKeyManager)
	{
		WorldClient level = MINECRAFT.theWorld;
		if (level == null)
		{
			return null;
		}

		return ClientLevelWrapper.getWrapper(level, bypassLevelKeyManager);
	}

	//endregion



	//===========//
	// messaging //
	//===========//
	//region

	@Override
	public void sendChatMessage(String string)
	{
		EntityPlayerSP player = this.getPlayer();
		if (player == null)
		{
			return;
		}

		String[] lines = string.split("\n");
		for (String line : lines) {
			player.addChatMessage(new ChatComponentText(line));
		}
	}

	@Override
	public void sendOverlayMessage(String string)
	{
		EntityPlayerSP player = this.getPlayer();
		if (player == null)
		{
			return;
		}

		// shown above the hotbar, where 1.7.10 shows the record playing message
		Minecraft.getMinecraft().ingameGUI.setRecordPlaying(string, false);
	}

	//endregion



	//==========================//
	// vanilla option overrides //
	//==========================//
	//region

	public void disableVanillaClouds()
	{
		LOGGER.info("Disabling vanilla clouds... This is done to prevent vanilla clouds from rendering on top of Distant Horizons LODs.");

		MINECRAFT.gameSettings.clouds = false;
	}

	//endregion



	//======//
	// misc //
	//======//
	//region

	@Override
	public IProfilerWrapper getProfiler()
	{
		Profiler profiler = MINECRAFT.mcProfiler;

		if (this.profilerWrapper == null)
		{
			this.profilerWrapper = new ProfilerWrapper(profiler);
		}
		else if (profiler != this.profilerWrapper.profiler)
		{
			this.profilerWrapper.profiler = profiler;
		}

		return this.profilerWrapper;
	}

	@Override
	public void crashMinecraft(String errorMessage, Throwable exception)
	{
		LOGGER.fatal(ModInfo.READABLE_NAME + " had the following error: [" + errorMessage + "]. Crashing Minecraft...", exception);

		// Only crash once the renderer has been set up.
		// If the renderer hasn't been set up yet crashing MC will
		// cause a UI error instead of the error we're trying to send.
		executeOnRenderThread(() ->
		{
			throw new RuntimeException(exception);
		});
	}

	@Override
	public void executeOnRenderThread(Runnable runnable)
	{
		MINECRAFT.addScheduledTask(runnable);
	}

	@Override
	public void showDialog(String title, String message, String dialogType, String iconType)
	{ NativeDialogUtil.showDialog(title, message, dialogType, iconType); }

	@Override
	public boolean dialogNeedsAwtHeadless() { return NativeDialogUtil.needsAwtHeadless(); }

	//endregion



	//========//
	// shared //
	//========//
	//region

	@Override
	public boolean isDedicatedServer() { return false; }

	@Override
	public File getInstallationDirectory()
	{
		return MINECRAFT.mcDataDir;
	}

	@Override
	public int getPlayerCount()
	{
		// can be null if the server hasn't finished booting up yet
		if (MINECRAFT.getIntegratedServer() == null)
		{
			return 1;
		}
		else
		{
			return MINECRAFT.getIntegratedServer().getCurrentPlayerCount();
		}
	}

	@Nullable
	@Override
	public IServerLevelWrapper getLevelWrapper(String dimensionResourceLocation)
	{
		if (!this.hasSinglePlayerServer())
		{
			return null;
		}

		Integer dimensionKey = this.deserializeDimensionResourceKey(dimensionResourceLocation);
		if (dimensionKey == null || MINECRAFT.getIntegratedServer() == null)
		{
			return null;
		}

		WorldServer mcLevel = DimensionManager.getWorld(dimensionKey);
		return ServerLevelWrapper.getWrapper(mcLevel);
	}

	//endregion



}
