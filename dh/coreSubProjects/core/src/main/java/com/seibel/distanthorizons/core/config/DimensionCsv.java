package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A comma separated dimension list from a config entry, IE "nether:-1,7". <br>
 * Entries can be the "name:ID" shown on the F3 screen, just the name or just the ID. <br>
 * Entries can optionally have a value: "nether:-1=64,7=32". <br><br>
 *
 * The list is only parsed again when the config changes and results are cached per dimension,
 * so it can be checked for every LOD column.
 */
public class DimensionCsv
{
	public static final DimensionCsv CAVE_CULLING_DISABLED = new DimensionCsv(Config.Client.Advanced.Graphics.Culling.caveCullingDisabledDimensionCsv);
	public static final DimensionCsv DH_FOG_DISABLED = new DimensionCsv(Config.Client.Advanced.Graphics.Fog.dhFogDisabledDimensionCsv);
	public static final DimensionCsv GENERATION_RADIUS = new DimensionCsv(Config.Common.WorldGenerator.dimensionGenerationRadiusCsv);

	/** @return the dimension's generation radius in chunks, 0 for no limit, negative if generation is off there */
	public static int getGenerationMaxChunkRadius(ILevelWrapper levelWrapper)
	{ return GENERATION_RADIUS.getIntValue(levelWrapper, Config.Common.WorldGenerator.generationMaxChunkRadius.get()); }



	private final ConfigEntry<String> configEntry;

	private volatile ParsedCsv parsedCsv = new ParsedCsv("");



	public DimensionCsv(ConfigEntry<String> configEntry) { this.configEntry = configEntry; }



	public boolean contains(ILevelWrapper levelWrapper) { return this.getValue(levelWrapper) != null; }

	/** @return null if the dimension isn't in the list, "" if it's in the list without a value */
	@Nullable
	public String getValue(ILevelWrapper levelWrapper)
	{
		ParsedCsv parsed = this.getParsedCsv();
		if (parsed.valueByEntry.isEmpty())
		{
			return null;
		}

		String dimensionName = levelWrapper.getDimensionName();
		return parsed.valueByDimensionName.computeIfAbsent(dimensionName, parsed::findValue).orElse(null);
	}

	/** @return the dimension's value or the fallback if the dimension isn't listed or its value isn't a number */
	public int getIntValue(ILevelWrapper levelWrapper, int fallback)
	{
		String value = this.getValue(levelWrapper);
		if (value == null || value.isEmpty())
		{
			return fallback;
		}

		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private ParsedCsv getParsedCsv()
	{
		String csv = this.configEntry.get();
		if (csv == null)
		{
			csv = "";
		}

		ParsedCsv parsed = this.parsedCsv;
		if (!parsed.csv.equals(csv))
		{
			parsed = new ParsedCsv(csv);
			this.parsedCsv = parsed;
		}
		return parsed;
	}



	private static class ParsedCsv
	{
		final String csv;
		/** lower case entry -> value, "" when no value was given */
		final Map<String, String> valueByEntry = new HashMap<>();
		final ConcurrentHashMap<String, Optional<String>> valueByDimensionName = new ConcurrentHashMap<>();

		ParsedCsv(String csv)
		{
			this.csv = csv;
			for (String entry : csv.split(","))
			{
				String value = "";
				int equalsIndex = entry.indexOf('=');
				if (equalsIndex >= 0)
				{
					value = entry.substring(equalsIndex + 1).trim();
					entry = entry.substring(0, equalsIndex);
				}

				entry = entry.trim().toLowerCase(Locale.ROOT);
				if (!entry.isEmpty())
				{
					this.valueByEntry.put(entry, value);
				}
			}
		}

		Optional<String> findValue(String dimensionName)
		{
			String name = dimensionName.toLowerCase(Locale.ROOT);

			// client levels are prefixed with a server/world hash
			int atIndex = name.indexOf('@');
			if (atIndex >= 0)
			{
				name = name.substring(atIndex + 1);
			}

			String value = this.valueByEntry.get(name);
			if (value == null)
			{
				// "name:ID", try the name and the ID on their own
				int colonIndex = name.lastIndexOf(':');
				if (colonIndex >= 0)
				{
					value = this.valueByEntry.get(name.substring(0, colonIndex));
					if (value == null)
					{
						value = this.valueByEntry.get(name.substring(colonIndex + 1));
					}
				}
			}

			return Optional.ofNullable(value);
		}

	}

}
