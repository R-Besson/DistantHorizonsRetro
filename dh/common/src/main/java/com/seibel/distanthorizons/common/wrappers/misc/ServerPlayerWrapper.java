package com.seibel.distanthorizons.common.wrappers.misc;

import com.google.common.base.Objects;
import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.WorldServer;

import java.util.concurrent.ConcurrentMap;

/**
 * This wrapper transparently ensures that the underlying {@link EntityPlayerMP} is always valid,
 * unless the player has disconnected.
 */
public class ServerPlayerWrapper implements IServerPlayerWrapper
{
	private static final ConcurrentMap<NetHandlerPlayServer, ServerPlayerWrapper> serverPlayerWrapperMap = new MapMaker().weakKeys().weakValues().makeMap();

	private final NetHandlerPlayServer connection;



	//=============//
	// constructor //
	//=============//
	//region

	public static ServerPlayerWrapper getWrapper(EntityPlayerMP serverPlayer)
	{
		NetHandlerPlayServer netHandler = serverPlayer.playerNetServerHandler;
		return serverPlayerWrapperMap.computeIfAbsent(netHandler, ignored -> new ServerPlayerWrapper(netHandler));
	}

	private ServerPlayerWrapper(NetHandlerPlayServer connection)
	{ this.connection = connection; }

	//endregion



	//=========//
	// getters //
	//=========//
	//region

	private EntityPlayerMP getServerPlayer()
	{
		return this.connection.playerEntity;
	}

	@Override
	public String getName()
	{
		return this.getServerPlayer().getDisplayName();
	}

	@Override
	public IServerLevelWrapper getLevel()
	{
		WorldServer level = null;
		if (this.getServerPlayer() instanceof IMixinServerPlayer)
		{
			IMixinServerPlayer mixinPlayer = (IMixinServerPlayer) this.getServerPlayer();
			level = mixinPlayer.distantHorizons$getDimensionChangeDestination();
		}

		if (level == null)
		{
			level = (WorldServer) this.getServerPlayer().worldObj;
		}

		return ServerLevelWrapper.getWrapper(level);
	}

	@Override
	public DhVec3d getPosition()
	{
		EntityPlayerMP player = this.getServerPlayer();
		return new DhVec3d(player.posX, player.posY, player.posZ);
	}

	//endregion



	//================//
	// base overrides //
	//================//
	//region

	@Override
	public Object getWrappedMcObject() { return this.getServerPlayer(); }

	@Override
	public String toString() { return "Wrapped{" + this.getServerPlayer() + "}"; }

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (!(obj instanceof ServerPlayerWrapper))
		{
			return false;
		}
		ServerPlayerWrapper that = (ServerPlayerWrapper) obj;
		return Objects.equal(this.connection, that.connection);
	}

	@Override
	public int hashCode() { return Objects.hashCode(this.connection); }

	//endregion



}
