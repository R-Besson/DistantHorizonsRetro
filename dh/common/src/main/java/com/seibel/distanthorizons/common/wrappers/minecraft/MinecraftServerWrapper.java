package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.io.File;

public class MinecraftServerWrapper extends AbstractMinecraftSharedWrapper
{
	public static final MinecraftServerWrapper INSTANCE = new MinecraftServerWrapper();

	/** set during server startup */
	@Nullable
	public DedicatedServer dedicatedServer = null;



	//=============//
	// constructor //
	//=============//

	private MinecraftServerWrapper() { }



	//=========//
	// methods //
	//=========//

	@Override
	public boolean isDedicatedServer() { return true; }

	@Override
	public File getInstallationDirectory()
	{
		if (this.dedicatedServer == null)
		{
			throw new IllegalStateException("Trying to get Installation Direction before dedicated server completed initialization!");
		}

		return new File("."); // equivalent to new MC's "server.getDataDirectory()"
	}

	@Override
	public int getPlayerCount()
	{
		if (this.dedicatedServer == null)
		{
			throw new IllegalStateException("Trying to get player count before dedicated server completed initialization!");
		}

		return this.dedicatedServer.getCurrentPlayerCount();
	}


	@Nullable
	@Override
	public IServerLevelWrapper getLevelWrapper(String dimensionResourceLocation)
	{
		if (this.dedicatedServer == null)
		{
			throw new IllegalStateException("Trying to get the server mcLevel before dedicated server completed initialization!");
		}

		Integer dimensionKey = this.deserializeDimensionResourceKey(dimensionResourceLocation);
		if (dimensionKey == null)
		{
			return null;
		}

		WorldServer mcLevel = DimensionManager.getWorld(dimensionKey);
		return ServerLevelWrapper.getWrapper(mcLevel);
	}

}
