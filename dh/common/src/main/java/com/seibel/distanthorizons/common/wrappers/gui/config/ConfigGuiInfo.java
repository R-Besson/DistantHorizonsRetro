package com.seibel.distanthorizons.common.wrappers.gui.config;

import com.seibel.distanthorizons.core.config.gui.IConfigGuiInfo;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
import com.seibel.distanthorizons.common.wrappers.gui.OnPressed;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * holds information needed by the config GUI for rendering.
 *
 * @see AbstractConfigBase
 */
public class ConfigGuiInfo implements IConfigGuiInfo
{
	/**
	 * Used to display validation errors.
	 * Null if no error is present.
	 */

	@Nullable
	public String errorMessage;

	public BiFunction<GuiTextField, GuiButton, Predicate<String>> tooltipFunction;

	/** determines which options the button will show */
	public AbstractMap.SimpleEntry<OnPressed, Function<Object, String>> buttonOptionMap;

}
