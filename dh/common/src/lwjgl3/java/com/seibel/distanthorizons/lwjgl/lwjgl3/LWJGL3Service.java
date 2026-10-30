package com.seibel.distanthorizons.lwjgl.lwjgl3;

import com.seibel.distanthorizons.lwjgl.AbstractMemoryStack;
import com.seibel.distanthorizons.lwjgl.EGLExtension;
import com.seibel.distanthorizons.lwjgl.EGLFeature;
import com.seibel.distanthorizons.lwjgl.ILWJGLService;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * LWJGL3 implementation of {@link ILWJGLService}.
 */
// TODO why is this a record?
@SuppressWarnings("ALL") public class LWJGL3Service implements ILWJGLService
{
	private static final Thread RENDER_THREAD = Thread.currentThread();

	private final VAOMode vaoMode;
	private final VertexAttribIMode vertexAttribIMode;

	private LWJGL3Service(
		VAOMode vaoMode,
		VertexAttribIMode vertexAttribIMode)
	{
		this.vaoMode = vaoMode;
		this.vertexAttribIMode = vertexAttribIMode;
	}
	
	
	
	// ===================== CAPABILITIES =====================
	
	@Override
    public boolean isOpenGLVersionSupported(int major, int minor) 
	{
        GLCapabilities caps = GL.getCapabilities();
        switch (major * 10 + minor) 
        {
            case 11: return caps.OpenGL11;
            case 12: return caps.OpenGL12;
            case 13: return caps.OpenGL13;
            case 14: return caps.OpenGL14;
            case 15: return caps.OpenGL15;
            case 20: return caps.OpenGL20;
            case 21: return caps.OpenGL21;
            case 30: return caps.OpenGL30;
            case 31: return caps.OpenGL31;
            case 32: return caps.OpenGL32;
            case 33: return caps.OpenGL33;
            case 40: return caps.OpenGL40;
            case 41: return caps.OpenGL41;
            case 42: return caps.OpenGL42;
            case 43: return caps.OpenGL43;
            case 44: return caps.OpenGL44;
            case 45: return caps.OpenGL45;
            case 46: return caps.OpenGL46;
            default: return false;
        }
    }

    @Override
    public boolean isFeatureSupported(EGLFeature feature)
    {
        GLCapabilities caps = GL.getCapabilities();
        switch (feature)
        {
            case glBufferStorage: return caps.glBufferStorage != 0L;
            case glBindVertexBuffer: return caps.glBindVertexBuffer != 0L;
            default: throw new UnsupportedOperationException("No logic defined for feature ["+feature+"].");
        }
    }

    @Override
    public boolean isExtensionSupported(EGLExtension extension)
    {
        GLCapabilities caps = GL.getCapabilities();
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
	        case ARB_instanced_arrays: return caps.GL_ARB_instanced_arrays;
	        case ARB_compatibility: return caps.GL_ARB_compatibility;
			
	        default: throw new UnsupportedOperationException("No logic defined for extension ["+extension+"].");
        }
    }

	
	
    // ===================== BUFFER OPERATIONS =====================

    @Override
    public int glGenBuffers() {
        return GL15C.glGenBuffers();
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void glBufferData(int target, long size, int usage) {
        GL15C.glBufferData(target, size, usage);
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        GL15C.glBufferData(target, data, usage);
    }

	@Override
	public void glBufferData(int target, int[] data, int usage)
	{
		GL15C.glBufferData(target, data, usage);	
	}
	
	@Override
	public void glBufferData(int target, float[] data, int usage)
	{
		GL15C.glBufferData(target, data, usage);
	}
	
	@Override
	public void glBufferStorage(int target, ByteBuffer bb, int flags) {
		GL44C.glBufferStorage(target, bb, flags);
	}
	
	@Override
	public void glBufferSubData(int target, long size, ByteBuffer data)
	{
		GL15.glBufferSubData(target, size, data);
	}

	@Override
	public void glBindVertexBuffer(int bindingindex, int buffer, int offset, int stride)
	{
		GL43C.glBindVertexBuffer(bindingindex, buffer, offset, stride);
	}
	
	
	
	private enum VAOMode 
	{
        CORE 
        {
            @Override public int gen() { return GL30C.glGenVertexArrays(); }
            @Override public void delete(int array) { GL30C.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { GL30C.glBindVertexArray(array); }
        },
        ARB 
	        {
            @Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
            @Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
        },
        APPLE 
        {
            @Override 
            public int gen() 
            {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) 
                {
                    IntBuffer buf = stack.callocInt(1);
                    JNI.callPV(1, MemoryUtil.memAddress(buf), glGenVertexArraysAPPLE);
                    return buf.get(0);
                }
            }
            @Override 
            public void delete(int array) 
            {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) 
                {
                    IntBuffer buf = stack.ints(array);
                    JNI.callPV(1, MemoryUtil.memAddress(buf), glDeleteVertexArraysAPPLE);
                }
            }
            @Override 
            public void bind(int array) { JNI.callV(array, glBindVertexArrayAPPLE); }
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
    private enum VertexAttribIMode { CORE, EXT, NONE }


    // Cached function addresses for APPLE VAO extensions.
    // Looked up in create() instead of during class init: lwjgl3ify sets OPENGL_EXPLICIT_INIT,
    // so GL.getFunctionProvider() can be null until the GL context exists, and an exception
    // during class init would permanently break this class.
    private static long glGenVertexArraysAPPLE = 0;
    private static long glDeleteVertexArraysAPPLE = 0;
    private static long glBindVertexArrayAPPLE = 0;

	
	
    public static LWJGL3Service create() 
    {
        GLCapabilities caps = GL.getCapabilities();
        resolveAppleVaoFunctions();

        VAOMode vaoMode;
	    
	    if (caps.OpenGL30)
	    {
		    vaoMode = VAOMode.CORE;
	    }
	    else if (caps.GL_ARB_vertex_array_object)
	    {
		    vaoMode = VAOMode.ARB;
	    }
	    else if (glBindVertexArrayAPPLE != 0)
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

        return new LWJGL3Service(vaoMode, vertexAttribIMode);
    }
    /** If no function provider is available the APPLE VAO path is treated as unsupported, it's only an old macOS fallback. */
    private static void resolveAppleVaoFunctions()
    {
        FunctionProvider provider = GL.getFunctionProvider();
        if (provider == null)
        {
            return;
        }
        
        glGenVertexArraysAPPLE = provider.getFunctionAddress("glGenVertexArraysAPPLE");
        glDeleteVertexArraysAPPLE = provider.getFunctionAddress("glDeleteVertexArraysAPPLE");
        glBindVertexArrayAPPLE = provider.getFunctionAddress("glBindVertexArrayAPPLE");
    }

	
	
    @Override
    public int glGenVertexArrays() {
        return vaoMode.gen();
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        vaoMode.delete(array);
    }

    @Override
    public void glBindVertexArray(int array) {
        vaoMode.bind(array);
    }

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) 
    {
        switch (vertexAttribIMode)
        {
            case CORE: 
				GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
				break;
            case EXT: 
				EXTGPUShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
				break;
            case NONE: 
				throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
        }
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        GL20C.glEnableVertexAttribArray(index);
    }
	
	@Override
	public void glDisableVertexAttribArray(int index)
	{
		GL20C.glDisableVertexAttribArray(index);
	}
	
	@Override
	public void glVertexAttribDivisor(int index, int divisor)
	{
		GL33C.glVertexAttribDivisor(index, divisor);
	}
	
	@Override
	public void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset)
	{
		GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
	}
	
	@Override
	public void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset)
	{
		GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
	}
	
	@Override
	public void glVertexAttribBinding(int attribindex, int bindingindex)
	{
		GL43C.glVertexAttribBinding(attribindex, bindingindex);
	}
	
	// ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return GL20C.glCreateShader(type);
    }

    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        // AMD driver workaround: pass null for string length to force null-terminator reliance.
        // Some AMD drivers don't receive or interpret the length correctly, resulting in an
        // access violation when the driver tries to read past the string memory.
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);
            GL20C.nglShaderSource(shader, 1, pointers.address0(), 0);
            APIUtil.apiArrayFree(pointers.address0(), 1);
        }
    }

    @Override
    public void glCompileShader(int shader) {
        GL20C.glCompileShader(shader);
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        // LWJGL3 doesn't need maxLength, but we accept it for API compatibility
        return GL20C.glGetShaderInfoLog(shader);
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        return GL20C.glGetShaderi(shader, pname);
    }

    @Override
    public void glDeleteShader(int shader) {
        GL20C.glDeleteShader(shader);
    }

    @Override
    public int glCreateProgram() {
        return GL20C.glCreateProgram();
    }

    @Override
    public void glAttachShader(int program, int shader) {
        GL20C.glAttachShader(program, shader);
    }

    @Override
    public void glLinkProgram(int program) {
        GL20C.glLinkProgram(program);
    }

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        return GL20C.glGetProgramInfoLog(program);
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    @Override
    public void glUseProgram(int program) {
        GL20C.glUseProgram(program);
    }

    @Override
    public void glDeleteProgram(int program) {
        GL20C.glDeleteProgram(program);
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        GL20C.glBindAttribLocation(program, index, name);
    }

    // ===================== UNIFORM OPERATIONS =====================

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public void glUniform1f(int location, float v0) {
        GL20C.glUniform1f(location, v0);
    }

    @Override
    public void glUniform1i(int location, int v0) {
        GL20C.glUniform1i(location, v0);
    }

	@Override
	public void glUniform2f(int location, int v0, int v1)
	{
		GL20C.glUniform2f(location, v0, v1);
	}
	
	@Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        GL20C.glUniform3f(location, v0, v1, v2);
    }
	
	@Override
	public void glUniform3i(int location, int v0, int v1, int v2)
	{
		GL20C.glUniform3i(location, v0, v1, v2);
	}

	@Override
	public void glUniform4f(int location, float v0, float v1, float v2, float v3)
	{
		GL20C.glUniform4f(location, v0, v1, v2, v3);
	}

    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }
	
	@Override
	public void glDrawArrays(int mode, int first, int count)
	{
		GL11C.glDrawArrays(mode, first, count);
	}
	
	@Override
	public void glDrawElements(int mode, int count, int type, int indices)
	{
		GL11C.glDrawElements(mode, count, type, indices);
	}
	
	// ===================== DRAW OPERATIONS =====================

	@Override
	public void glDrawElementsInstanced(int mode, int count, int type, int indices, int instancecount)
	{
		GL32C.glDrawElementsInstanced(mode, count, type, indices, instancecount);
	}

    // ===================== SYNC OPERATIONS =====================

	@Override
	public void glFlush()
	{
		GL11C.glFlush();
	}

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return GL11C.glGenTextures();
    }

    @Override
    public void glDeleteTextures(int texture) {
        GL11C.glDeleteTextures(texture);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    @Override
    public void glActiveTexture(int texture) {
        GL13C.glActiveTexture(texture);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        GL11C.glPixelStorei(pname, param);
    }
	
	@Override
	public void glTexParameteri(int target, int pname, int param)
	{
		GL11C.glTexParameteri(target, pname, param);
	}
	
	@Override
	public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels)
	{
		GL11C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}
	
	@Override
	public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels)
	{
		GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}
	
	@Override
	public void glGenerateMipmap(int target)
	{
		GL30C.glGenerateMipmap(target);
	}
	
	@Override
	public boolean glIsTexture(int texture)
	{
		return GL11C.glIsTexture(texture);
	}
	
	@Override
	public void glBindSampler(int unit, int sampler)
	{
		GL44C.glBindSampler(unit, sampler);
	}
	
	// ===================== FRAMEBUFFER OPERATIONS =====================

    @Override
    public int glGenFramebuffers() {
        return GL30C.glGenFramebuffers();
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        return GL30C.glCheckFramebufferStatus(target);
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
	
	@Override
	public void glFramebufferTexture(int target, int attachment, int texture, int level)
	{
		GL32C.glFramebufferTexture(target, attachment, texture, level);
	}
	
	@Override
	public int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname)
	{
		return GL30C.glGetFramebufferAttachmentParameteri(target, attachment, pname);
	}
	
	// ===================== STATE OPERATIONS =====================

    @Override
    public void glEnable(int cap) {
        GL11C.glEnable(cap);
    }

    @Override
    public void glDisable(int cap) {
        GL11C.glDisable(cap);
    }
	
	@Override
	public boolean glIsEnabled(int cap)
	{
		return GL11C.glIsEnabled(cap);
	}
	
	@Override
    public void glBlendFunc(int sfactor, int dfactor) {
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
	
	@Override
	public void glBlendEquation(int mode)
	{
		GL14C.glBlendEquation(mode);
	}
	
	@Override
	public void glBlendEquationSeparate(int modeRGB, int modeAlpha)
	{
		GL20C.glBlendEquationSeparate(modeRGB, modeAlpha);
	}
	
	@Override
    public void glDepthFunc(int func) {
        GL11C.glDepthFunc(func);
    }

    @Override
    public void glDepthMask(boolean flag) {
        GL11C.glDepthMask(flag);
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11C.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        GL11C.glViewport(x, y, width, height);
    }

    @Override
    public void glClear(int mask) {
        GL11C.glClear(mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        GL11C.glClearColor(red, green, blue, alpha);
    }
	
	@Override
	public void glClearDepth(double depth)
	{
		GL11C.glClearDepth(depth);	
	}
	
	@Override
	public void glCullFace(int mode)
	{
		GL11C.glCullFace(mode);
	}
	
	@Override
	public void glPolygonMode(int face, int mode)
	{
		GL11C.glPolygonMode(face, mode);
	}
	
	@Override
	public void glStencilFunc(int func, int ref, int mask)
	{
		GL11C.glStencilFunc(func, ref, mask);
	}

	@Override
	public void glColor4f(float v, float v1, float v2, float v3)
	{
		GL11.glColor4f(v, v1, v2, v3);
	}

	// ===================== Fog =========================
	
	@Override
	public void glFogf(int pname, float params) { GL32.glFogf(pname, params); }
	
	// ===================== MISC GL =====================

    @Override
    public int glGetInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        GL11C.glGetIntegerv(pname, params);
    }
	
	@Override
	public void glGetFloatv(int pname, float[] params)
	{
		GL11C.glGetFloatv(pname, params);
	}
	
	@Override
    public boolean glGetBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    @Override
    public String glGetString(int pname) {
        return GL11C.glGetString(pname);
    }

    // ===================== MEMORY STACK OPERATIONS =====================

    @Override
    public AbstractMemoryStack stackPush() {
        return new LWJGL3MemoryStack(org.lwjgl.system.MemoryStack.stackPush());
    }

    // ===================== NATIVE MEMORY OPERATIONS =====================
	
	@Override
	public boolean isRunningOnRenderThread()
	{
		return Thread.currentThread() == RENDER_THREAD;
	}
}
