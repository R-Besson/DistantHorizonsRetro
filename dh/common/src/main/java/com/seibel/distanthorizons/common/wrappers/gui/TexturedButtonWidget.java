/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.gui;



import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Creates a button with a texture on it (and a background)
 *
 * @author coolGi
 * @version 2023-10-03
 */
public class TexturedButtonWidget
	extends GuiButton
{
	public final boolean renderBackground;

	private final int u;
	private final int v;
	private final int hoveredVOffset;

	private final int textureWidth;
	private final int textureHeight;

	private final ResourceLocation textureResourceLocation;



	//==============//
	// constructors //
	//==============//
	//region

	public TexturedButtonWidget(int id, int x, int y, int width, int height, int u, int v, int hoveredVOffset, ResourceLocation textureResourceLocation, int textureWidth, int textureHeight, String text)
	{ this(id, x, y, width, height, u, v, hoveredVOffset, textureResourceLocation, textureWidth, textureHeight, text, true); }

	public TexturedButtonWidget(int id, int x, int y, int width, int height, int u, int v, int hoveredVOffset, ResourceLocation textureResourceLocation, int textureWidth, int textureHeight, String text, boolean renderBackground)
	{
		super(id, x, y, width, height, text);

		this.u = u;
		this.v = v;
		this.hoveredVOffset = hoveredVOffset;

		this.textureResourceLocation = textureResourceLocation;

		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;

		this.renderBackground = renderBackground;
	}

	//endregion



	//===========//
	// rendering //
	//===========//
	//region

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY)
	{
		if (this.visible)
		{
			this.hovered =
				mouseX >= this.xPosition
					&& mouseY >= this.yPosition
					&& mouseX < this.xPosition + this.width
					&& mouseY < this.yPosition + this.height;
			int hoverState = this.getHoverState(this.hovered);

			LWJGL.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			LWJGL.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(770, 771, 1, 0);
			LWJGL.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			if (this.renderBackground)
			{
				mc.getTextureManager().bindTexture(buttonTextures);
				this.drawTexturedModalRect(
					this.xPosition, this.yPosition,
					0, 46 + hoverState * 20,
					this.width / 2, this.height);
				this.drawTexturedModalRect(
					this.xPosition + this.width / 2, this.yPosition,
					200 - this.width / 2, 46 + hoverState * 20,
					this.width / 2, this.height);
			}

			mc.getTextureManager().bindTexture(this.textureResourceLocation);
			drawModalRectWithCustomSizedTexture(this.xPosition, this.yPosition, this.u, this.v + (this.hoveredVOffset * this.getIconHoverState(this.hovered)), this.width, this.height, this.textureWidth, this.textureHeight);
		}
	}
	private int getIconHoverState(boolean mouseOver)
	{
		if (!this.enabled || mouseOver)
		{
			return 1;
		}
		return 0;
	}


	//endregion



}
