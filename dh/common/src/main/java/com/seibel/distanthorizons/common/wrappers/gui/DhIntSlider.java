package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * A slider for an integer config, drawn like MC's option sliders. <br>
 * The scale is logarithmic so small values get as much room as large ones,
 * IE a render distance from 32 to 4096 chunks. <br>
 * The value is only applied when the slider is let go, changing some configs reloads things.
 */
public class DhIntSlider extends GuiButton
{
	private final ConfigEntry<Integer> configEntry;
	private final int min;
	private final int max;
	private final int step;
	/** turns the value into the text shown on the slider */
	private final IntFunction<String> labelFunc;
	/** called with the value while dragging, before it's applied */
	private final IntConsumer onDrag;

	/** the value while the slider is being dragged, null otherwise */
	@Nullable
	private Integer draggedValue = null;

	/** @param step values are rounded to multiples of this */
	public DhIntSlider(int id, int x, int y, int width, int height,
		ConfigEntry<Integer> configEntry, int step,
		IntFunction<String> labelFunc, IntConsumer onDrag)
	{
		super(id, x, y, width, height, "");
		this.configEntry = configEntry;
		this.min = Math.max(1, configEntry.getMin());
		this.max = Math.max(this.min + 1, configEntry.getMax());
		this.step = Math.max(1, step);
		this.labelFunc = labelFunc;
		this.onDrag = onDrag;
		this.displayString = labelFunc.apply(configEntry.get());
	}

	//=============//
	// value logic //
	//=============//
	//region

	private float valueToFraction(int value)
	{
		int clampedValue = Math.max(this.min, Math.min(this.max, value));
		return (float) (Math.log(clampedValue / (double) this.min) / Math.log(this.max / (double) this.min));
	}

	private int fractionToValue(float fraction)
	{
		double value = this.min * Math.pow(this.max / (double) this.min, fraction);
		int roundedValue = (int) (Math.round(value / this.step) * this.step);
		return Math.max(this.min, Math.min(this.max, roundedValue));
	}

	private void dragTo(int mouseX)
	{
		float fraction = (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
		fraction = Math.max(0.0f, Math.min(1.0f, fraction));

		int value = this.fractionToValue(fraction);
		if (this.draggedValue == null || this.draggedValue != value)
		{
			this.draggedValue = value;
			this.onDrag.accept(value);
		}
	}

	//endregion

	//=====================//
	// MC button overrides //
	//=====================//
	//region

	/** the slider's background is never highlighted, like MC's */
	@Override
	public int getHoverState(boolean mouseOver) { return 0; }

	@Override
	protected void mouseDragged(Minecraft mc, int mouseX, int mouseY)
	{
		if (!this.visible)
		{
			return;
		}

		if (this.draggedValue != null)
		{
			this.dragTo(mouseX);
		}
		// read back every frame so the reset button and the text box show up here
		int value = (this.draggedValue != null) ? this.draggedValue : this.configEntry.get();
		this.displayString = this.labelFunc.apply(value);

		int knobX = this.xPosition + (int) (this.valueToFraction(value) * (this.width - 8));
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		this.drawTexturedModalRect(knobX, this.yPosition, 0, 66, 4, 20);
		this.drawTexturedModalRect(knobX + 4, this.yPosition, 196, 66, 4, 20);
	}

	@Override
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY)
	{
		if (!super.mousePressed(mc, mouseX, mouseY))
		{
			return false;
		}

		this.dragTo(mouseX);
		return true;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY)
	{
		if (this.draggedValue == null)
		{
			return;
		}

		// setting a value runs its listeners even when it's the same
		if (!this.draggedValue.equals(this.configEntry.get()))
		{
			this.configEntry.uiSetWithoutSaving(this.draggedValue);
		}
		this.draggedValue = null;
	}

	//endregion
}
