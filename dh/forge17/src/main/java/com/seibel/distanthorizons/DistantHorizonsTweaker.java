package com.seibel.distanthorizons;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry point. <br><br>
 *
 * Implements {@link IEarlyMixinLoader} so GTNHMixins registers our mixin config;
 * outside of a dev environment nothing else does (the jar's MixinConfigs manifest entry
 * is only read for jars that use MixinTweaker as their tweaker). <br>
 * The mixins themselves are listed by {@link com.seibel.distanthorizons.forge17.DistantHorizonsMixinConfigPlugin}
 * so they're applied no matter which of the two registers the config first.
 */
public class DistantHorizonsTweaker implements IFMLLoadingPlugin, IEarlyMixinLoader
{
	@Override
	public String getMixinConfig() { return "DistantHorizons.forge.mixins.json"; }

	/** empty, the config plugin lists our mixins */
	@Override
	public List<String> getMixins(Set<String> loadedCoreMods) { return Collections.emptyList(); }

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
