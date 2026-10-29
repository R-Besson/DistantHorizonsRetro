package com.seibel.distanthorizons.common.render.openGl.glObject.texture;

import com.seibel.distanthorizons.common.render.openGl.glObject.enums.EGlVersion;
import org.lwjgl.opengl.GL11;

public enum EGlDhPixelType
{
	UNSIGNED_BYTE(GL11.GL_UNSIGNED_BYTE, EGlVersion.GL_11);



	private final int glFormat;
	
	
	
	EGlDhPixelType(int glFormat, EGlVersion minimumGlVersion)
	{
		this.glFormat = glFormat;
		/** kept to document which GL version each type needs */
	}



	public int getGlFormat() { return glFormat; }

}
