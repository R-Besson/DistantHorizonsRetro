package com.seibel.distanthorizons.common.wrappers.gui.classicConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.seibel.distanthorizons.core.config.types.*;

import com.seibel.distanthorizons.core.config.types.enums.EConfigCommentTextPosition;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.world.World;
import com.seibel.distanthorizons.core.logging.DhLogger;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.renderer.Tessellator;



import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.*;


/*
 * Based upon TinyConfig but is highly modified
 * https://github.com/Minenash/TinyConfig
 *
 * @author coolGi
 * @author Motschen
 * @author James Seibel
 * @version 5-21-2022
 */
@SuppressWarnings("unchecked")
public class ClassicConfigGUI
{
	public static final DhLogger RATE_LIMITED_LOGGER = new DhLoggerBuilder()
			.maxCountPerSecond(1)
			.build();

	public static final ConfigCoreInterface CONFIG_CORE_INTERFACE = new ConfigCoreInterface();



	//==============//
	// Initializers //
	//==============//
	//region

	// Some regexes to check if an input is valid
	public static final Pattern INTEGER_ONLY_REGEX = Pattern.compile("(-?[0-9]*)");
	public static final Pattern DECIMAL_ONLY_REGEX = Pattern.compile("-?([\\d]+\\.?[\\d]*|[\\d]*\\.?[\\d]+|\\.)");

	public static class ConfigScreenConfigs
	{
		// This contains all the configs for the configs
		public static final int SPACE_FROM_RIGHT_SCREEN = 10;
		public static final int SPACE_BETWEEN_TEXT_AND_OPTION_FIELD = 8;
		public static final int BUTTON_WIDTH_SPACING = 5;
		public static final int RESET_BUTTON_WIDTH = 60;
		public static final int RESET_BUTTON_HEIGHT = 20;
		public static final int OPTION_FIELD_WIDTH = 150;
		public static final int OPTION_FIELD_HEIGHT = 20;
		public static final int CATEGORY_BUTTON_WIDTH = 200;
		public static final int CATEGORY_BUTTON_HEIGHT = 20;

	}

	//endregion



	//==============//
	// GUI handling //
	//==============//
	//region
	/** if you want to get this config gui's screen call this */
	public static GuiScreen getScreen(GuiScreen parent, String category)
	{ return new DhConfigScreen(parent, category); }

	//endregion



	//================//
	// helper classes //
	//================//
	//region

	public static class ConfigListWidget extends GuiListExtended
	{
		public List<DhButtonEntry> children = new ArrayList<>();

		FontRenderer textRenderer;

		public ConfigListWidget(Minecraft minecraftClient, int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{
			super(minecraftClient, canvasWidth, canvasHeight, topMargin, canvasHeight - botMargin, itemSpacing);

			this.field_148163_i = false;

			this.textRenderer = minecraftClient.fontRendererObj;
		}

		@Override
		protected int getSize()
		{
			return this.children.size();
		}

		@Override
		public IGuiListEntry getListEntry(int index)
		{
			return this.children.get(index);
		}

		@Override
		protected void drawContainerBackground(Tessellator tessellator)
		{
			World world = Minecraft.getMinecraft().theWorld;
			if (world != null)
			{
				return; // in-game don't draw dirt background
			}
			super.drawContainerBackground(tessellator);
		}

		public void addButton(DhConfigScreen gui, AbstractConfigBase dhConfigType, Gui button, GuiButton resetButton, GuiButton indexButton, String text)
		{
			this.children.add(new DhButtonEntry(gui, dhConfigType, button, text, resetButton, indexButton));
		}

		@Override
		public int getListWidth()
		{ return 10_000; }

		public Gui getHoveredButton(double mouseX, double mouseY)
		{
			for (DhButtonEntry buttonEntry : this.children)
			{
				Gui gui = buttonEntry.button;
				if (gui == null) continue;

				double minX, minY, maxX, maxY;

				if (gui instanceof GuiButton)
				{
					GuiButton button = (GuiButton) gui;
					if (!button.visible)
					{
						continue;
					}

					minX = button.xPosition;
					minY = button.yPosition;
					maxX = minX + button.width;
					maxY = minY + button.height;
				}
				else if (gui instanceof GuiTextField)
				{
					GuiTextField field = (GuiTextField) gui;
					if (!field.getVisible())
					{
						continue;
					}

					minX = field.xPosition;
					minY = field.yPosition;
					maxX = minX + field.width;
					maxY = minY + field.height;
				}
				else
				{
					continue;
				}

				if (mouseX >= minX && mouseX < maxX && mouseY >= minY && mouseY < maxY)
				{
					return gui;
				}
			}

			return null;
		}

	}

	public static class DhButtonEntry implements GuiListExtended.IGuiListEntry
	{
		private static final FontRenderer textRenderer = Minecraft.getMinecraft().fontRendererObj;

		private final DhConfigScreen gui;
		public final Gui button;
		public final Gui resetButton;
		public final Gui indexButton;

		private final String text;

		private final List<Gui> children = new ArrayList<>();

		@NotNull
		private final EConfigCommentTextPosition textPosition;
		public final AbstractConfigBase dhConfigType;

		public static final Map<Gui, String> TEXT_BY_WIDGET = new HashMap<>();
		public static final Map<Gui, DhButtonEntry> BUTTON_BY_WIDGET = new HashMap<>();



		public DhButtonEntry(DhConfigScreen gui, AbstractConfigBase dhConfigType, Gui button, String text, GuiButton resetButton, GuiButton indexButton)
		{
			TEXT_BY_WIDGET.put(button, text);
			BUTTON_BY_WIDGET.put(button, this);

			this.gui = gui;
			this.dhConfigType = dhConfigType;

			this.button = button;
			this.resetButton = resetButton;
			this.text = text;
			this.indexButton = indexButton;

			if (button != null) { this.children.add(button); }
			if (resetButton != null) { this.children.add(resetButton); }
			if (indexButton != null) { this.children.add(indexButton); }


			EConfigCommentTextPosition textPosition = null;
			if (this.dhConfigType instanceof ConfigUIComment)
			{
				textPosition = ((ConfigUIComment)this.dhConfigType).textPosition;
			}

			if (textPosition == null)
			{
				if (this.button != null)
				{
					// if a button is present
					textPosition = EConfigCommentTextPosition.RIGHT_JUSTIFIED;
				}
				else
				{
					textPosition = EConfigCommentTextPosition.CENTERED_OVER_BUTTONS;
				}
			}
			this.textPosition = textPosition;

		}



		@Override
		public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
		{
			try
			{
				// setting the "y" variable is necessary so each child item
				// renders at the correct height,
				// if not set they will render off-screen.

				if (this.button != null)
				{
					if (this.button instanceof GuiButton)
					{
						GuiButton guiButton = (GuiButton) this.button;

						SetY(guiButton, y);
						guiButton.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
					}
					if (this.button instanceof GuiTextField)
					{
						GuiTextField guiTextField = (GuiTextField) this.button;
						SetY(guiTextField, y);
						guiTextField.drawTextBox();
					}
				}

				if (this.resetButton != null)
				{
					SetY((GuiButton) this.resetButton, y);

					((GuiButton) this.resetButton).drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
				}

				if (this.indexButton != null)
				{
					SetY((GuiButton) this.indexButton, y);

					((GuiButton) this.indexButton).drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
				}

				if (this.text != null)
				{
					int translatedLength = textRenderer.getStringWidth(this.text);

					int textXPos;
					if (this.textPosition == EConfigCommentTextPosition.RIGHT_JUSTIFIED)
					{
						// text right justified aligned against the buttons
						textXPos = this.gui.width
								- translatedLength
								- ConfigScreenConfigs.SPACE_BETWEEN_TEXT_AND_OPTION_FIELD
								- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN
								- ConfigScreenConfigs.OPTION_FIELD_WIDTH
								- ConfigScreenConfigs.BUTTON_WIDTH_SPACING
								- ConfigScreenConfigs.RESET_BUTTON_WIDTH;
					}
					else if (this.textPosition == EConfigCommentTextPosition.CENTERED_OVER_BUTTONS)
					{
						// have button centered relative to a category button
						textXPos = this.gui.width
								- (translatedLength / 2)
								- (ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH / 2)
								- ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
					}
					else if (this.textPosition == EConfigCommentTextPosition.CENTER_OF_SCREEN)
					{
						// have button centered in the screen
						textXPos = (this.gui.width / 2)
								- (translatedLength / 2);
					}
					else
					{
						throw new UnsupportedOperationException("No text position render defined for [" + this.textPosition + "]");
					}

				textRenderer.drawString(
						this.text,
						textXPos, y + 5,
						0xFFFFFF);
				}
			}
			catch (Exception e)
			{
				// should prevent crashing the game if there's an issue
				RATE_LIMITED_LOGGER.error("Unexpected gui rendering issue: ["+e.getMessage()+"]", e);
			}
		}


		@Override
		public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY)
		{ return false; /* handled in DhConfigScreen.mouseClicked */ }

		@Override
		public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) { }

	}

	//endregion



	//================//
	// event handling //
	//================//
	//region

	public static class ConfigCoreInterface implements IConfigGui
	{
		/**
		 * in the future it would be good to pass in the current page and other variables,
		 * but for now just knowing when the page is closed is good enough
		 */
		public final ArrayList<Runnable> onScreenChangeListenerList = new ArrayList<>();



		@Override
		public void addOnScreenChangeListener(Runnable newListener) { this.onScreenChangeListenerList.add(newListener); }
		@Override
		public void removeOnScreenChangeListener(Runnable oldListener) { this.onScreenChangeListenerList.remove(oldListener); }

	}

	//endregion
}
