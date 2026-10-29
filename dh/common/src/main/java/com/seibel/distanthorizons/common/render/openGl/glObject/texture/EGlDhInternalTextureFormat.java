package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import com.seibel.distanthorizons.common.render.openGl.glObject.enums.EGlVersion;
import org.lwjgl.opengl.GL11;

public enum EGlDhInternalTextureFormat
{
	// 8-bit normalized
	RGBA8(GL11.GL_RGBA8, EGlVersion.GL_11, EGlDhPixelFormat.RGBA);



	private final int glFormat;
	/** kept to document which GL version each format needs */
	@SuppressWarnings("unused")
	private final EGlVersion minimumGlVersion;
	private final EGlDhPixelFormat expectedPixelFormat;



	EGlDhInternalTextureFormat(int glFormat, EGlVersion minimumGlVersion, EGlDhPixelFormat expectedPixelFormat)
	{
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.expectedPixelFormat = expectedPixelFormat;
	}



	public int getGlFormat() { return this.glFormat; }

	public EGlDhPixelFormat getPixelFormat() { return this.expectedPixelFormat; }



}
