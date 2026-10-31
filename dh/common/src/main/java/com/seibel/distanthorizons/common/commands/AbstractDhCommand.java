package com.seibel.distanthorizons.common.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/**
 * Abstract class providing common functionality for DH's commands.
 */
public abstract class AbstractDhCommand
{
	protected static void sendMessage(ICommandSender sender, String message)
	{
		sender.addChatMessage(new ChatComponentText(message));
	}
}
