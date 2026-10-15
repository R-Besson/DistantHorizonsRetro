package com.seibel.distanthorizons.common.commonMixins;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;


public class MixinVanillaFogCommon
{
	public static boolean cancelFog()
	{
		// don't cancel fog if DH isn't rendering
		if (!Config.Client.quickEnableRendering.get())
		{
			return false;
		}

		return !SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class).isFogStateSpecial()
			&& !Config.Client.Advanced.Graphics.Fog.enableVanillaFog.get();
	}



}
