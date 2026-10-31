package com.seibel.distanthorizons.common;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.entity.player.EntityPlayerMP;

public abstract class AbstractPluginPacketSender implements IPluginPacketSender
{
	public static final String WRAPPER_PACKET_RESOURCE = ModInfo.RESOURCE_NAMESPACE + ModInfo.WRAPPER_PACKET_PATH;

	@Override
	public final void sendToClient(IServerPlayerWrapper serverPlayer, AbstractNetworkMessage message)
	{
		this.sendToClient((EntityPlayerMP) serverPlayer.getWrappedMcObject(), message);
	}

	public abstract void sendToClient(EntityPlayerMP serverPlayer, AbstractNetworkMessage message);

	@Override
	public abstract void sendToServer(AbstractNetworkMessage message);
}
