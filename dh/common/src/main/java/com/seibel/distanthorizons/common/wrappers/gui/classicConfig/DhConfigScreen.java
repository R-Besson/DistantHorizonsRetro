package com.seibel.distanthorizons.common.wrappers.gui.classicConfig;

import com.seibel.distanthorizons.api.enums.config.DisallowSelectingViaConfigGui;
import com.seibel.distanthorizons.common.wrappers.gui.DhIntSlider;
import com.seibel.distanthorizons.common.wrappers.gui.DhScreen;
import com.seibel.distanthorizons.common.wrappers.gui.DhScreenUtil;
import com.seibel.distanthorizons.common.wrappers.gui.GuiHelper;
import com.seibel.distanthorizons.common.wrappers.gui.OnPressed;
import com.seibel.distanthorizons.common.wrappers.gui.config.ConfigGuiInfo;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigHandler;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
import com.seibel.distanthorizons.core.config.types.ConfigCategory;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.config.types.ConfigUIButton;
import com.seibel.distanthorizons.core.config.types.ConfigUIComment;
import com.seibel.distanthorizons.core.config.types.ConfigUISpacer;
import com.seibel.distanthorizons.core.config.types.ConfigUiLinkedEntry;
import com.seibel.distanthorizons.core.config.types.enums.EConfigValidity;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.AnnotationUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.MakeBtn;
import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.TextOrLiteral;
import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.TextOrTranslatable;
import static com.seibel.distanthorizons.common.wrappers.gui.GuiHelper.Translatable;

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
	/** validates a text box's text, the boolean applies it */
	private final Map<GuiTextField, BiPredicate<String, Boolean>> textFieldProcessors = new HashMap<>();
	/** text boxes that had focus last tick, losing focus applies their value */
	private final Set<GuiTextField> focusedTextFields = new HashSet<>();

	// layout, set in initGui //

	/** width of the widest option label on this page */
	private int labelWidth;
	/** where a row (label, option and reset button) starts so the rows are centered */
	private int rowLeft;

	//=============//
	// constructor //
	//=============//
	//region

	protected DhConfigScreen(GuiScreen parent, String category)
	{
		super(createTitle(category));
		this.parent = parent;
		this.category = category;
	}
	/** IE "Distant Horizons Retro Settings - World Generator", the main page ("client") only has the mod's title */
	private static String createTitle(String category)
	{
		String title = Translatable(ModInfo.ID + ".config.title");
		String categoryKey = TRANSLATION_PREFIX + category;
		if (!category.isEmpty()
			&& !category.equals(MAIN_PAGE_CATEGORY)
			&& LANG_WRAPPER.langExists(categoryKey))
		{
			title += " - " + Translatable(categoryKey);
		}
		return title;
	}
	private static final String MAIN_PAGE_CATEGORY = "client";

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

			// apply the value once the box loses focus
			if (field.isFocused())
			{
				this.focusedTextFields.add(field);
			}
			else if (this.focusedTextFields.remove(field))
			{
				this.commitTextField(field);
			}
		}
	}

	/** applies the text box's value if it's valid, otherwise it goes back to the current value */
	private void commitTextField(GuiTextField field)
	{
		BiPredicate<String, Boolean> processor = this.textFieldProcessors.get(field);
		if (processor == null)
		{
			return;
		}

		if (!processor.test(field.getText(), true))
		{
			AbstractConfigBase<?> configBase = this.configBaseByTextField.get(field);
			if (configBase != null)
			{
				field.setText(String.valueOf(configBase.get()));
				processor.test(field.getText(), false);
				((ConfigGuiInfo) configBase.guiValue).errorMessage = null;
			}
		}
	}
	private final Map<GuiTextField, AbstractConfigBase<?>> configBaseByTextField = new HashMap<>();

	/** applies any value still being typed, saves and goes back to the previous screen */
	private void close()
	{
		for (GuiTextField field : this.textFieldProcessors.keySet())
		{
			this.commitTextField(field);
		}
		ConfigHandler.INSTANCE.configFileHandler.saveToFile();
		DhScreenUtil.setScreen(this.parent);
	}

	//endregion

	//========//
	// layout //
	//========//
	//region

	/**
	 * The rows are as wide as this page's longest label plus the option and reset button,
	 * centered on the screen. Category buttons and headers span the same width so everything lines up.
	 */
	private void calculateLayout()
	{
		int widestLabel = 0;
		for (AbstractConfigBase<?> configBase : ConfigHandler.INSTANCE.configBaseList)
		{
			if (!configBase.getCategory().matches(this.category)
				|| !configBase.getAppearance().showInGui)
			{
				continue;
			}

			AbstractConfigBase<?> shownConfig = (configBase instanceof ConfigUiLinkedEntry) ? ((ConfigUiLinkedEntry) configBase).get() : configBase;
			if (shownConfig instanceof ConfigEntry)
			{
				widestLabel = Math.max(widestLabel, this.fontRendererObj.getStringWidth(this.GetTranslatableTextComponentForConfig(shownConfig)));
			}
		}

		int controlsWidth = ClassicConfigGUI.ConfigScreenConfigs.SPACE_BETWEEN_TEXT_AND_OPTION_FIELD
			+ ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH
			+ ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING
			+ ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_WIDTH;
		// pages without options (IE only category buttons) still get a reasonable width
		int minLabelWidth = ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH - controlsWidth;
		int maxLabelWidth = this.width - (2 * ClassicConfigGUI.ConfigScreenConfigs.SPACE_FROM_RIGHT_SCREEN) - controlsWidth;

		this.labelWidth = Math.max(minLabelWidth, Math.min(widestLabel, maxLabelWidth));
		this.rowLeft = (this.width - this.getRowWidth()) / 2;
	}

	public int getRowLeft() { return this.rowLeft; }
	public int getRowWidth()
	{
		return this.labelWidth
			+ ClassicConfigGUI.ConfigScreenConfigs.SPACE_BETWEEN_TEXT_AND_OPTION_FIELD
			+ ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH
			+ ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING
			+ ClassicConfigGUI.ConfigScreenConfigs.RESET_BUTTON_WIDTH;
	}
	/** labels are right justified against this */
	public int getLabelRightX() { return this.rowLeft + this.labelWidth; }
	public int getOptionX() { return this.getLabelRightX() + ClassicConfigGUI.ConfigScreenConfigs.SPACE_BETWEEN_TEXT_AND_OPTION_FIELD; }
	public int getResetX() { return this.getOptionX() + ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH + ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING; }

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

		// changes apply as they're made, so there's only one button to leave (like MC's options),
		// a separate back button would've done the same
		this.doneButton = this.addBtn(
			MakeBtn(Translatable("distanthorizons.general.done"),
				(this.width - ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH) / 2, this.height - 28,
				ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_WIDTH, ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_HEIGHT,
				(button) -> this.close()));

		this.textFieldProcessors.clear();
		this.focusedTextFields.clear();
		this.configBaseByTextField.clear();
		this.calculateLayout();

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

		ClassicConfigGUI.CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach(Runnable::run);
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
				(stringValue, commit) ->
				{
					boolean isNumber = (pattern != null);

					stringValue = stringValue.trim();
					if (!(stringValue.isEmpty() || !isNumber || pattern.matcher(stringValue).matches()))
					{
						configGuiInfo.errorMessage = TextOrTranslatable("§cValue is invalid");
						editBox.setTextColor(0xFFFF7777); // red
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

					boolean isString = (configMenuOption.getType() == String.class || configMenuOption.getType() == List.class);
					// an empty number box isn't a value yet
					boolean valid = isString
						|| (!stringValue.isEmpty()
						&& !stringValue.equals("-")
						&& !stringValue.equals(".")
						&& ((ConfigEntry<Number>) configMenuOption).getValidity(numberValue) == EConfigValidity.VALID);
					editBox.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF7777); // white and red

					// values are only applied once they're done being typed,
					// IE typing 1000 would otherwise apply 1, 10 and 100 first
					if (commit && valid)
					{
						Object newValue = isString ? stringValue
							: !cast ? numberValue
							: Integer.valueOf(numberValue != null ? numberValue.intValue() : 0);
						// setting a value runs its listeners even when it's the same, which can reload things
						if (!Objects.equals(newValue, configMenuOption.get()))
						{
							((ConfigEntry<Object>) configMenuOption).uiSetWithoutSaving(newValue);
						}
					}

					return valid;
				};
	}
	private static void setupBooleanMenuOption(ConfigEntry<Boolean> booleanConfigEntry)
	{
		// For boolean
		Function<Object, String> func = value -> (((Boolean) value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + Translatable("distanthorizons.general." + value));

		final ConfigGuiInfo configGuiInfo = ((ConfigGuiInfo) booleanConfigEntry.guiValue);

		configGuiInfo.buttonOptionMap =
			new AbstractMap.SimpleEntry<>(
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
			new AbstractMap.SimpleEntry<>(
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

						// does enum have ignored attribute?
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

	@SuppressWarnings("rawtypes") private boolean tryCreateInputField(AbstractConfigBase<?> configBase)
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

			int resetButtonPosX = this.getResetX();
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

			int optionFieldPosX = this.getOptionX();
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
				// or the MC version doesn't support it.
				// The value is grayed out too, otherwise colored values (IE green "True") look selectable
				if (configEntry.mcVersionOverridePresent()
					|| configEntry.apiIsOverriding())
				{
					button.enabled = false;
					button.displayString = EnumChatFormatting.DARK_GRAY + EnumChatFormatting.getTextWithoutFormattingCodes(button.displayString);
				}

				this.configListWidget.addButton(this, configEntry,
					button,
					resetButton,
					null,
					textComponent);
			}
			else if (configEntry == Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius)
			{
				this.createRenderDistanceInput((ConfigEntry<Integer>) configEntry, resetButton, textComponent);
			}
			else
			{
				// text box input,
				// a text box's border is drawn around it, so it's 1 pixel inset to line up with the buttons
				GuiTextField widget = new GuiTextField(this.fontRendererObj,
					optionFieldPosX + 1, optionFieldPosZ,
					ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH - 2, ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT - 2);
				widget.setMaxStringLength(3_000_000); // hopefully 3 million characters should be enough for any normal use-case, lol
				widget.setText(String.valueOf(configEntry.get()));

				BiPredicate<String, Boolean> processor = configGuiInfo.tooltipFunction.apply(widget, this.doneButton);
				this.textFieldProcessors.put(widget, processor);
				this.configBaseByTextField.put(widget, configEntry);

				ClassicConfigGUI.DhButtonEntry entry = this.configListWidget.addButton(this, configEntry, widget, resetButton, null, textComponent);

				// -1 is a special value for these, which a number doesn't explain
				String negativeValueHintKey = NEGATIVE_VALUE_HINT_KEY_BY_CONFIG.get(configEntry);
				if (negativeValueHintKey != null)
				{
					entry.valueHint = () -> (((Number) configEntry.get()).doubleValue() < 0) ? Translatable(negativeValueHintKey) : null;
				}
			}
			return true;

			//endregion
		}

		return false;
	}
	private boolean tryCreateCategoryButton(AbstractConfigBase<?> configType)
	{
		if (configType instanceof ConfigCategory)
		{
			ConfigCategory configCategory = (ConfigCategory) configType;

			// "..." like MC's buttons that open another screen (IE "Video Settings...")
			String textComponent = this.GetTranslatableTextComponentForConfig(configCategory) + "...";

			int categoryPosZ = this.height - ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT; // Note: the posZ value here seems to be ignored

			GuiButton widget = MakeBtn(
				textComponent,
				this.getRowLeft(), categoryPosZ,
				this.getRowWidth(), ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
				((button) ->
				{
					for (GuiTextField field : this.textFieldProcessors.keySet())
					{
						this.commitTextField(field);
					}
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

			GuiButton widget = MakeBtn(
				textComponent,
				this.getRowLeft(), this.height - 28,
				this.getRowWidth(), ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT,
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

	/**
	 * A slider with a box next to it for typing the exact number of chunks. <br>
	 * Changing the render distance reloads the LODs, so it's only applied
	 * when the slider is let go or the typed number is done (enter or clicking away).
	 */
	private void createRenderDistanceInput(ConfigEntry<Integer> configEntry, GuiButton resetButton, String textComponent)
	{
		int textFieldWidth = RENDER_DISTANCE_TEXT_FIELD_WIDTH;
		int sliderWidth = ClassicConfigGUI.ConfigScreenConfigs.OPTION_FIELD_WIDTH - textFieldWidth - ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING;
		int height = ClassicConfigGUI.ConfigScreenConfigs.CATEGORY_BUTTON_HEIGHT;

		GuiTextField textField = new GuiTextField(this.fontRendererObj,
			this.getOptionX() + sliderWidth + ClassicConfigGUI.ConfigScreenConfigs.BUTTON_WIDTH_SPACING + 1, 0,
			textFieldWidth - 2, height - 2);
		textField.setMaxStringLength(6);
		textField.setText(String.valueOf(configEntry.get()));

		// the slider shows the blocks, the box the chunks
		DhIntSlider slider = new DhIntSlider(GuiHelper.HANDLER_BY_BUTTON.size(),
			this.getOptionX(), 0, sliderWidth, height,
			configEntry, RENDER_DISTANCE_SLIDER_STEP,
			(chunks) -> Translatable("distanthorizons.general.blocks", chunks * 16),
			(chunks) -> textField.setText(String.valueOf(chunks)));

		BiPredicate<String, Boolean> processor = (text, commit) ->
		{
			Integer chunks;
			try
			{
				chunks = Integer.parseInt(text.trim());
			}
			catch (NumberFormatException e)
			{
				chunks = null;
			}

			ConfigGuiInfo configGuiInfo = (ConfigGuiInfo) configEntry.guiValue;
			boolean valid = (chunks != null && configEntry.getValidity(chunks) == EConfigValidity.VALID);
			configGuiInfo.errorMessage = valid ? null
				: TextOrTranslatable("§c" + Translatable("distanthorizons.general.chunkRange", configEntry.getMin(), configEntry.getMax()));
			textField.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF7777); // white and red

			if (commit && valid && !chunks.equals(configEntry.get()))
			{
				configEntry.uiSetWithoutSaving(chunks);
			}
			return valid;
		};
		this.textFieldProcessors.put(textField, processor);
		this.configBaseByTextField.put(textField, configEntry);

		ClassicConfigGUI.DhButtonEntry entry = this.configListWidget.addButton(this, configEntry, slider, resetButton, null, textComponent);
		entry.valueTextField = textField;
	}
	private static final int RENDER_DISTANCE_SLIDER_STEP = 8;
	/** fits "4096" */
	private static final int RENDER_DISTANCE_TEXT_FIELD_WIDTH = 40;

	/** text shown in the text box when the value is negative */
	private static final Map<AbstractConfigBase<?>, String> NEGATIVE_VALUE_HINT_KEY_BY_CONFIG = new HashMap<>();
	static
	{
		NEGATIVE_VALUE_HINT_KEY_BY_CONFIG.put(Config.Client.Advanced.Graphics.Culling.overdrawPrevention, "distanthorizons.general.auto");
		NEGATIVE_VALUE_HINT_KEY_BY_CONFIG.put(Config.Common.WorldGenerator.surfaceRegenMaxDistancePercent, "distanthorizons.general.noLimit");
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

		this.renderTooltip(mouseX, mouseY);
	}

	private void renderTooltip(int mouseX, int mouseY)
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

	/** sliders stop following the mouse once it's let go */
	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state)
	{
		super.mouseReleased(mouseX, mouseY, state);

		// -1 means the mouse moved, anything else is a released button
		if (state != -1)
		{
			for (ClassicConfigGUI.DhButtonEntry entry : this.configListWidget.children)
			{
				if (entry.button instanceof DhIntSlider)
				{
					((DhIntSlider) entry.button).mouseReleased(mouseX, mouseY);
				}
			}
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
					&& ((GuiButton) entry.button).visible)
				{
					GuiButton btn = (GuiButton) entry.button;

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
					&& ((GuiTextField) entry.button).getVisible())
				{
					GuiTextField field = (GuiTextField) entry.button;
					field.mouseClicked(mouseX, mouseY, mouseButton);
				}

				if (entry.valueTextField != null
					&& entry.valueTextField.getVisible())
				{
					entry.valueTextField.mouseClicked(mouseX, mouseY, mouseButton);
				}

				if (entry.resetButton instanceof GuiButton
					&& ((GuiButton) entry.resetButton).visible)
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
		// escape goes back a page like the done button instead of closing every screen
		if (keyCode == Keyboard.KEY_ESCAPE)
		{
			this.close();
			return;
		}

		for (GuiTextField field : this.textFieldProcessors.keySet())
		{
			if (!field.isFocused())
			{
				continue;
			}

			// enter applies the typed value
			if (keyCode == Keyboard.KEY_RETURN
				|| keyCode == Keyboard.KEY_NUMPADENTER)
			{
				field.setFocused(false);
				this.focusedTextFields.remove(field);
				this.commitTextField(field);
				continue;
			}

			field.textboxKeyTyped(typedChar, keyCode);
			// only validated while typing, it's applied once it's done
			this.textFieldProcessors.get(field).test(field.getText(), false);
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
		// IE a value still being typed when another screen opens
		for (GuiTextField field : this.textFieldProcessors.keySet())
		{
			this.commitTextField(field);
		}
		ConfigHandler.INSTANCE.configFileHandler.saveToFile();
		// Handled by button to avoid recursive loop
		ClassicConfigGUI.CONFIG_CORE_INTERFACE.onScreenChangeListenerList.forEach(Runnable::run);
	}

	//endregion
}
