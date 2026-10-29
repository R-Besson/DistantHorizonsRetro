package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import com.seibel.distanthorizons.common.render.openGl.glObject.enums.EGlVersion;
import org.lwjgl.opengl.GL11;

public enum EGlDhPixelFormat
{
	RGBA(GL11.GL_RGBA, EGlVersion.GL_11, false);



	private final int glFormat;
	private final boolean isInteger;



	EGlDhPixelFormat(int glFormat, EGlVersion minimumGlVersion, boolean isInteger)
	{
		this.glFormat = glFormat;
		/** kept to document which GL version each format needs */
		this.isInteger = isInteger;
	}



	public int getGlFormat() { return this.glFormat; }

	public boolean isInteger() { return this.isInteger; }

}
