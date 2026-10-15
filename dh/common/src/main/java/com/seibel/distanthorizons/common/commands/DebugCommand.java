package com.seibel.distanthorizons.common.commands;

import com.seibel.distanthorizons.core.logging.f3.F3Screen;

import net.minecraft.command.ICommandSender;

import java.util.ArrayList;
import java.util.List;


public class DebugCommand extends AbstractDhCommand
{
	private static String getDebugString()
	{
		List<String> lines = new ArrayList<>();
		F3Screen.addStringToDisplay(lines);
		return String.join("\n", lines);
	}

	public void execute(ICommandSender sender)
	{ sendMessage(sender, getDebugString()); }

}
