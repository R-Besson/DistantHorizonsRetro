/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import com.seibel.distanthorizons.common.backports.Camera;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.util.math.DhVec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fluids.IFluidBlock;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;



/**
 * A singleton that contains everything
 * related to rendering in Minecraft.
 */
public class MinecraftRenderWrapper implements IMinecraftRenderWrapper
{
	public static final MinecraftRenderWrapper INSTANCE = new MinecraftRenderWrapper();

	private static final Minecraft MC = Minecraft.getMinecraft();

	/** Delayed accessing is necessary since this object will be created before the mod accessors are bound. */
	private static class DelayedAccessors
	{
		private static final IIrisAccessor IRIS = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		private static final IAngelicaAccessor ANGELICA = ModAccessorInjector.INSTANCE.get(IAngelicaAccessor.class);

	}

	/** {@link LightMapWrapper} queries MC directly, so one instance can be shared by every level. */
	private final LightMapWrapper lightmapWrapper = new LightMapWrapper();




	//=========//
	// methods //
	//=========//

	@Override
	public DhVec3f getLookAtVector()
	{
		Vec3 lookVector = MC.renderViewEntity.getLookVec();
		return new DhVec3f((float) lookVector.xCoord, (float) lookVector.yCoord, (float) lookVector.zCoord);
	}

	/**
	 * Unless you really need to know if the player is blind,
	 * use {@link MinecraftRenderWrapper#isFogStateSpecial()} or {@link IMinecraftRenderWrapper#isFogStateSpecial()} instead
	 */
	public boolean playerHasBlindingEffect()
	{
		if (MC.thePlayer == null)
		{
			return false;
		}

		return MC.thePlayer.getActivePotionEffect(Potion.blindness) != null;
	}

	@Override
	public DhVec3d getCameraExactPosition() { return this.getCameraExactPosition(new DhVec3d()); }
	@Override
	public DhVec3d getCameraExactPosition(DhVec3d output)
	{
		float frameTime = Minecraft.getMinecraft().timer.renderPartialTicks;
		Camera.INSTANCE.getUpdatedPos(MC.renderViewEntity, frameTime, output);
		return output;
	}

	/**
	 * percentage of time into the current client tick. <br><br>
	 *
	 * Can be converted to a millisecond frametime
	 * (IE time between frames in milliseconds) using the formula: <br>
	 * <code>
	 * (partialTickTime/20*1000)
	 * </code> <br>
	 * IE 60 FPS = 16.6 MS <br>
	 *
	 * @link <a href="https://fpstoms.com/">...</a>
	 * @see com.seibel.distanthorizons.core.api.internal.rendering.DhRenderState#partialTickTime
	 */
	public float getPartialTickTime()
	{
		return Minecraft.getMinecraft().timer.renderPartialTicks;
	}

	@Override
	public Color getFogColor()
	{
		if (DelayedAccessors.ANGELICA != null)
		{
			return DelayedAccessors.ANGELICA.getFogColor();
		}

		float[] colorValues = new float[4];
		LWJGL.glGetFloatv(GL11.GL_FOG_COLOR, colorValues);
		return new Color(
			Math.max(0f, Math.min(colorValues[0], 1f)), // r
			Math.max(0f, Math.min(colorValues[1], 1f)), // g
			Math.max(0f, Math.min(colorValues[2], 1f)), // b
			Math.max(0f, Math.min(colorValues[3], 1f))  // a
		);
	}

	@Override
	public Color getSkyColor()
	{
		if (!MC.theWorld.provider.hasNoSky)
		{
			float frameTime = this.getPartialTickTime();
			Vec3 colorValues = MC.theWorld.provider.getSkyColor(MC.renderViewEntity, frameTime);
			return new Color((float) colorValues.xCoord, (float) colorValues.yCoord, (float) colorValues.zCoord);
		}
		else
		{
			return new Color(0, 0, 0);
		}
	}

	/** Measured in chunks */
	@Override
	public int getRenderDistance()
	{
		return MC.gameSettings.renderDistanceChunks;
	}

	/** Measured in degrees */
	@Override
	public double getFovSetting()
	{
		return MC.gameSettings.fovSetting;
	}

	@Override
	public int getFrameLimit()
	{
		return MC.gameSettings.limitFramerate;
	}


	@Override
	public boolean mcRendersToFrameBuffer()
	{
		return true;
	}

	@Override
	public boolean runningLegacyOpenGL()
	{
		return true;
	}

	/** Returns the Graphics API Minecraft is currently using for rendering */
	public EDhApiRenderingApi getMcRenderingApi()
	{
		return EDhApiRenderingApi.OPEN_GL;
	}

	@Override
	public EDhApiDepthDirection getMcDepthDirection()
	{
		if (DelayedAccessors.IRIS != null
			&& DelayedAccessors.IRIS.isShaderPackInUse())
		{
			if (DelayedAccessors.IRIS.isReverseZDuringShaders())
			{
				return EDhApiDepthDirection.REVERSE_Z;
			}
			else
			{
				return EDhApiDepthDirection.FORWARD_Z;
			}
		}

		return EDhApiDepthDirection.FORWARD_Z;
	}

	@Override
	public int getTargetFramebuffer()
	{
		return MC.getFramebuffer().framebufferObject;
	}

	@Override
	public int getGlDepthTextureId()
	{
		if (DelayedAccessors.ANGELICA != null)
		{
			return DelayedAccessors.ANGELICA.getDepthTextureId();
		}

		final Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
		return framebuffer.depthBuffer;
	}
	@Override
	public int getGlColorTextureId()
	{
		return MC.getFramebuffer().framebufferTexture;
	}

	@Override
	public int getTargetFramebufferViewportWidth()
	{
		return MC.getFramebuffer().framebufferWidth;
	}

	@Override
	public int getTargetFramebufferViewportHeight()
	{
		return MC.getFramebuffer().framebufferHeight;
	}

	@Override
	public boolean isFogStateSpecial()
	{
		DhVec3d cameraPos = this.getCameraExactPosition();

		Block fluidBlock = MC.renderViewEntity.worldObj.getBlock(
			MathHelper.floor_double(cameraPos.x),
			MathHelper.floor_double(cameraPos.y),
			MathHelper.floor_double(cameraPos.z));

		return this.playerHasBlindingEffect()
			|| fluidBlock.getMaterial().isLiquid()
			|| fluidBlock instanceof IFluidBlock
			|| modDensityFogActive;
	}
	/**
	 * True while a mod overrides MC's fog with a density (IE GregTech's pollution smog),
	 * set by the forge17 MixinEntityRenderer every time MC sets up its fog.
	 */
	public static volatile boolean modDensityFogActive = false;



	//==========//
	// lightmap //
	//==========//
	//region

	@Override
	public ILightMapWrapper getLightmapWrapper() { return this.lightmapWrapper; }

	//endregion



}
