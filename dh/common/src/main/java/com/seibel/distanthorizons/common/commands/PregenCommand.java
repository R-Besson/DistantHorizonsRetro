package com.seibel.distanthorizons.common.commands;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.generation.PregenManager;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.world.DhServerWorld;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;


public class PregenCommand extends AbstractDhCommand
{
	private PregenManager getPregenManager()
	{
		DhServerWorld world = (DhServerWorld) Objects.requireNonNull(SharedApi.getAbstractDhWorld());
		return world.getPregenManager();
	}

	public void execute(MinecraftServer server, ICommandSender sender, String[] args)
	{
		if (args.length < 2)
		{
			sendMessage(sender, "Usage: /dh pregen <status|start|stop>");
			return;
		}

		switch (args[1])
		{
			case "status":
			{
				String statusString = this.getPregenManager().getStatusString();
				sendMessage(sender, statusString != null ? statusString : "Pregen is not running");
				break;
			}
			case "start":
			{
				if (args.length < 5)
				{
					sendMessage(sender, "Usage: /dh pregen start <dimension> <x> <z> <chunkRadius>");
					return;
				}

				try
				{
					String dimensionName = args[2];
					int x = Integer.parseInt(args[3]);
					int z = Integer.parseInt(args[4]);
					int chunkRadius = args.length >= 6 ? Integer.parseInt(args[5]) : 32;

					// find the world by dimension name
					WorldServer world = null;
					for (WorldServer possibleWorld : server.worldServers)
					{
						if (possibleWorld.provider.getDimensionName().equals(dimensionName)
							|| String.valueOf(possibleWorld.provider.dimensionId).equals(dimensionName))
						{
							world = possibleWorld;
							break;
						}
					}

					if (world == null)
					{
						sendMessage(sender, "Unknown dimension: " + dimensionName);
						return;
					}

					sendMessage(sender, "Starting pregen. Progress will be in the server console.");

					final ICommandSender finalSender = sender;
					CompletableFuture<Void> future = this.getPregenManager().startPregen(
						ServerLevelWrapper.getWrapper(world),
						new DhBlockPos2D(x, z),
						chunkRadius
					);

					future.whenComplete((result, throwable) -> {
						if (throwable instanceof CancellationException)
						{
							sendMessage(finalSender, "Pregen is cancelled");
							return;
						}
						else if (throwable != null)
						{
							sendMessage(finalSender, "Pregen failed: " + throwable.getMessage());
							return;
						}
						sendMessage(finalSender, "Pregen is complete");
					});
				}
				catch (NumberFormatException e)
				{
					sendMessage(sender, "Invalid number format");
				}
				break;
			}
			case "stop":
			{
				CompletableFuture<Void> runningPregen = this.getPregenManager().getRunningPregen();
				if (runningPregen == null)
				{
					sendMessage(sender, "Pregen is not running");
					return;
				}
				runningPregen.cancel(true);
				break;
			}
			default:
				sendMessage(sender, "Unknown subcommand: " + args[1]);
		}
	}
}
