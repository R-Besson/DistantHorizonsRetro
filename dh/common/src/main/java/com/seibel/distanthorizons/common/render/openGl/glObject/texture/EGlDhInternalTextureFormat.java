package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import org.lwjgl.opengl.GL11;

public enum EGlDhInternalTextureFormat
{
	// 8-bit normalized
	RGBA8(GL11.GL_RGBA8, EGlDhPixelFormat.RGBA);

	private final int glFormat;
	private final EGlDhPixelFormat expectedPixelFormat;

	EGlDhInternalTextureFormat(int glFormat, EGlDhPixelFormat expectedPixelFormat)
	{
		this.glFormat = glFormat;
		this.expectedPixelFormat = expectedPixelFormat;
	}

	public int getGlFormat() { return this.glFormat; }

	public EGlDhPixelFormat getPixelFormat() { return this.expectedPixelFormat; }
}
