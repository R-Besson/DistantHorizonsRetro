package com.seibel.distanthorizons.lwjgl;

/**
 * OpenGL functions that can be queried via {@link ILWJGLService#isFeatureSupported(EGLFeature)},
 * drivers can expose them below the GL version that requires them.
 */
public enum EGLFeature 
{
	glBufferStorage,
	glBindVertexBuffer,
}
