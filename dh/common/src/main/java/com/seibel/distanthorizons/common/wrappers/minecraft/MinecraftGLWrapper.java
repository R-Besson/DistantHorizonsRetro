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


import org.lwjgl.opengl.GL11;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;


/**
 * Wraps the GL state changes DH makes while rendering,
 * so they all go through one place.
 */
public class MinecraftGLWrapper
{
	public static final MinecraftGLWrapper INSTANCE = new MinecraftGLWrapper();



	// scissor //
	//region

	/** @see GL11#GL_SCISSOR_TEST */
	public void enableScissorTest()
	{
		LWJGL.glEnable(GL11.GL_SCISSOR_TEST);
	}
	/** @see GL11#GL_SCISSOR_TEST */
	public void disableScissorTest()
	{
		LWJGL.glDisable(GL11.GL_SCISSOR_TEST);
	}

	//endregion



	// depth //
	//region

	/** @see GL11#GL_DEPTH_TEST */
	public void enableDepthTest()
	{
		LWJGL.glEnable(GL11.GL_DEPTH_TEST);
	}
	/** @see GL11#GL_DEPTH_TEST */
	public void disableDepthTest()
	{
		LWJGL.glDisable(GL11.GL_DEPTH_TEST);
	}

	/** @see GL11#glDepthFunc(int)  */
	public void glDepthFunc(int func)
	{
		LWJGL.glDepthFunc(func);
	}
	public int getActiveDepthFunc() { return LWJGL.glGetInteger(GL11.GL_DEPTH_FUNC); }

	/** @see GL11#glDepthMask(boolean) */
	public void enableDepthMask()
	{
		LWJGL.glDepthMask(true);
	}
	/** @see GL11#glDepthMask(boolean) */
	public void disableDepthMask()
	{
		LWJGL.glDepthMask(false);
	}

	//endregion



	// blending //
	//region

	/** @see GL11#GL_BLEND */
	public void enableBlend()
	{
		LWJGL.glEnable(GL11.GL_BLEND);
	}
	/** @see GL11#GL_BLEND */
	public void disableBlend()
	{
		LWJGL.glDisable(GL11.GL_BLEND);
	}

	/** @see GL11#glBlendFunc */
	public void glBlendFunc(int sfactor, int dfactor)
	{
		LWJGL.glBlendFunc(sfactor, dfactor);
	}
	/** @see org.lwjgl.opengl.GL14#glBlendFuncSeparate */
	public void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha)
	{
		LWJGL.glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
	}

	//endregion



	// frame buffers //
	//region

	/** @see org.lwjgl.opengl.GL30#glBindFramebuffer */
	public void glBindFramebuffer(int target, int framebuffer)
	{
		LWJGL.glBindFramebuffer(target, framebuffer);
	}

	//endregion



	// buffers //
	//region

	/** @see org.lwjgl.opengl.GL15#glGenBuffers() */
	public int glGenBuffers()
	{ return LWJGL.glGenBuffers(); }

	/** @see org.lwjgl.opengl.GL15#glDeleteBuffers(int) */
	public void glDeleteBuffers(int buffer)
	{
		LWJGL.glDeleteBuffers(buffer);
	}

	//endregion



	// culling //
	//region

	/** @see GL11#GL_CULL_FACE */
	public void enableFaceCulling()
	{
		LWJGL.glEnable(GL11.GL_CULL_FACE);
	}
	/** @see GL11#GL_CULL_FACE */
	public void disableFaceCulling()
	{
		LWJGL.glDisable(GL11.GL_CULL_FACE);
	}

	//endregion



	// textures //
	//region

	/** @see GL11#glGenTextures() */
	public int glGenTextures()
	{
		return LWJGL.glGenTextures();
	}
	/** @see GL11#glDeleteTextures(int) */
	public void glDeleteTextures(int texture)
	{
		LWJGL.glDeleteTextures(texture);
	}

	/** @see org.lwjgl.opengl.GL13#glActiveTexture(int) */
	public void glActiveTexture(int textureId)
	{
		LWJGL.glActiveTexture(textureId);
	}
	public int getActiveTexture() { return LWJGL.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }

	/**
	 * Always binds to {@link GL11#GL_TEXTURE_2D}
	 * @see GL11#glBindTexture(int, int)
	 */
	public void glBindTexture(int texture)
	{
		LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
	}

	//endregion



	// viewport //
	//region

	/** @see GL11#glViewport(int, int, int, int) */
	public void glViewport(int x, int y, int viewportWidth, int viewportHeight)
	{
		LWJGL.glViewport(x, y, viewportWidth, viewportHeight);
	}

	//endregion



}
