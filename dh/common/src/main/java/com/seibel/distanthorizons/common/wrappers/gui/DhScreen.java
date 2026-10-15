package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;


import java.util.List;
import java.util.Collections;

public class DhScreen extends GuiScreen
{
	protected String title;

	protected DhScreen(String title)
	{
		this.title = title;
	}

	protected GuiButton addBtn(GuiButton button)
	{
		this.buttonList.add(button);
		return button;
	}

	@Override
	protected void actionPerformed(GuiButton button)
	{
		OnPressed handler = GuiHelper.HANDLER_BY_BUTTON.get(button);
		if (handler != null)
		{
			handler.pressed(button);
		}
	}

	protected void DhDrawCenteredString(String text, int x, int y, int color) {
		if (StatCollector.canTranslate(text))
		{
			text = StatCollector.translateToLocal(text);
		}
		this.drawCenteredString(this.fontRendererObj, text, x, y, color);
	}

	protected void DhDrawString(String text, int x, int y, int color) {
		if (StatCollector.canTranslate(text))
		{
			text = StatCollector.translateToLocal(text);
		}
		this.drawString(this.fontRendererObj, text, x, y, color);
	}

	protected void DhRenderComponentTooltip(List<String> list, int x, int y)
	{
		drawHoveringText(list, x, y, fontRendererObj);
	}

	protected void DhRenderTooltip(String text, int x, int y)
	{
		drawHoveringText(Collections.singletonList(text), x, y, fontRendererObj);
	}
}
