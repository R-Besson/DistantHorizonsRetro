package com.seibel.distanthorizons.forge17;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;

public class DistantHorizonsMixinConfigPlugin implements IMixinConfigPlugin
{
	public static final Logger LOGGER = LogManager.getLogger("DistantHorizonsMixins");
	
	@Override
	public void onLoad(String mixinPackage)
	{
		LOGGER.info("Loaded DistantHorizons mixin plugin");
/*		var handle = SharedConfig.getRfbTransformers().stream().filter(transformer -> transformer.id().equals("lwjgl3ify:redirect")).findFirst().orElseThrow();
		handle.exclusions().add("com.seibel.distanthorizons.main");
		handle.exclusions().add("com.seibel.distanthorizons.lwjgl.lwjgl3");*/
	}
	@Override
	public String getRefMapperConfig()
	{
		return null;
	}
	
	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
	{
		return true;
	}
	
	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
	{
		
	}
	
	@Override
	public List<String> getMixins()
	{
		// The config is registered by the jar's MixinConfigs manifest entry before GTNHMixins
		// gets a chance to, so GTNHMixins' list would be dropped; supply it from here instead.
		return IMixins.getEarlyMixins(Mixins.class, getLoadedTargetCoreMods());
	}
	
	/** coremods are already on the classpath at this point, so checking for their class file is enough */
	private static Set<String> getLoadedTargetCoreMods()
	{
		Set<String> loadedCoreMods = new HashSet<>();
		ClassLoader classLoader = DistantHorizonsMixinConfigPlugin.class.getClassLoader();
		for (TargetedMod targetedMod : TargetedMod.values())
		{
			String coreModClass = targetedMod.coreModClass;
			if (classLoader.getResource(coreModClass.replace('.', '/') + ".class") != null)
			{
				loadedCoreMods.add(coreModClass);
			}
		}
		return loadedCoreMods;
	}
	
	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
	{
		
	}
	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
	{
		
	}
	
}
