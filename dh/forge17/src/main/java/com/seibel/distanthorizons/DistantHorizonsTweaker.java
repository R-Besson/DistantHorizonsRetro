package com.seibel.distanthorizons;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry point. <br>
 * Our mixins are listed by {@link com.seibel.distanthorizons.forge17.DistantHorizonsMixinConfigPlugin}.
 */
public class DistantHorizonsTweaker implements IFMLLoadingPlugin
{
	@Override
	public String[] getASMTransformerClass() { return new String[0]; }

	@Override
	public String getModContainerClass() { return null; }

	@Override
	public String getSetupClass() { return null; }

	@Override
	public void injectData(Map<String, Object> data) { }

	@Override
	public String getAccessTransformerClass() { return null; }
}
