package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User defined LOD colors, read from {@link #FILE_NAME} in the config folder. <br>
 * Each line is {@code modid:block=RRGGBB} (every meta) or {@code modid:block:meta=RRGGBB},
 * {@code AARRGGBB} sets the alpha too ({@code 00} hides the block). <br>
 * Overridden colors are final, biome tinting isn't applied to them.
 */
public class BlockColorOverrides
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	public static final String FILE_NAME = "DistantHorizonsBlockColors.cfg";
	/** used as the meta key for overrides that apply to every meta */
	private static final int ANY_META = -1;

	private static final List<String> DEFAULT_FILE_LINES = Arrays.asList(
		"# Distant Horizons block color overrides",
		"# Changes the color of blocks in LODs. Reloaded with F3+T.",
		"#",
		"# Format: modid:block=RRGGBB or modid:block:meta=RRGGBB (hex)",
		"# Use AARRGGBB to also set the alpha, 00 alpha hides the block in LODs.",
		"# Overridden colors aren't biome tinted.",
		"# Block names are registry names, the same ones /give uses.",
		"# Meta is the block's damage value, leave it out to match every meta.",
		"#",
		"# Examples:",
		"# minecraft:wool:14=A12722",
		"# minecraft:stone=7D7D7D",
		""
	);

	/** block name -> (meta or {@link #ANY_META}) -> ARGB color */
	private static volatile Map<String, Map<Integer, Integer>> overrides = new HashMap<>();
	private static volatile boolean loaded = false;



	//=========//
	// loading //
	//=========//
	//region

	/** (re)reads the override file, creating it with examples if it doesn't exist */
	public static synchronized void reload()
	{
		Map<String, Map<Integer, Integer>> newOverrides = new HashMap<>();
		File file = new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), FILE_NAME);
		try
		{
			if (!file.exists())
			{
				file.getParentFile().mkdirs();
				Files.write(file.toPath(), DEFAULT_FILE_LINES, StandardCharsets.UTF_8);
			}

			List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i).trim();
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}

				if (!parseLine(line, newOverrides))
				{
					LOGGER.warn("Ignoring invalid line " + (i + 1) + " in [" + FILE_NAME + "]: [" + line + "]");
				}
			}
		}
		catch (IOException e)
		{
			LOGGER.error("Unable to read block color overrides from [" + file + "]: " + e.getMessage(), e);
		}

		overrides = newOverrides;
		loaded = true;
		if (!newOverrides.isEmpty())
		{
			LOGGER.info("Loaded block color overrides for [" + newOverrides.size() + "] blocks");
		}
	}

	/** @return false if the line is malformed */
	private static boolean parseLine(String line, Map<String, Map<Integer, Integer>> into)
	{
		int equalsIndex = line.indexOf('=');
		if (equalsIndex <= 0)
		{
			return false;
		}

		String blockString = line.substring(0, equalsIndex).trim();
		String colorString = line.substring(equalsIndex + 1).trim();
		if (colorString.startsWith("#"))
		{
			colorString = colorString.substring(1);
		}

		int color;
		try
		{
			if (colorString.length() == 6)
			{
				color = 0xFF000000 | Integer.parseInt(colorString, 16);
			}
			else if (colorString.length() == 8)
			{
				color = (int) Long.parseLong(colorString, 16);
			}
			else
			{
				return false;
			}
		}
		catch (NumberFormatException e)
		{
			return false;
		}

		// "modid:block" or "modid:block:meta"
		String[] parts = blockString.split(":");
		int meta = ANY_META;
		String blockName;
		if (parts.length == 2)
		{
			blockName = blockString;
		}
		else if (parts.length == 3)
		{
			try
			{
				meta = Integer.parseInt(parts[2]);
			}
			catch (NumberFormatException e)
			{
				return false;
			}
			blockName = parts[0] + ":" + parts[1];
		}
		else
		{
			return false;
		}

		into.computeIfAbsent(blockName, (name) -> new HashMap<>()).put(meta, color);
		return true;
	}

	//endregion



	//=========//
	// getters //
	//=========//
	//region

	/** @return the ARGB override for this block, or null if it has none */
	@Nullable
	public static Integer get(Block block, int meta)
	{
		if (!loaded)
		{
			reload();
		}

		Map<String, Map<Integer, Integer>> currentOverrides = overrides;
		if (currentOverrides.isEmpty())
		{
			return null;
		}

		String blockName = GameData.getBlockRegistry().getNameForObject(block);
		Map<Integer, Integer> colorByMeta = (blockName != null) ? currentOverrides.get(blockName) : null;
		if (colorByMeta == null)
		{
			return null;
		}

		Integer color = colorByMeta.get(meta);
		return (color != null) ? color : colorByMeta.get(ANY_META);
	}

	//endregion



}
