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

package com.seibel.distanthorizons.common.wrappers.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IRpleAccessor;



public class LightMapWrapper implements ILightMapWrapper
{
	private static final IRpleAccessor RPLE_ACCESSOR = ModAccessorInjector.INSTANCE.get(IRpleAccessor.class);

	/**
	 * which texture index IE 0,1,2... the lightmap will be bound to. <Br>
	 * Related to but different from {@link org.lwjgl.opengl.GL13#GL_TEXTURE0}.
	 */
	public static final int GL_BOUND_INDEX = 0;



	//==============//
	// constructors //
	//==============//
	//region

	public LightMapWrapper() { }

	//endregion



	//==============//
	// lightmap use //
	//==============//
	//region

	/** Queries MC's lightmap texture each time, so this wrapper is stateless. */
	public int getOpenGlId()
	{
		// RPLE (the colored-lighting mod) replaces the vanilla lightmap, so check that first.
		if (RPLE_ACCESSOR != null)
		{
			return RPLE_ACCESSOR.getLightmapTextureId();
		}

		DynamicTexture lightmapTexture = Minecraft.getMinecraft().entityRenderer.lightmapTexture;
		return lightmapTexture.getGlTextureId();
	}

	//endregion



}
