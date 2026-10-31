package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import org.lwjgl.opengl.GL11;

public enum EGlDhPixelType
{
	UNSIGNED_BYTE(GL11.GL_UNSIGNED_BYTE);

	private final int glFormat;

	EGlDhPixelType(int glFormat)
	{
		this.glFormat = glFormat;
	}

	public int getGlFormat() { return glFormat; }
}
