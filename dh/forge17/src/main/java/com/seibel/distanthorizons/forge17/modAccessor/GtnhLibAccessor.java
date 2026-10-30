package com.seibel.distanthorizons.forge17.modAccessor;

import com.gtnewhorizon.gtnhlib.util.AboveHotbarHUD;
import com.seibel.distanthorizons.common.wrappers.modAccessor.IGtnhLibCommonAccessor;
import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;

public class GtnhLibAccessor implements IGtnhLibCommonAccessor
{
	@Override
	public String getModName() { return "gtnhlib"; }

	@Override
	public void renderTextAboveHotbar(String text)
	{ AboveHotbarHUD.renderTextAboveHotbar(MinecraftTextFormat.WHITE + text, 60, true, true); }

}
