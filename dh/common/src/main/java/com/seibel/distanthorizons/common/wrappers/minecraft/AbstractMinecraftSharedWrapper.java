package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.common.util.threading.ServerThreadTaskHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.FMLCommonHandler;

public abstract class AbstractMinecraftSharedWrapper implements IMinecraftSharedWrapper
{
	public static boolean supportsSurfaceGeneration = false;

	/** a tick slower than this is a lag spike, 50 ms is the standard tick rate */
	private static final long SLOW_TICK_NANO = 50_000_000L;
	/** out of the last 100 ticks */
	private static final int MAX_SLOW_TICKS = 5;



	@Nullable
	protected Integer deserializeDimensionResourceKey(String dimensionResourceLocation)
	{
		try
		{
			return Integer.parseInt(dimensionResourceLocation.substring(dimensionResourceLocation.indexOf(":")+1));
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
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

		// DH's server thread work runs after Minecraft records its tick time,
		// so add it back to get the real time used per tick.
		// The server stays at 20 TPS as long as the total averages under 50 ms,
		// 40 ms leaves a margin for spikes and work neither of us can measure.
		// A single slow tick (chunk saving, a GC pause) doesn't lower TPS,
		// the server loop catches up by running the missed ticks back to back,
		// so only treat slow ticks as a problem when they keep happening.
		// DH can't make a slow tick slower, its per-tick deadline has already passed by then.
		double totalAvgMs = avgMs + ServerThreadTaskHandler.INSTANCE.getAverageDhMsPerTick();
		int slowTickCount = (int) avgAndP99Ms[2];
		return totalAvgMs < 40.0
			&& slowTickCount < MAX_SLOW_TICKS;
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
		details += ", DH avg " + String.format("%.1f", ServerThreadTaskHandler.INSTANCE.getAverageDhMsPerTick()) + " ms";
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
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		if (server == null)
		{
			return null;
		}
		return server.tickTimeArray;
	}


	@Override
	public boolean supportsSurfaceGeneration() { return supportsSurfaceGeneration; }



}
