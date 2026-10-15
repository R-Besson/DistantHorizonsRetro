package com.seibel.distanthorizons.common.wrappers.level;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import net.minecraft.client.multiplayer.WorldClient;

public class ServerKeyedClientLevelWrapper extends ClientLevelWrapper implements IServerKeyedClientLevel
{
	/** Returns the folder name the server wants the client to use. */
	private final String serverKey;
	
	/** A unique identifier (generally the level's name) for differentiating multiverse levels */
	private final String serverLevelKey;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public ServerKeyedClientLevelWrapper(WorldClient level, String serverKey, String serverLevelKey)
	{
		super(level);
		this.serverKey = serverKey;
		this.serverLevelKey = serverLevelKey;
	}
	
	//endregion
	
	
	
	//======================//
	// level identification //
	//======================//
	//region
	
	@Override
	public String getServerKey() { return this.serverKey; }
	
	@Override
	public String getServerLevelKey() { return this.serverLevelKey; }
	
	@Override
	public String getDhIdentifier() { return this.getServerLevelKey(); }
	
	//endregion
	
	
	
}
