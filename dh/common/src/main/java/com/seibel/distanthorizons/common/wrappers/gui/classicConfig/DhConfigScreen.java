package com.seibel.distanthorizons.common.wrappers.gui.classicConfig;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import com.seibel.distanthorizons.common.wrappers.gui.*;
import com.seibel.distanthorizons.common.wrappers.gui.config.ConfigGuiInfo;
import com.seibel.distanthorizons.core.config.ConfigHandler;
import com.seibel.distanthorizons.core.config.types.*;
import com.seibel.distanthorizons.core.config.types.enums.EConfigValidity;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.AnnotationUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.client.gui.*;
import net.minecraft.util.EnumChatFormatting;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.Nullable;


import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.*;

class DhConfigScreen extends DhScreen
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final ILangWrapper LANG_WRAPPER = SingletonInjector.INSTANCE.get(ILangWrapper.class);

	private static final String TRANSLATION_PREFIX = ModInfo.ID + ".config.";

	private final GuiScreen parent;

	private final String category;
	private ClassicConfigGUI.ConfigListWidget configListWidget;
	private boolean reload = false;

	private GuiButton doneButton;
	private final Map<GuiTextField, Predicate<String>> textFieldProcessors = new HashMap<>();



	//=============//
	// constructor //
	//=============//
	//region

	protected DhConfigScreen(GuiScreen parent, String category)
	{
		super(Translatable(
			LANG_WRAPPER.langExists(ModInfo.ID + ".config" + (category.isEmpty() ? "." + category : "") + ".title") ?
				ModInfo.ID + ".config.title" :
				ModInfo.ID + ".config" + (category.isEmpty() ? "" : "." + category) + ".title")
		);
		this.parent = parent;
		this.category = category;
	}

	//endregion



	//===================//
	// menu UI lifecycle //
	//===================//
	//region

	@Override
	public void updateScreen()
	{
		super.updateScreen();

		for (GuiTextField field : this.textFieldProcessors.keySet())
		{
			field.updateCursorCounter();
		}
	}

	//endregion



	//==================//
	// menu UI creation //
	//==================//
	//region

	@Override
	public void initGui()
	{
		super.initGui();

		if (!this.reload)
		{
			ConfigHandler.INSTANCE.configFileHandler.loadFromFile();
		}



		// back button
		this.addBtn(MakeBtn(Translatable("distanthorizons.general.back"),
			(this.width / 2) - 154, this.height - 28,
			ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_HEIGHT,
			(button) ->
			{
				ConfigHandler.INSTANCE.configFileHandler.loadFromFile();
				DhScreenUtil.setScreen(this.parent);
			}));

		// done/close button
		this.doneButton = this.addBtn(
			MakeBtn(Translatable("distanthorizons.general.done"),
				(this.width / 2) + 4, this.height - 28,
				ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_HEIGHT,
				(button) ->
				{
					ConfigHandler.INSTANCE.configFileHandler.saveToFile();
					DhScreenUtil.setScreen(this.parent);
				}));

		this.configListWidget = new ClassicConfigGUI.ConfigListWidget(this.mc, this.width * 2, this.height, 32, 32, 25);



		for (AbstractConfigBase<?> configEntry : ConfigHandler.INSTANCE.configBaseList)
		{
			try
			{
				if (configEntry.getCategory().matches(this.category)
					&& configEntry.getAppearance().showInGui)
				{
					this.addMenuItem(configEntry);
				}
			}
			catch (Exception e)
			{
				String message = "ERROR: Failed to show [" + configEntry.getNameAndCategory() + "], error: [" + e.getMessage() + "]";
				if (configEntry.get() != null)
				{
					message += " with the value [" + configEntry.get() + "] with type [" + configEntry.getType() + "]";
				}

				LOGGER.error(message, e);
			}
		}

		ClassicConfigGUI.CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
	}
	private void addMenuItem(AbstractConfigBase<?> configEntry)
	{
		trySetupConfigEntry(configEntry);

		if (this.tryCreateInputField(configEntry)) return;
		if (this.tryCreateCategoryButton(configEntry)) return;
		if (this.tryCreateButton(configEntry)) return;
		if (this.tryCreateComment(configEntry)) return;
		if (this.tryCreateSpacer(configEntry)) return;
		if (this.tryCreateLinkedEntry(configEntry)) return;

		LOGGER.warn("Config [" + configEntry.getNameAndCategory() + "] failed to show. Please try something like changing its type.");
	}

	private static void trySetupConfigEntry(AbstractConfigBase<?> configMenuOption)
	{
		configMenuOption.guiValue = new ConfigGuiInfo();
		Class<?> configValueClass = configMenuOption.getType();

		if (configMenuOption instanceof ConfigEntry)
		{
			ConfigEntry<?> configEntry = (ConfigEntry<?>) configMenuOption;

			if (configValueClass == Integer.class)
			{
				setupTextMenuOption(configEntry, Integer::parseInt, ClassicConfigGUI.INTEGER_ONLY_REGEX, true);
			}
			else if (configValueClass == Double.class)
			{
				setupTextMenuOption(configEntry, Double::parseDouble, ClassicConfigGUI.DECIMAL_ONLY_REGEX, false);
			}
			else if (configValueClass == Float.class)
			{
				setupTextMenuOption(configEntry, Float::parseFloat, ClassicConfigGUI.DECIMAL_ONLY_REGEX, false);
			}
			else if (configValueClass == String.class || configValueClass == List.class)
			{
				// For string or list
				setupTextMenuOption(configEntry, String::length, null, true);
			}
			else if (configValueClass == Boolean.class)
			{
				ConfigEntry<Boolean> booleanConfigEntry = (ConfigEntry<Boolean>) configEntry;
				setupBooleanMenuOption(booleanConfigEntry);
			}
			else if (configValueClass.isEnum())
			{
				ConfigEntry<Enum<?>> enumConfigEntry = (ConfigEntry<Enum<?>>) configEntry;
				Class<? extends Enum<?>> configEnumClass = (Class<? extends Enum<?>>) configValueClass;
				setupEnumMenuOption(enumConfigEntry, configEnumClass);
			}
			else
			{
				LOGGER.error("No definition for config with type: [" + configValueClass.getName() + "], for config: [" + configMenuOption.name + "].");
			}
		}

	}
	private static void setupTextMenuOption(AbstractConfigBase<?> configMenuOption, Function<String, Number> parsingFunc, @Nullable Pattern pattern, boolean cast)
	{
		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configMenuOption.guiValue);

		configGuiInfo.tooltipFunction =
			(editBox, button) ->
				(stringValue) ->
				{
					boolean isNumber = (pattern != null);

					stringValue = stringValue.trim();
					if (!(stringValue.isEmpty() || !isNumber || pattern.matcher(stringValue).matches()))
					{
						return false;
					}


					Number numberValue = configMenuOption.typeIsFloatingPointNumber() ? 0.0 : 0; // different default values are needed so implicit casting works correctly (if not done casting from 0 (an int) to a double will cause an exception)
					configGuiInfo.errorMessage = null;
					if (isNumber
						&& !stringValue.isEmpty()
						&& !stringValue.equals("-")
						&& !stringValue.equals("."))
					{
						ConfigEntry<Number> numberConfigEntry = (ConfigEntry<Number>) configMenuOption;

						try
						{
							numberValue = parsingFunc.apply(stringValue);
						}
						catch (Exception e)
						{
							numberValue = null;
						}

						EConfigValidity validity = numberConfigEntry.getValidity(numberValue);
						switch (validity)
						{
							case VALID:
								configGuiInfo.errorMessage = null;
								break;
							case NUMBER_TOO_LOW:
								configGuiInfo.errorMessage = TextOrTranslatable("§cMinimum length is " + numberConfigEntry.getMin());
								break;
							case NUMBER_TOO_HIGH:
								configGuiInfo.errorMessage = TextOrTranslatable("§cMaximum length is " + numberConfigEntry.getMax());
								break;
							case INVALID:
								configGuiInfo.errorMessage = TextOrTranslatable("§cValue is invalid");
								break;
						}
					}

					editBox.setTextColor(((ConfigEntry<Number>) configMenuOption).getValidity(numberValue) == EConfigValidity.VALID ? 0xFFFFFFFF : 0xFFFF7777); // white and red


					if (configMenuOption.getType() == String.class
						|| configMenuOption.getType() == List.class)
					{
						((ConfigEntry<String>) configMenuOption).uiSetWithoutSaving(stringValue);
					}
					else if (((ConfigEntry<Number>) configMenuOption).getValidity(numberValue) == EConfigValidity.VALID)
					{
						if (!cast)
						{
							((ConfigEntry<Number>) configMenuOption).uiSetWithoutSaving(numberValue);
						}
						else
						{
							((ConfigEntry<Number>) configMenuOption).uiSetWithoutSaving(numberValue != null ? numberValue.intValue() : 0);
						}
					}

					return true;
				};
	}
	private static void setupBooleanMenuOption(ConfigEntry<Boolean> booleanConfigEntry)
	{
		// For boolean
		Function<Object, String> func = value -> (((Boolean) value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + Translatable("distanthorizons.general."+((Boolean) value ? "true" : "false")));

		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) booleanConfigEntry.guiValue);

		configGuiInfo.buttonOptionMap =
			new AbstractMap.SimpleEntry<OnPressed, Function<Object, String>>(
				(button) ->
				{
					button.enabled = !booleanConfigEntry.apiIsOverriding();

					booleanConfigEntry.uiSetWithoutSaving(!booleanConfigEntry.get());

					button.displayString = func.apply(booleanConfigEntry.get());
				}, func);
	}
	private static void setupEnumMenuOption(ConfigEntry<Enum<?>> enumConfigEntry, Class<? extends Enum<?>> enumClass)
	{
		List<Enum<?>> enumList = Arrays.asList(enumClass.getEnumConstants());

		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) enumConfigEntry.guiValue);

		String translatableEnumPrefix = TRANSLATION_PREFIX + "enum." + enumClass.getSimpleName() + ".";
		Function<Object, String> getEnumTranslatableFunc = (value) -> Translatable(translatableEnumPrefix + value.toString());

		configGuiInfo.buttonOptionMap =
			new AbstractMap.SimpleEntry<OnPressed, Function<Object, String>>(
				(button) ->
				{
					// get the currently selected enum and enum index
					int startingIndex = enumList.indexOf(enumConfigEntry.get());
					Enum<?> enumValue = enumList.get(startingIndex);

					boolean shiftPressed = GuiScreen.isShiftKeyDown();

					// move forward or backwards depending on if the shift key is pressed
					int index = shiftPressed ? startingIndex - 1 : startingIndex + 1;

					// wrap around to the other side of the array when necessary
					if (index >= enumList.size())
					{
						index = 0;
					}
					else if (index < 0)
					{
						index = enumList.size() - 1;
					}


					// walk through the enums to find the next selectable one
					while (index != startingIndex)
					{
						enumValue = enumList.get(index);

						// does enum have ignore attribute?
						boolean selectable = !AnnotationUtil.doesEnumHaveAnnotation(enumValue, DisallowSelectingViaConfigGui.class);
						if (selectable)
						{
							// does this enum have a show function?
							ConfigEntry.IShowEnumOptionFunc showEnumFunc = enumConfigEntry.getShowEnumOptionFunc();
							if (showEnumFunc != null)
							{
								selectable = showEnumFunc.shouldShowEnum(enumValue);
							}
						}

						if (selectable)
						{
							// this enum is a valid option
							break;
						}


						// move forward or backwards depending on if the shift key is pressed
						index = shiftPressed ? index - 1 : index + 1;

						// wrap around to the other side of the array when necessary
						if (index >= enumList.size())
						{
							index = 0;
						}
						else if (index < 0)
						{
							index = enumList.size() - 1;
						}
					}


					if (index == startingIndex)
					{
						// one of the enums should be selectable, this is a programmer error
						enumValue = enumList.get(startingIndex);
						LOGGER.warn("Enum [" + enumValue.getClass() + "] doesn't contain any values that should be selectable via the UI, sticking to the currently selected value [" + enumValue + "].");
					}


					enumConfigEntry.uiSetWithoutSaving(enumValue);

					button.enabled = !enumConfigEntry.apiIsOverriding();
					button.displayString = getEnumTranslatableFunc.apply(enumConfigEntry.get());
				}, getEnumTranslatableFunc);
	}

	private boolean tryCreateInputField(AbstractConfigBase<?> configBase)
	{
		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configBase.guiValue);

		if (configBase instanceof ConfigEntry)
		{
			ConfigEntry configEntry = (ConfigEntry) configBase;


			//==============//
			// reset button //
			//==============//
			//region

			OnPressed btnAction = (button) ->
			{
				configEntry.uiSetWithoutSaving(configEntry.getDefaultValue());
				this.reload = true;
				DhScreenUtil.setScreen(this);
			};

			int resetButtonPosX = this.width
				- ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_WIDTH
				- ClassicConfigGUI.ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
			int resetButtonPosZ = 0;

			GuiButton resetButton = MakeBtn(
				EnumChatFormatting.RED + Translatable("distanthorizons.general.reset"),
				resetButtonPosX, resetButtonPosZ,
				ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_HEIGHT,
				btnAction
			);


			if (configEntry.mcVersionOverridePresent())
			{
				resetButton.displayString = EnumChatFormatting.DARK_GRAY + Translatable("distanthorizons.general.unsupportedMcVersion");
				resetButton.enabled = false;
			}
			else if (configEntry.apiIsOverriding())
			{
				resetButton.displayString = EnumChatFormatting.DARK_GRAY + Translatable("distanthorizons.general.apiOverride");
				resetButton.enabled = false;
			}
			else
			{
				resetButton.enabled = true;
			}

			//endregion



			//==============//
			// option field //
			//==============//
			//region

			String textComponent = this.GetTranslatableTextComponentForConfig(configEntry);

			int optionFieldPosX = this.width
				- ClassicConfigGUI.ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN
				- ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_WIDTH
				- ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING
				- ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH;
			int optionFieldPosZ = 0;

			if (configGuiInfo.buttonOptionMap != null)
			{
				// enum/multi option input button
				Map.Entry<OnPressed, Function<Object, String>> widget = configGuiInfo.buttonOptionMap;

				if (configEntry.getType().isEnum())
				{
					widget.setValue((value) -> Translatable(TRANSLATION_PREFIX + "enum." + configEntry.getType().getSimpleName() + "." + value.toString()));
				}

				GuiButton button = MakeBtn(
					widget.getValue().apply(configEntry.get()),
					optionFieldPosX, optionFieldPosZ,
					ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
					widget.getKey());


				// deactivate the button if the API is overriding it
				// or the MC version doesn't support it
				if (configEntry.mcVersionOverridePresent()
					|| configEntry.apiIsOverriding())
				{
					button.enabled = false;
				}



				this.configListWidget.addButton(this, configEntry,
					button,
					resetButton,
					null,
					textComponent);

				return true;
			}
			else
			{
				// text box input
				GuiTextField widget = new GuiTextField(this.fontRendererObj,
					optionFieldPosX, optionFieldPosZ,
					ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH - 4, ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT);
				widget.setMaxStringLength(3_000_000); // hopefully 3 million characters should be enough for any normal use-case, lol
				widget.setText(String.valueOf(configEntry.get()));

				Predicate<String> processor = configGuiInfo.tooltipFunction.apply(widget, this.doneButton);
				this.textFieldProcessors.put(widget, processor);

				this.configListWidget.addButton(this, configEntry, widget, resetButton, null, textComponent);

				return true;
			}

			//endregion
		}

		return false;
	}
	private boolean tryCreateCategoryButton(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigCategory)
		{
			ConfigCategory configCategory = (ConfigCategory) configType;

			String textComponent = this.GetTranslatableTextComponentForConfig(configCategory);

			int categoryPosX = this.width - ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH - ClassicConfigGUI.ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;
			int categoryPosZ = this.height - ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT; // Note: the posZ value here seems to be ignored

			GuiButton widget = MakeBtn(
				textComponent,
				categoryPosX, categoryPosZ,
				ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
				((button) ->
				{
					ConfigHandler.INSTANCE.configFileHandler.saveToFile();
					DhScreenUtil.setScreen(ClassicConfigGUI.getScreen(this, configCategory.getDestination()));
				}));
			this.configListWidget.addButton(this, configType, widget, null, null, null);

			return true;
		}

		return false;
	}
	private boolean tryCreateButton(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigUIButton)
		{
			ConfigUIButton configUiButton = (ConfigUIButton) configType;

			String textComponent = this.GetTranslatableTextComponentForConfig(configUiButton);

			int buttonPosX = this.width - ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH - ClassicConfigGUI.ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN;

			GuiButton widget = MakeBtn(
				textComponent,
				buttonPosX, this.height - 28,
				ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
				(button) -> ((ConfigUIButton) configType).runAction());
			this.configListWidget.addButton(this, configType, widget, null, null, null);

			return true;
		}

		return false;
	}
	private boolean tryCreateComment(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigUIComment)
		{
			ConfigUIComment configUiComment = (ConfigUIComment) configType;

			String textComponent = this.GetTranslatableTextComponentForConfig(configUiComment);
			if (configUiComment.parentConfigPath != null)
			{
				textComponent = Translatable(TRANSLATION_PREFIX + configUiComment.parentConfigPath);
			}

			this.configListWidget.addButton(this, configType, null, null, null, textComponent);

			return true;
		}

		return false;
	}
	private boolean tryCreateSpacer(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigUISpacer)
		{
			GuiButton spacerButton = MakeBtn(
				Translatable("distanthorizons.general.spacer"),
				10, 10, // having too small of a size can cause division by 0 errors
				1, 1,
				(button) -> { });

			spacerButton.visible = false;
			this.configListWidget.addButton(this, configType, spacerButton, null, null, null);

			return true;
		}

		return false;
	}
	private boolean tryCreateLinkedEntry(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigUiLinkedEntry)
		{
			this.addMenuItem(((ConfigUiLinkedEntry) configType).get());

			return true;
		}

		return false;
	}

	private String GetTranslatableTextComponentForConfig(AbstractConfigBase<?> configType)
	{ return Translatable(TRANSLATION_PREFIX + configType.getNameAndCategory()); }

	//endregion



	//===========//
	// rendering //
	//===========//
	//region

	@Override
	public void drawScreen(int mouseX, int mouseY, float delta)
	{
		this.drawDefaultBackground();

		// Render buttons
		this.configListWidget.drawScreen(mouseX, mouseY, delta);


		// Render config title
		this.DhDrawCenteredString(
			this.title,
			this.width / 2, 15,
			0xFFFFFF // RGB white
		);


		// render DH version
		this.DhDrawString(
			TextOrLiteral(ModInfo.VERSION), 2, this.height - 10,
			0xAAAAAA // RGB gray
		);
		super.drawScreen(mouseX, mouseY, delta);

		this.renderTooltip(mouseX, mouseY, delta);
	}

	private void renderTooltip(int mouseX, int mouseY, float delta)
	{
		Gui hoveredWidget = this.configListWidget.getHoveredButton(mouseX, mouseY);
		if (hoveredWidget == null)
		{
			return;
		}


		ClassicConfigGUI.DhButtonEntry button = ClassicConfigGUI.DhButtonEntry.BUTTON_BY_WIDGET.get(hoveredWidget);


		// A quick fix for tooltips on linked entries
		AbstractConfigBase<?> configBase = ConfigUiLinkedEntry.class.isAssignableFrom(button.dhConfigType.getClass()) ?
			((ConfigUiLinkedEntry) button.dhConfigType).get() :
			button.dhConfigType;

		boolean apiOverrideActive = false;
		boolean unsupportedMcVersion = false;
		String apiUser = null;
		if (configBase instanceof ConfigEntry)
		{
			apiOverrideActive = ((ConfigEntry<?>) configBase).apiIsOverriding();
			unsupportedMcVersion = ((ConfigEntry<?>) configBase).mcVersionOverridePresent();
			apiUser = ((ConfigEntry<?>) configBase).getApiUser();
		}
		if (apiUser == null
			|| apiUser.isEmpty())
		{
			apiUser = "UNKNOWN";
		}

		String key = TRANSLATION_PREFIX + (configBase.category.isEmpty() ? "" : configBase.category + ".") + configBase.getName() + ".@tooltip";

		if (unsupportedMcVersion)
		{
			key = "distanthorizons.general.unsupportedMcVersion.@tooltip";
		}
		else if (apiOverrideActive)
		{
			key = "distanthorizons.general.disabledByApi.@tooltip";
		}

		// display the validation error tooltip if present
		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) configBase.guiValue);
		if (configGuiInfo.errorMessage != null)
		{
			this.DhRenderTooltip(configGuiInfo.errorMessage, mouseX, mouseY);
		}
		// display the tooltip if present
		else if (LANG_WRAPPER.langExists(key))
		{
			List<String> list = new ArrayList<>();

			// MC doesn't convert \n to new lines so we must do it manually
			String lang = LANG_WRAPPER.getLang(key).replace("\\n", "\n");
			for (String langLine : lang.split("\n"))
			{
				list.add(TextOrTranslatable(langLine));
			}

			if (apiOverrideActive)
			{
				// raw text is used here since we don't want to force
				// API users to translate their name
				list.add(TextOrLiteral(MinecraftTextFormat.ORANGE + apiUser + MinecraftTextFormat.CLEAR_FORMATTING));
			}

			this.DhRenderComponentTooltip(list, mouseX, mouseY);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
	{
		super.mouseClicked(mouseX, mouseY, mouseButton);

		if (mouseY >= this.configListWidget.top
			&& mouseY <= this.configListWidget.bottom)
		{
			for (ClassicConfigGUI.DhButtonEntry entry : this.configListWidget.children)
			{
				if (entry.button instanceof GuiButton
					&& ((GuiButton)entry.button).visible)
				{
					GuiButton btn = (GuiButton)entry.button;

					if (btn.mousePressed(this.mc, mouseX, mouseY))
					{
						btn.playPressSound(this.mc.getSoundHandler());

						OnPressed handler = GuiHelper.HANDLER_BY_BUTTON.get(btn);
						if (handler != null)
						{
							handler.pressed(btn);
						}
					}
				}
				else if (entry.button instanceof GuiTextField
					&& ((GuiTextField)entry.button).getVisible())
				{
					GuiTextField field = (GuiTextField) entry.button;
					field.mouseClicked(mouseX, mouseY, mouseButton);
				}

				if (entry.resetButton instanceof GuiButton
					&& ((GuiButton)entry.resetButton).visible)
				{
					GuiButton reset = (GuiButton) entry.resetButton;

					if (reset.mousePressed(this.mc, mouseX, mouseY))
					{
						reset.playPressSound(this.mc.getSoundHandler());

						OnPressed handler = GuiHelper.HANDLER_BY_BUTTON.get(reset);
						if (handler != null)
						{
							handler.pressed(reset);
						}
					}
				}
			}
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode)
	{
		super.keyTyped(typedChar, keyCode);
		for (ClassicConfigGUI.DhButtonEntry entry : this.configListWidget.children)
		{
			if (entry.button instanceof GuiTextField)
			{
				GuiTextField field = (GuiTextField) entry.button;

				field.textboxKeyTyped(typedChar, keyCode);

				Predicate<String> processor = this.textFieldProcessors.get(field);
				if (processor != null)
				{
					processor.test(field.getText());
				}
			}
		}
	}

	//endregion



	//==========//
	// shutdown //
	//==========//
	//region
	/** When you close it, it goes to the previous screen and saves */
	@Override
	public void onGuiClosed()
	{
		ConfigHandler.INSTANCE.configFileHandler.saveToFile();
		// Handled by button to avoid recursive loop
		ClassicConfigGUI.CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach((listener) -> listener.run());
	}

	//endregion

}
