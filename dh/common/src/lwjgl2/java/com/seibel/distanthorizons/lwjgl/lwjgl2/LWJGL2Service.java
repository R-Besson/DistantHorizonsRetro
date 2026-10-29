package com.seibel.distanthorizons.lwjgl.lwjgl2;

import com.seibel.distanthorizons.lwjgl.AbstractMemoryStack;
import com.seibel.distanthorizons.lwjgl.ILWJGLService;
import com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryStack;
import com.seibel.distanthorizons.lwjgl.lwjgl2.memory.MemoryUtilities;
import com.seibel.distanthorizons.lwjgl.EGLExtension;
import com.seibel.distanthorizons.lwjgl.EGLFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.APPLEVertexArrayObject;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTGpuShader4;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GLContext;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * LWJGL2 implementation of {@link ILWJGLService}.
 */
public class LWJGL2Service implements ILWJGLService
{
	private static final Logger LOGGER = LogManager.getLogger("DistantHorizons/LWJGL2Service");

	private final VAOMode vaoMode;
	private final VertexAttribIMode vertexAttribIMode;



	private LWJGL2Service(
		VAOMode vaoMode,
		VertexAttribIMode vertexAttribIMode)
	{
		this.vaoMode = vaoMode;
		this.vertexAttribIMode = vertexAttribIMode;
	}
	
	
	
	private enum VAOMode
	{
		CORE
		{
			@Override public int gen() { return GL30.glGenVertexArrays(); }
			@Override public void delete(int array) { GL30.glDeleteVertexArrays(array); }
			@Override public void bind(int array) { GL30.glBindVertexArray(array); }
		},
		ARB
		{
			@Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
			@Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
			@Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
		},
		APPLE
		{
			@Override public int gen() { return APPLEVertexArrayObject.glGenVertexArraysAPPLE(); }
			@Override public void delete(int array) { APPLEVertexArrayObject.glDeleteVertexArraysAPPLE(array); }
			@Override public void bind(int array) { APPLEVertexArrayObject.glBindVertexArrayAPPLE(array); }
		},
		NONE
		{
			@Override public int gen() { throw new UnsupportedOperationException("VAO not supported"); }
			@Override public void delete(int array) { throw new UnsupportedOperationException("VAO not supported"); }
			@Override public void bind(int array) { throw new UnsupportedOperationException("VAO not supported"); }
		};
		
		public abstract int gen();
		public abstract void delete(int array);
		public abstract void bind(int array);
	}
	
	private enum VertexAttribIMode
	{
		CORE
		{
			@Override 
			public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer)
			{
				GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
			}
		},
		EXT
		{
			@Override
			public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer)
			{
				EXTGpuShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
			}
		},
		NONE
		{
			@Override 
			public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer)
			{
				throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
			}
		};
		
		public abstract void vertexAttribIPointer(int index, int size, int type, int stride, long pointer);
	}
	
	
	
	public static LWJGL2Service create()
	{
		ContextCapabilities caps = GLContext.getCapabilities();
		
		VAOMode vaoMode;
		if (caps.OpenGL30)
		{
			vaoMode = VAOMode.CORE;
		}
		else if (caps.GL_ARB_vertex_array_object)
		{
			vaoMode = VAOMode.ARB;
		}
		else if (caps.GL_APPLE_vertex_array_object)
		{
			vaoMode = VAOMode.APPLE;
		}
		else
		{
			vaoMode = VAOMode.NONE;
		}

		VertexAttribIMode vertexAttribIMode;
		if (caps.OpenGL30)
		{
			vertexAttribIMode = VertexAttribIMode.CORE;
		}
		else if (caps.GL_EXT_gpu_shader4)
		{
			vertexAttribIMode = VertexAttribIMode.EXT;
		}
		else
		{
			vertexAttribIMode = VertexAttribIMode.NONE;
		}
		
		return new LWJGL2Service(vaoMode, vertexAttribIMode);
	}
	
	
	
	
	// ===================== CAPABILITIES =====================
	
	@Override
	public boolean isOpenGLVersionSupported(int major, int minor)
	{
		ContextCapabilities caps = GLContext.getCapabilities();
		switch (major * 10 + minor)
		{
			case 11:
				return caps.OpenGL11;
			case 12:
				return caps.OpenGL12;
			case 13:
				return caps.OpenGL13;
			case 14:
				return caps.OpenGL14;
			case 15:
				return caps.OpenGL15;
			case 20:
				return caps.OpenGL20;
			case 21:
				return caps.OpenGL21;
			case 30:
				return caps.OpenGL30;
			case 31:
				return caps.OpenGL31;
			case 32:
				return caps.OpenGL32;
			case 33:
				return caps.OpenGL33;
			case 40:
				return caps.OpenGL40;
			case 41:
				return caps.OpenGL41;
			case 42:
				return caps.OpenGL42;
			case 43:
				return caps.OpenGL43;
			case 44:
				return caps.OpenGL44;
			default:
				return false;
		}
	}
	
	@Override
	public boolean isFeatureSupported(EGLFeature feature)
	{
		ContextCapabilities caps = GLContext.getCapabilities();
		switch (feature)
		{
			case glBufferStorage: return isCapabilityFunctionLoaded(caps, "glBufferStorage");
			case glBindVertexBuffer: return isCapabilityFunctionLoaded(caps, "glBindVertexBuffer");
			default: throw new UnsupportedOperationException("No logic defined for feature ["+feature+"].");
		}
	}
	/** LWJGL 2 keeps each function's pointer in a private field of the same name, 0 if the driver doesn't have it */
	private static boolean isCapabilityFunctionLoaded(ContextCapabilities caps, String functionName)
	{
		try
		{
			Field field = caps.getClass().getDeclaredField(functionName);
			field.setAccessible(true);
			return field.getLong(caps) != 0L;
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			LOGGER.error("Unable to check the GL function ["+functionName+"], error: ["+e.getMessage()+"].", e);
			return false;
		}
	}

	@Override
	public boolean isExtensionSupported(EGLExtension extension)
	{
		ContextCapabilities caps = GLContext.getCapabilities();
		switch (extension)
		{
			case ARB_buffer_storage: return caps.GL_ARB_buffer_storage;
			case ARB_multi_draw_indirect: return caps.GL_ARB_multi_draw_indirect;
			case ARB_draw_elements_base_vertex: return caps.GL_ARB_draw_elements_base_vertex;
			case ARB_shader_storage_buffer_object: return caps.GL_ARB_shader_storage_buffer_object;
			case ARB_sync: return caps.GL_ARB_sync;
			case ARB_timer_query: return caps.GL_ARB_timer_query;
			case ARB_debug_output: return caps.GL_ARB_debug_output;
			case KHR_debug: return caps.GL_KHR_debug;
			case AMD_debug_output: return caps.GL_AMD_debug_output;
			case ARB_uniform_buffer_object: return caps.GL_ARB_uniform_buffer_object;
			case ARB_vertex_array_object: return caps.GL_ARB_vertex_array_object;
			case ARB_map_buffer_range: return caps.GL_ARB_map_buffer_range;
			case ARB_copy_buffer: return caps.GL_ARB_copy_buffer;
			case ARB_texture_storage: return caps.GL_ARB_texture_storage;
			case ARB_base_instance: return caps.GL_ARB_base_instance;
			case ARB_compatibility: return caps.GL_ARB_compatibility;
			case ARB_instanced_arrays: return caps.GL_ARB_instanced_arrays;
			
			default: throw new UnsupportedOperationException("No logic defined for extension ["+extension+"].");
		}
	}
	
	// ===================== BUFFER OPERATIONS =====================
	
	@Override
	public int glGenBuffers()
	{
		return GL15.glGenBuffers();
	}
	
	@Override
	public void glDeleteBuffers(int buffer)
	{
		GL15.glDeleteBuffers(buffer);
	}
	
	@Override
	public void glBindBuffer(int target, int buffer)
	{
		GL15.glBindBuffer(target, buffer);
	}
	
	@Override
	public void glBufferData(int target, long size, int usage)
	{
		GL15.glBufferData(target, size, usage);
	}
	
	@Override
	public void glBufferData(int target, ByteBuffer data, int usage)
	{
		GL15.glBufferData(target, data, usage);
	}
	
	@Override
	public void glBufferData(int target, int[] data, int usage)
	{
		IntBuffer buf = MemoryUtilities.memAllocInt(data.length);
		buf.put(data).flip();
		GL15.glBufferData(target, buf, usage);
		MemoryUtilities.memFree(buf);
	}
	
	@Override
	public void glBufferData(int target, float[] data, int usage)
	{
		FloatBuffer buf = MemoryUtilities.memAllocFloat(data.length);
		buf.put(data).flip();
		GL15.glBufferData(target, buf, usage);
		MemoryUtilities.memFree(buf);
	}
	
	@Override
	public void glBufferStorage(int target, ByteBuffer bb, int flags)
	{
		ARBBufferStorage.glBufferStorage(target, bb, flags);
	}
	
	@Override
	public void glBufferSubData(int target, long size, ByteBuffer data)
	{
		GL15.glBufferSubData(target, size, data);
	}

	@Override
	public void glBindVertexBuffer(int bindingindex, int buffer, int offset, int stride)
	{
		GL43.glBindVertexBuffer(bindingindex, buffer, offset, stride);
	}
	
	// ===================== VAO OPERATIONS =====================
	
	@Override
	public int glGenVertexArrays()
	{
		return vaoMode.gen();
	}
	
	@Override
	public void glDeleteVertexArrays(int array)
	{
		vaoMode.delete(array);
	}
	
	@Override
	public void glBindVertexArray(int array)
	{
		vaoMode.bind(array);
	}
	
	@Override
	public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer)
	{
		GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
	}
	
	@Override
	public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer)
	{
		vertexAttribIMode.vertexAttribIPointer(index, size, type, stride, pointer);
	}
	
	@Override
	public void glEnableVertexAttribArray(int index)
	{
		GL20.glEnableVertexAttribArray(index);
	}
	
	@Override
	public void glDisableVertexAttribArray(int index)
	{
		GL20.glDisableVertexAttribArray(index);
	}
	
	@Override
	public void glVertexAttribDivisor(int index, int divisor)
	{
		GL33.glVertexAttribDivisor(index, divisor);
	}
	
	@Override
	public void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset)
	{
		GL43.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
	}
	
	@Override
	public void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset)
	{
		GL43.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
	}
	
	@Override
	public void glVertexAttribBinding(int attribindex, int bindingindex)
	{
		GL43.glVertexAttribBinding(attribindex, bindingindex);
	}
	
	// ===================== SHADER OPERATIONS =====================
	
	@Override
	public int glCreateShader(int type)
	{
		return GL20.glCreateShader(type);
	}
	
	@Override
	public void glShaderSourceSafe(int shader, CharSequence source)
	{
		// AMD driver workaround: pass null for string length to force null-terminator reliance.
		// Some AMD drivers don't receive or interpret the length correctly, resulting in an
		// access violation when the driver tries to read past the string memory.
		// In LWJGL2, we use the normal method which should be null-terminated aware.
		ByteBuffer sourceBuffer = MemoryUtilities.memUTF8(source, true);
		GL20.glShaderSource(shader, sourceBuffer);
	}
	
	@Override
	public void glCompileShader(int shader)
	{
		GL20.glCompileShader(shader);
	}
	
	@Override
	public String glGetShaderInfoLog(int shader, int maxLength)
	{
		return GL20.glGetShaderInfoLog(shader, maxLength);
	}
	
	@Override
	public int glGetShaderi(int shader, int pname)
	{
		return GL20.glGetShaderi(shader, pname);
	}
	
	@Override
	public void glDeleteShader(int shader)
	{
		GL20.glDeleteShader(shader);
	}
	
	@Override
	public int glCreateProgram()
	{
		return GL20.glCreateProgram();
	}
	
	@Override
	public void glAttachShader(int program, int shader)
	{
		GL20.glAttachShader(program, shader);
	}
	
	@Override
	public void glLinkProgram(int program)
	{
		GL20.glLinkProgram(program);
	}
	
	@Override
	public String glGetProgramInfoLog(int program, int maxLength)
	{
		return GL20.glGetProgramInfoLog(program, maxLength);
	}
	
	@Override
	public int glGetProgrami(int program, int pname)
	{
		return GL20.glGetProgrami(program, pname);
	}
	
	@Override
	public void glUseProgram(int program)
	{
		GL20.glUseProgram(program);
	}
	
	@Override
	public void glDeleteProgram(int program)
	{
		GL20.glDeleteProgram(program);
	}
	
	@Override
	public void glBindAttribLocation(int program, int index, CharSequence name)
	{
		GL20.glBindAttribLocation(program, index, name);
	}

	// ===================== UNIFORM OPERATIONS =====================

	@Override
	public int glGetUniformLocation(int program, CharSequence name)
	{
		return GL20.glGetUniformLocation(program, name);
	}

	@Override
	public void glUniform1f(int location, float v0)
	{
		GL20.glUniform1f(location, v0);
	}

	@Override
	public void glUniform1i(int location, int v0)
	{
		GL20.glUniform1i(location, v0);
	}

	@Override
	public void glUniform2f(int location, int v0, int v1)
	{
		GL20.glUniform2f(location, v0, v1);
	}
	
	@Override
	public void glUniform3f(int location, float v0, float v1, float v2)
	{
		GL20.glUniform3f(location, v0, v1, v2);
	}
	
	@Override
	public void glUniform3i(int location, int v0, int v1, int v2)
	{
		GL20.glUniform3i(location, v0, v1, v2);
	}

	@Override
	public void glUniform4f(int location, float v0, float v1, float v2, float v3)
	{
		GL20.glUniform4f(location, v0, v1, v2, v3);
	}

	@Override
	public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value)
	{
		GL20.glUniformMatrix4(location, transpose, value);
	}
	
	@Override
	public void glDrawArrays(int mode, int first, int count)
	{
		GL11.glDrawArrays(mode, first, count);
	}
	
	@Override
	public void glDrawElements(int mode, int count, int type, int indices)
	{
		GL11.glDrawElements(mode, count, type, indices);
	}
	
	// ===================== DRAW OPERATIONS =====================

	@Override
	public void glDrawElementsInstanced(int mode, int count, int type, int indices, int instancecount)
	{
		GL31.glDrawElementsInstanced(mode, count, type, indices, instancecount);
	}

	// ===================== SYNC OPERATIONS =====================

	@Override
	public void glFlush()
	{
		GL11.glFlush();
	}

	// ===================== TEXTURE OPERATIONS =====================

	@Override
	public int glGenTextures()
	{
		return GL11.glGenTextures();
	}

	@Override
	public void glDeleteTextures(int texture)
	{
		GL11.glDeleteTextures(texture);
	}

	@Override
	public void glBindTexture(int target, int texture)
	{
		GL11.glBindTexture(target, texture);
	}

	@Override
	public void glActiveTexture(int texture)
	{
		GL13.glActiveTexture(texture);
	}

	@Override
	public void glPixelStorei(int pname, int param)
	{
		GL11.glPixelStorei(pname, param);
	}
	
	@Override
	public void glTexParameteri(int target, int pname, int param)
	{
		GL11.glTexParameteri(target, pname, param);
	}
	
	@Override
	public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels)
	{
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}
	@Override
	public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels)
	{
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}
	
	@Override
	public void glGenerateMipmap(int target)
	{
		GL30.glGenerateMipmap(target);
	}
	
	@Override
	public boolean glIsTexture(int texture)
	{
		return GL11.glIsTexture(texture);
	}
	
	@Override
	public void glBindSampler(int unit, int sampler)
	{
		GL33.glBindSampler(unit, sampler);
	}
	
	// ===================== FRAMEBUFFER OPERATIONS =====================
	
	@Override
	public int glGenFramebuffers()
	{
		return GL30.glGenFramebuffers();
	}
	
	@Override
	public void glDeleteFramebuffers(int framebuffer)
	{
		GL30.glDeleteFramebuffers(framebuffer);
	}
	
	@Override
	public void glBindFramebuffer(int target, int framebuffer)
	{
		GL30.glBindFramebuffer(target, framebuffer);
	}
	
	@Override
	public int glCheckFramebufferStatus(int target)
	{
		return GL30.glCheckFramebufferStatus(target);
	}
	
	@Override
	public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level)
	{
		GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
	}
	
	@Override
	public void glFramebufferTexture(int target, int attachment, int texture, int level)
	{
		GL32.glFramebufferTexture(target, attachment, texture, level);
	}
	
	@Override
	public int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname)
	{
		return GL30.glGetFramebufferAttachmentParameteri(target, attachment, pname);
	}
	
	// ===================== STATE OPERATIONS =====================
	
	@Override
	public void glEnable(int cap)
	{
		GL11.glEnable(cap);
	}
	
	@Override
	public void glDisable(int cap)
	{
		GL11.glDisable(cap);
	}
	
	@Override
	public boolean glIsEnabled(int cap)
	{
		return GL11.glIsEnabled(cap);
	}
	
	@Override
	public void glBlendFunc(int sfactor, int dfactor)
	{
		GL11.glBlendFunc(sfactor, dfactor);
	}
	
	@Override
	public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha)
	{
		GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
	}
	
	@Override
	public void glBlendEquation(int mode)
	{
		GL14.glBlendEquation(mode);
	}
	
	@Override
	public void glBlendEquationSeparate(int modeRGB, int modeAlpha)
	{
		GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
	}
	
	@Override
	public void glDepthFunc(int func)
	{
		GL11.glDepthFunc(func);
	}
	
	@Override
	public void glDepthMask(boolean flag)
	{
		GL11.glDepthMask(flag);
	}
	
	@Override
	public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha)
	{
		GL11.glColorMask(red, green, blue, alpha);
	}
	
	@Override
	public void glViewport(int x, int y, int width, int height)
	{
		GL11.glViewport(x, y, width, height);
	}
	
	@Override
	public void glClear(int mask)
	{
		GL11.glClear(mask);
	}
	
	@Override
	public void glClearColor(float red, float green, float blue, float alpha)
	{
		GL11.glClearColor(red, green, blue, alpha);
	}
	
	@Override
	public void glClearDepth(double depth)
	{
		GL11.glClearDepth(depth);
	}
	
	@Override
	public void glCullFace(int mode)
	{
		GL11.glCullFace(mode);
	}
	
	@Override
	public void glPolygonMode(int face, int mode)
	{
		GL11.glPolygonMode(face, mode);
	}
	
	@Override
	public void glStencilFunc(int func, int ref, int mask)
	{
		GL11.glStencilFunc(func, ref, mask);
	}
	
	@Override
	public void glColor4f(float v, float v1, float v2, float v3)
	{
		GL11.glColor4f(v, v1, v2, v3);
	}

	// ===================== Fog =========================
	
	@Override
	public void glFogf(int pname, float params) { GL11.glFogf(pname, params); }
	
	// ===================== MISC GL =====================
	
	@Override
	public int glGetInteger(int pname)
	{
		return GL11.glGetInteger(pname);
	}
	
	@Override
	public void glGetIntegerv(int pname, int[] params)
	{
		IntBuffer buf = MemoryUtilities.memAllocInt(Math.max(params.length, 16));
		GL11.glGetInteger(pname, buf);
		buf.get(params, 0, params.length);
		MemoryUtilities.memFree(buf);
	}
	
	@Override
	public void glGetFloatv(int pname, float[] params)
	{
		FloatBuffer buf = MemoryUtilities.memAllocFloat(Math.max(params.length, 16));
		GL11.glGetFloat(pname, buf);
		buf.get(params, 0, params.length);
		MemoryUtilities.memFree(buf);
	}
	
	@Override
	public boolean glGetBoolean(int pname)
	{
		return GL11.glGetBoolean(pname);
	}
	
	@Override
	public String glGetString(int pname)
	{
		return GL11.glGetString(pname);
	}

	// ===================== MEMORY STACK OPERATIONS =====================

	@Override
	public AbstractMemoryStack stackPush()
	{
		return new LWJGL2MemoryStack(MemoryStack.stackPush());
	}

	// ===================== NATIVE MEMORY OPERATIONS =====================

    @Override
	public boolean isRunningOnRenderThread()
	{
		try
		{
			return Display.getDrawable().isCurrent();
		}
		catch (LWJGLException e)
		{
			return false;
		}
	}
	
}
