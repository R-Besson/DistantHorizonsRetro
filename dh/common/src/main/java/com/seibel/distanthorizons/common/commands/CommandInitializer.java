package com.seibel.distanthorizons.common.commands;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import static com.seibel.distanthorizons.core.network.messages.MessageRegistry.DEBUG_CODEC_CRASH_MESSAGE;

public class CommandInitializer
{
	public static ICommand initCommands()
	{
		return new CommandBase()
		{
			@Override
			public String getCommandName()
			{ return "dh"; }

			@Override
			public String getCommandUsage(ICommandSender sender)
			{
				return DEBUG_CODEC_CRASH_MESSAGE
						? "/dh <help|debug|config|crash|pregen>"
						: "/dh <help|debug|config|pregen>";
			}


			@Override
			public void processCommand(ICommandSender sender, String[] args) { this.executeCommand(MinecraftServer.getServer(), sender, args); }



			private void executeCommand(MinecraftServer server, ICommandSender sender, String[] args)
			{
				if (args.length == 0)
				{
					HelpCommand helpCommand = new HelpCommand();
					helpCommand.execute(sender);
					return;
				}

				switch (args[0])
				{
					case "help":
						HelpCommand helpCommand = new HelpCommand();
						helpCommand.execute(sender);
						break;
					case "debug":
						DebugCommand debugCommand = new DebugCommand();
						debugCommand.execute(sender);
						break;
					case "config":
						ConfigCommand configCommand = new ConfigCommand();
						configCommand.execute(sender, args);
						break;
					case "crash":
						if (DEBUG_CODEC_CRASH_MESSAGE)
						{
							CrashCommand crashCommand = new CrashCommand();
							crashCommand.execute(sender, args);
						}
						break;
					case "pregen":
						if (!server.isDedicatedServer())
						{
							AbstractDhCommand.sendMessage(sender, "Pregen command is only available on dedicated servers");
							break;
						}
						PregenCommand pregenCommand = new PregenCommand();
						pregenCommand.execute(server, sender, args);
						break;
					default:
						AbstractDhCommand.sendMessage(sender, "Unknown subcommand: " + args[0]);
				}
			}

			@Override
			public int compareTo(@NotNull Object obj) { return 0; }
		};

	}
}
