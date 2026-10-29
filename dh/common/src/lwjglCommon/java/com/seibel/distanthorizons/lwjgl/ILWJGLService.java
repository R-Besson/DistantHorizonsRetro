package com.seibel.distanthorizons.lwjgl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * LWJGL2/LWJGL3 abstraction.
 */
public interface ILWJGLService
{
	// ===================== CAPABILITIES =====================
	//region

	boolean isOpenGLVersionSupported(int major, int minor);
	boolean isExtensionSupported(EGLExtension extension);
	boolean isFeatureSupported(EGLFeature feature);

	//endregion



	// ===================== BUFFER OPERATIONS =====================
	//region

	int glGenBuffers();
	void glDeleteBuffers(int buffer);
	void glBindBuffer(int target, int buffer);
	void glBufferData(int target, long size, int usage);
	void glBufferData(int target, ByteBuffer data, int usage);
	void glBufferData(int target, int[] data, int usage);
	void glBufferData(int target, float[] data, int usage);
	void glBufferStorage(int target, ByteBuffer bb, int flags);
	void glBufferSubData(int target, long size, ByteBuffer data);
	void glBindVertexBuffer(int bindingindex, int buffer, int offset, int stride);

	//endregion



	// ===================== VAO OPERATIONS =====================
	//region

	int glGenVertexArrays();
	void glDeleteVertexArrays(int array);
	void glBindVertexArray(int array);
	void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer);
	void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer);
	void glEnableVertexAttribArray(int index);
	void glDisableVertexAttribArray(int index);
	void glVertexAttribDivisor(int index, int divisor);
	void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset);
	void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset);
	void glVertexAttribBinding(int attribindex, int bindingindex);

	//endregion



	// ===================== SHADER OPERATIONS =====================
	//region

	int glCreateShader(int type);

	/**
	 * Passes a null pointer for string length to force the driver to rely on the null
	 * terminator for string length. This is a workaround for an apparent flaw with some
	 * AMD drivers that don't receive or interpret the length correctly, resulting in
	 * an access violation when the driver tries to read past the string memory.
	 *
	 * <p>Hat tip to fewizz for the find and the fix.
	 *
	 * @see <a href="https://github.com/grondag/canvas/commit/820bf754092ccaf8d0c169620c2ff575722d7d96">Original Canvas commit</a>
	 */
	void glShaderSourceSafe(int shader, CharSequence source);
	void glCompileShader(int shader);
	String glGetShaderInfoLog(int shader, int maxLength);
	int glGetShaderi(int shader, int pname);
	void glDeleteShader(int shader);

	int glCreateProgram();
	void glAttachShader(int program, int shader);
	void glLinkProgram(int program);
	String glGetProgramInfoLog(int program, int maxLength);
	int glGetProgrami(int program, int pname);
	void glUseProgram(int program);
	void glDeleteProgram(int program);
	void glBindAttribLocation(int program, int index, CharSequence name);

	//endregion



	// ===================== UNIFORM OPERATIONS =====================
	//region

	int glGetUniformLocation(int program, CharSequence name);
	void glUniform1f(int location, float v0);
	void glUniform1i(int location, int v0);
	void glUniform2f(int location, int v0, int v1);
	void glUniform3f(int location, float v0, float v1, float v2);
	void glUniform3i(int location, int v0, int v1, int v2);
	void glUniform4f(int location, float v0, float v1, float v2, float v3);
	void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value);

	//endregion



	// ===================== DRAW OPERATIONS =====================
	//region

	void glDrawArrays(int mode, int first, int count);
	void glDrawElements(int mode, int count, int type, int indices);
	void glDrawElementsInstanced(int mode, int count, int type, int indices, int instancecount);

	//endregion



	// ===================== SYNC OPERATIONS =====================
	//region

	void glFlush();

	//endregion



	// ===================== TEXTURE OPERATIONS =====================
	//region

	int glGenTextures();
	void glDeleteTextures(int texture);
	void glBindTexture(int target, int texture);
	void glActiveTexture(int texture);
	void glPixelStorei(int pname, int param);
	void glTexParameteri(int target, int pname, int param);
	void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels);
	void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels);
	void glGenerateMipmap(int target);
	boolean glIsTexture(int texture);
	void glBindSampler(int unit, int sampler);

	//endregion



	// ===================== FRAMEBUFFER OPERATIONS =====================
	//region

	int glGenFramebuffers();
	void glDeleteFramebuffers(int framebuffer);
	void glBindFramebuffer(int target, int framebuffer);
	int glCheckFramebufferStatus(int target);
	void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
	void glFramebufferTexture(int target, int attachment, int texture, int level);
	int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname);

	//endregion



	// ===================== STATE OPERATIONS =====================
	//region

	void glEnable(int cap);
	void glDisable(int cap);
	boolean glIsEnabled(int cap);
	void glBlendFunc(int sfactor, int dfactor);
	void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha);
	void glBlendEquation(int mode);
	void glBlendEquationSeparate(int modeRGB, int modeAlpha);
	void glDepthFunc(int func);
	void glDepthMask(boolean flag);
	void glColorMask(boolean red, boolean green, boolean blue, boolean alpha);
	void glViewport(int x, int y, int width, int height);
	void glClear(int mask);
	void glClearColor(float red, float green, float blue, float alpha);
	void glClearDepth(double depth);
	void glCullFace(int mode);
	void glPolygonMode(int face, int mode);
	void glStencilFunc(int func, int ref, int mask);
	void glColor4f(float v, float v1, float v2, float v3);

	//endregion



	// ===================== Fog =========================
	//region

	void glFogf(int	pname, float params);

	//endregion



	// ===================== MISC GL =====================
	//region

	int glGetInteger(int pname);
	void glGetIntegerv(int pname, int[] params);
	void glGetFloatv(int pname, float[] params);
	boolean glGetBoolean(int pname);
	String glGetString(int pname);

	//endregion



	// ===================== MEMORY STACK =====================
	//region

	AbstractMemoryStack stackPush();

	//endregion

	// ===================== UTIL =====================
	//region

	boolean isRunningOnRenderThread();

	//endregion


}
