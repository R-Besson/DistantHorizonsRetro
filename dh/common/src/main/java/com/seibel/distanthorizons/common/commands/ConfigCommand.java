package com.seibel.distanthorizons.common.commands;

import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import com.seibel.distanthorizons.core.config.ConfigHandler;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.util.AnnotationUtil;

import net.minecraft.command.ICommandSender;



/**
 * Command for managing config.
 */
public class ConfigCommand extends AbstractDhCommand
{
	private static boolean isSelectableEnumValue(ConfigEntry<?> configEntry, Enum<?> enumValue)
	{
		if (AnnotationUtil.doesEnumHaveAnnotation(enumValue, DisallowSelectingViaConfigGui.class))
		{
			return false;
		}

		ConfigEntry.IShowEnumOptionFunc showEnumOptionFunc = configEntry.getShowEnumOptionFunc();
		return showEnumOptionFunc == null || showEnumOptionFunc.shouldShowEnum(enumValue);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setConfigValue(ConfigEntry<?> configEntry, String value)
	{
		Class<?> type = configEntry.getType();

		if (type == Boolean.class)       ((ConfigEntry) configEntry).set(Boolean.parseBoolean(value));
		else if (type == Integer.class)  ((ConfigEntry) configEntry).set(Integer.parseInt(value));
		else if (type == Double.class)   ((ConfigEntry) configEntry).set(Double.parseDouble(value));
		else if (type == Float.class)    ((ConfigEntry) configEntry).set(Float.parseFloat(value));
		else if (type == Long.class)     ((ConfigEntry) configEntry).set(Long.parseLong(value));
		else if (type == String.class)   ((ConfigEntry) configEntry).set(value);
		else if (type.isEnum())
		{
			Enum<?> enumValue = Enum.valueOf((Class<Enum>) type, value);
			if (!isSelectableEnumValue(configEntry, enumValue))
			{
				throw new IllegalArgumentException("This enum value is not available on this Minecraft version.");
			}
			((ConfigEntry) configEntry).set(enumValue);
		}
		else throw new RuntimeException("Unsupported config type: " + type.getSimpleName());
	}

	public void execute(ICommandSender sender, String[] args)
	{
		if (args.length < 2)
		{
			sendMessage(sender, "Usage: /dh config <name> [value]");
			return;
		}

		String configName = args[1];
		AbstractConfigBase<?> found = null;
		for (AbstractConfigBase<?> entry : ConfigHandler.INSTANCE.configBaseList)
		{
			if (entry instanceof ConfigEntry && configName.equals(((ConfigEntry<?>) entry).getChatCommandName()))
			{
				found = entry;
				break;
			}
		}

		if (found == null)
		{
			sendMessage(sender, "Unknown config: " + configName);
			return;
		}

		ConfigEntry<?> configEntry = (ConfigEntry<?>) found;
		if (args.length == 2)
		{
			sendMessage(sender, "Current value of " + configName + " is " + configEntry.get());
		}
		else
		{
			String value = args[2];
			try
			{
				setConfigValue(configEntry, value);
				sendMessage(sender, "Changed " + configName + " to " + value);
			}
			catch (Exception e)
			{
				sendMessage(sender, "Invalid value: " + value);
			}
		}
	}

}
