package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

#if MC_VER > MC_1_12_2
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
#endif

#if MC_VER <= MC_1_7_10
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.FMLCommonHandler;
#elif MC_VER <= MC_1_12_2
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
#elif MC_VER <= MC_1_21_10
import net.minecraft.resources.ResourceLocation;
#else
import net.minecraft.resources.Identifier;
#endif

#if MC_VER > MC_1_19_2
import net.minecraft.core.registries.Registries;
#elif MC_VER > MC_1_12_2
import net.minecraft.core.Registry;
#endif

public abstract class AbstractMinecraftSharedWrapper implements IMinecraftSharedWrapper
{
	public static boolean supportsSurfaceGeneration = false;

	/** a tick slower than this is a lag spike, 50 ms is the standard tick rate */
	private static final long SLOW_TICK_NANO = 50_000_000L;
	/** out of the last 100 ticks */
	private static final int MAX_SLOW_TICKS_1_7_10 = 5;

	
	
	@Nullable
	#if MC_VER <= MC_1_12_2
	protected Integer deserializeDimensionResourceKey(String dimensionResourceLocation)
	#else
	protected ResourceKey<Level> deserializeDimensionResourceKey(String dimensionResourceLocation)
	#endif
	{
		#if  MC_VER <= MC_1_12_2
		try
		{
			return Integer.parseInt(dimensionResourceLocation.substring(dimensionResourceLocation.indexOf(":")+1));
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
		#else
			#if  MC_VER <= MC_1_21_10
			ResourceLocation dimResourceLocation = ResourceLocation.tryParse(dimensionResourceLocation);
			#else
			Identifier dimResourceLocation = Identifier.tryParse(dimensionResourceLocation);
			#endif
			if (dimResourceLocation == null)
			{
				return null;
			}
			
			#if  MC_VER > MC_1_19_2
			ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimResourceLocation);
			#else
			ResourceKey<Level> dimensionKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimResourceLocation);
			#endif
			
		return dimensionKey;
		#endif
	}
	
	@Override
	public boolean isServerThreadHealthy()
	{
		double[] avgAndP99Ms = this.getServerTickAvgAndP99Ms();
		if (avgAndP99Ms == null)
		{
			// assume the server is healthy if
			// we can't get the ticks
			return true;
		}
		double avgMs = avgAndP99Ms[0];
		double p99Ms = avgAndP99Ms[1];
		
		#if MC_VER <= MC_1_7_10
		// DH's server thread work runs after Minecraft records its tick time,
		// so add it back to get the real time used per tick.
		// The server stays at 20 TPS as long as the total averages under 50 ms,
		// 40 ms leaves a margin for spikes and work neither of us can measure.
		// A single slow tick (chunk saving, a GC pause) doesn't lower TPS,
		// 1.7.10's server loop catches up by running the missed ticks back to back,
		// so only treat slow ticks as a problem when they keep happening.
		// DH can't make a slow tick slower, its per-tick deadline has already passed by then.
		double totalAvgMs = avgMs + ServerThreadTaskHandler.INSTANCE.getAverageDhMsPerTick();
		int slowTickCount = (int) avgAndP99Ms[2];
		return totalAvgMs < 40.0
			&& slowTickCount < MAX_SLOW_TICKS_1_7_10;
		#else
		return avgMs < 25.0 // 50 ms is standard tick rate
			&& p99Ms < 50.0;
		#endif
	}
	@Override
	public String getServerHealthDetails()
	{
		double[] avgAndP99Ms = this.getServerTickAvgAndP99Ms();
		if (avgAndP99Ms == null)
		{
			return null;
		}
		
		String details = "MC avg " + String.format("%.1f", avgAndP99Ms[0]) + " ms"
			+ ", slowest " + String.format("%.1f", avgAndP99Ms[1]) + " ms"
			+ ", " + (int) avgAndP99Ms[2] + " ticks over 50 ms";
		#if MC_VER <= MC_1_7_10
		details += ", DH avg " + String.format("%.1f", ServerThreadTaskHandler.INSTANCE.getAverageDhMsPerTick()) + " ms";
		#endif
		return details;
	}
	
	/** @return null if tick times aren't available, otherwise {average ms, 99th percentile ms, number of ticks over 50 ms} */
	private double @Nullable [] getServerTickAvgAndP99Ms()
	{
		long[] nanoTicks = this.getServerTickTimesNano();
		if (nanoTicks == null)
		{
			return null;
		}
		
		long[] sortedNanoTicks = nanoTicks.clone();
		Arrays.sort(sortedNanoTicks);
		
		// 99 percentile
		int p99Index = (int)Math.ceil(0.99 * sortedNanoTicks.length) - 1;
		double p99Ms = sortedNanoTicks[Math.max(0, p99Index)] * 1e-6; // convert from Nano to Milli
		
		double avgMs = Arrays.stream(sortedNanoTicks).average().orElse(0) * 1e-6; // convert from Nano to Milli
		
		int slowTickCount = 0;
		for (int i = sortedNanoTicks.length - 1; i >= 0 && sortedNanoTicks[i] > SLOW_TICK_NANO; i--)
		{
			slowTickCount++;
		}
		
		return new double[] { avgMs, p99Ms, slowTickCount };
	}
	
	private long @Nullable [] getServerTickTimesNano()
	{
		// currently this logic is only implemented for singleplayer servers (except 1.12.2 and lower)
		#if MC_VER > MC_1_12_2
		if (this.isDedicatedServer())
		{
			return null;
		}
		#endif
		
		#if MC_VER <= MC_1_12_2
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		if (server == null)
		{
			return null;
		}
		return server.tickTimeArray;
		#elif MC_VER <= MC_1_20_2

		if (Minecraft.getInstance().getSingleplayerServer() == null)
		{
			return null;
		}
		return Minecraft.getInstance().getSingleplayerServer().tickTimes;
		#else
		
		if (Minecraft.getInstance().getSingleplayerServer() == null)
		{
			return null;
		}
		return Minecraft.getInstance().getSingleplayerServer().getTickTimesNanos();
		#endif
	}
	
	
	@Override
	public boolean supportsSurfaceGeneration() { return supportsSurfaceGeneration; }
	
	
	
}
