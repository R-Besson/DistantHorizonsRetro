package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Objects;

public class DhScreenUtil
{
	//================//
	// helper methods //
	//================//
	//region

	public static void setScreen(GuiScreen screen)
	{
		Objects.requireNonNull(Minecraft.getMinecraft()).displayGuiScreen(screen);
	}

	//endregion



}
