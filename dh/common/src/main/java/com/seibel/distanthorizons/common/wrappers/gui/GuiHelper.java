package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import java.util.HashMap;
import java.util.Map;


/**
 * Helper static methods for building GUIs
 */
public class GuiHelper
{
	public static final Map<GuiButton, OnPressed> HANDLER_BY_BUTTON = new HashMap<>();

	public static GuiButton MakeBtn(String base, int posX, int posZ, int width, int height, OnPressed action)
	{
		GuiButton button = new GuiButton(
			HANDLER_BY_BUTTON.size(),
			posX, posZ,
			width, height,
			base
		);
		HANDLER_BY_BUTTON.put(button, action);
		return button;
	}

	public static String TextOrLiteral(String text)
	{
		return text;
	}

	public static String TextOrTranslatable(String text)
	{
		if (StatCollector.canTranslate(text))
		{
			return StatCollector.translateToLocal(text);
		}
		return text;
	}

	public static String Translatable(String text, Object... args)
	{
		return StatCollector.translateToLocalFormatted(text, args);
	}

	public static void SetX(GuiButton widget, int x)
	{
		widget.xPosition = x;
	}

	public static void SetY(GuiTextField textField, int y)
	{
		textField.yPosition = y;
	}

	public static void SetY(GuiButton widget, int y)
	{
		widget.yPosition = y;
	}



}
