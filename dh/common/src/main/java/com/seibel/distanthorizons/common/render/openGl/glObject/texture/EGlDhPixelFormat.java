package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import org.lwjgl.opengl.GL11;

public enum EGlDhPixelFormat
{
	RGBA(GL11.GL_RGBA, false);

	private final int glFormat;
	private final boolean isInteger;

	EGlDhPixelFormat(int glFormat, boolean isInteger)
	{
		this.glFormat = glFormat;
		this.isInteger = isInteger;
	}

	public int getGlFormat() { return this.glFormat; }

	public boolean isInteger() { return this.isInteger; }
}
