package com.seibel.distanthorizons.common.commands;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerState;
import com.seibel.distanthorizons.core.network.messages.base.CodecCrashMessage;

import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.core.world.IDhServerWorld;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;


public class CrashCommand extends AbstractDhCommand
{
	public void execute(ICommandSender sender, String[] args)
	{
		if (!(sender instanceof EntityPlayerMP))
		{
			sendMessage(sender, "This command can only be run by a player");
			return;
		}

		if (args.length < 2)
		{
			sendMessage(sender, "Usage: /dh crash <encode|decode>");
			return;
		}

		IDhServerWorld world = SharedApi.tryGetDhServerWorld();
		if (world == null)
		{
			return;
		}

		ServerPlayerState serverPlayerState =
			world
			.getServerPlayerStateManager()
			.getConnectedPlayer(ServerPlayerWrapper.getWrapper((EntityPlayerMP) sender));

		if (serverPlayerState == null)
		{
			return;
		}

		switch (args[1])
		{
			case "encode":
				serverPlayerState.networkSession.sendMessage(new CodecCrashMessage(CodecCrashMessage.ECrashPhase.ENCODE));
				break;
			case "decode":
				serverPlayerState.networkSession.sendMessage(new CodecCrashMessage(CodecCrashMessage.ECrashPhase.DECODE));
				break;
			default:
				sendMessage(sender, "Usage: /dh crash <encode|decode>");
		}
	}

}
