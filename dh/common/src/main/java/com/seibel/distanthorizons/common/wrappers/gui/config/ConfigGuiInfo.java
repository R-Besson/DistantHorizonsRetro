package com.seibel.distanthorizons.common.wrappers.gui.config;

import com.seibel.distanthorizons.common.wrappers.gui.OnPressed;
import com.seibel.distanthorizons.core.config.gui.IConfigGuiInfo;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

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

	/**
	 * Validates a text box's text and colors it, if the boolean is true the value is also applied. <br>
	 * Returns true if the text is valid
	 */
	public BiFunction<GuiTextField, GuiButton, BiPredicate<String, Boolean>> tooltipFunction;

	/** determines which options the button will show */
	public AbstractMap.SimpleEntry<OnPressed, Function<Object, String>> buttonOptionMap;
}
