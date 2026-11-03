package com.seibel.distanthorizons.common.wrappers.gui.classicConfig;

import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.config.types.ConfigUIComment;
import com.seibel.distanthorizons.core.config.types.enums.EConfigCommentTextPosition;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.IConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.SetY;
import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.Translatable;

/*
 * Based upon TinyConfig but is highly modified
 * https://github.com/Minenash/TinyConfig
 *
 * @author coolGi
 * @author Motschen
 * @author James Seibel
 * @version 5-21-2022
 */
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

		public ConfigListWidget(Minecraft minecraftClient, int canvasWidth, int canvasHeight, int topMargin, int botMargin, int itemSpacing)
		{
			super(minecraftClient, canvasWidth, canvasHeight, topMargin, canvasHeight - botMargin, itemSpacing);

			this.field_148163_i = false;
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

		public DhButtonEntry addButton(DhConfigScreen gui, AbstractConfigBase<?> dhConfigType, Gui button, GuiButton resetButton, GuiButton indexButton, String text)
		{
			DhButtonEntry entry = new DhButtonEntry(gui, dhConfigType, button, text, resetButton, indexButton);
			this.children.add(entry);
			return entry;
		}

		@Override
		public int getListWidth()
		{ return 10_000; }

		public Gui getHoveredButton(double mouseX, double mouseY)
		{
			for (DhButtonEntry buttonEntry : this.children)
			{
				if (isHovered(buttonEntry.button, mouseX, mouseY)
					|| isHovered(buttonEntry.valueTextField, mouseX, mouseY))
				{
					return buttonEntry.button;
				}
				// the lock label explains why the option can't be changed
				if (buttonEntry.isLocked()
					&& isHovered(buttonEntry.resetButton, mouseX, mouseY))
				{
					return buttonEntry.button;
				}
			}

			return null;
		}
		private static boolean isHovered(Gui gui, double mouseX, double mouseY)
		{
			double minX, minY, maxX, maxY;
			if (gui instanceof GuiButton)
			{
				GuiButton button = (GuiButton) gui;
				if (!button.visible)
				{
					return false;
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
					return false;
				}

				minX = field.xPosition;
				minY = field.yPosition;
				maxX = minX + field.width;
				maxY = minY + field.height;
			}
			else
			{
				return false;
			}

			return mouseX >= minX && mouseX < maxX && mouseY >= minY && mouseY < maxY;
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
		/**
		 * Gray text shown at the right of a text box, null when nothing is shown.
		 * IE "No Limit" for values where -1 means there's no limit
		 */
		@Nullable
		public Supplier<String> valueHint = null;
		/** a text box next to the main widget, IE for typing the exact value of a slider */
		@Nullable
		public GuiTextField valueTextField = null;

		@NotNull
		private final EConfigCommentTextPosition textPosition;
		public final AbstractConfigBase<?> dhConfigType;

		public static final Map<Gui, String> TEXT_BY_WIDGET = new HashMap<>();
		public static final Map<Gui, DhButtonEntry> BUTTON_BY_WIDGET = new HashMap<>();

		public DhButtonEntry(DhConfigScreen gui, AbstractConfigBase<?> dhConfigType, Gui button, String text, GuiButton resetButton, GuiButton indexButton)
		{
			TEXT_BY_WIDGET.put(button, text);
			BUTTON_BY_WIDGET.put(button, this);

			this.gui = gui;
			this.dhConfigType = dhConfigType;

			this.button = button;
			this.resetButton = resetButton;
			this.text = text;
			this.indexButton = indexButton;

			List<Gui> children = new ArrayList<>();
			if (button != null)
			{
				children.add(button);
			}
			if (resetButton != null)
			{
				children.add(resetButton);
			}
			if (indexButton != null)
			{
				children.add(indexButton);
			}

			EConfigCommentTextPosition textPosition = null;
			if (this.dhConfigType instanceof ConfigUIComment)
			{
				textPosition = ((ConfigUIComment) this.dhConfigType).textPosition;
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
						// the border is drawn around the box, so it's inset to line up with the buttons
						SetY(guiTextField, y + 1);
						guiTextField.drawTextBox();

						String hint = (this.valueHint != null) ? this.valueHint.get() : null;
						if (hint != null)
						{
							textRenderer.drawString(hint,
								guiTextField.xPosition + guiTextField.width - textRenderer.getStringWidth(hint) - 4,
								guiTextField.yPosition + (guiTextField.height - 8) / 2,
								0x808080);
						}
					}
				}

				if (this.valueTextField != null)
				{
					SetY(this.valueTextField, y + 1);
					this.valueTextField.drawTextBox();
				}

				if (this.resetButton != null)
				{
					this.updateResetButton((GuiButton) this.resetButton);
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
						textXPos = this.gui.getLabelRightX() - translatedLength;
					}
					else if (this.textPosition == EConfigCommentTextPosition.CENTERED_OVER_BUTTONS)
					{
						// centered over the rows, like the category buttons
						textXPos = this.gui.getRowLeft()
							+ (this.gui.getRowWidth() / 2)
							- (translatedLength / 2);
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
				RATE_LIMITED_LOGGER.error("Unexpected gui rendering issue: [" + e.getMessage() + "]", e);
			}
		}

		/** true if this MC version or another mod (via the API) decides the value */
		public boolean isLocked()
		{
			if (!(this.dhConfigType instanceof ConfigEntry))
			{
				return false;
			}
			ConfigEntry<?> configEntry = (ConfigEntry<?>) this.dhConfigType;
			return configEntry.mcVersionOverridePresent() || configEntry.apiIsOverriding();
		}

		/**
		 * Reset is only clickable (and red) when the value isn't its default,
		 * so the changed options stand out. Locked options keep their lock text.
		 */
		private void updateResetButton(GuiButton resetButton)
		{
			if (!(this.dhConfigType instanceof ConfigEntry)
				|| this.isLocked())
			{
				return;
			}

			ConfigEntry<?> configEntry = (ConfigEntry<?>) this.dhConfigType;
			boolean isDefault = Objects.equals(configEntry.get(), configEntry.getDefaultValue());
			resetButton.enabled = !isDefault;
			resetButton.displayString = (isDefault ? EnumChatFormatting.DARK_GRAY : EnumChatFormatting.RED) + Translatable("distanthorizons.general.reset");
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
	}

	//endregion
}
