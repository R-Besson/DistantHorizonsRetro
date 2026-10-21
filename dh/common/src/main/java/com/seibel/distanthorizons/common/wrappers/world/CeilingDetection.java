package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a dimension has a ceiling, IE it's all caves and shouldn't be cave culled. <br>
 * 1.7.10 has no flag for this, {@link WorldProvider#isHellWorld} is only set by the Nether,
 * so dimensions without sky light are treated as having one. The End is the exception,
 * it has no sky light but is open above. The config lists override the detection.
 */
public class CeilingDetection
{
	private static volatile ParsedCsv ceilingDimensions = new ParsedCsv("");
	private static volatile ParsedCsv noCeilingDimensions = new ParsedCsv("");



	public static boolean hasCeiling(WorldProvider provider)
	{
		int dimensionId = provider.dimensionId;
		if (getCeilingDimensions().contains(dimensionId))
		{
			return true;
		}
		if (getNoCeilingDimensions().contains(dimensionId))
		{
			return false;
		}

		return provider.isHellWorld
			|| (provider.hasNoSky && !(provider instanceof WorldProviderEnd));
	}



	//=========//
	// parsing //
	//=========//
	//region

	/** re-parsed only when the config string changes */
	private static ParsedCsv getCeilingDimensions()
	{
		String csv = Config.Client.Advanced.Graphics.Culling.ceilingDimensionCsv.get();
		if (csv == null) { csv = ""; }
		ParsedCsv parsed = ceilingDimensions;
		if (!parsed.csv.equals(csv))
		{
			parsed = new ParsedCsv(csv);
			ceilingDimensions = parsed;
		}
		return parsed;
	}
	private static ParsedCsv getNoCeilingDimensions()
	{
		String csv = Config.Client.Advanced.Graphics.Culling.noCeilingDimensionCsv.get();
		if (csv == null) { csv = ""; }
		ParsedCsv parsed = noCeilingDimensions;
		if (!parsed.csv.equals(csv))
		{
			parsed = new ParsedCsv(csv);
			noCeilingDimensions = parsed;
		}
		return parsed;
	}

	/** accepts "name:ID" like the F3 screen and the ignored dimension list, or just the ID */
	private static class ParsedCsv
	{
		final String csv;
		final Set<String> entries = new HashSet<>();

		ParsedCsv(String csv)
		{
			this.csv = (csv != null) ? csv : "";
			for (String entry : this.csv.split(","))
			{
				entry = entry.trim().toLowerCase(Locale.ROOT);
				if (!entry.isEmpty())
				{
					this.entries.add(entry);
				}
			}
		}

		boolean contains(int dimensionId)
		{
			return !this.entries.isEmpty()
				&& (this.entries.contains(Integer.toString(dimensionId))
					|| this.entries.contains(LegacyDimensionInfo.fullName(dimensionId).toLowerCase(Locale.ROOT)));
		}
	}

	//endregion



}
