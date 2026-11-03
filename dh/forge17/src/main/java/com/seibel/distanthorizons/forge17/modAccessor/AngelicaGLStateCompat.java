package com.seibel.distanthorizons.forge17.modAccessor;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Angelica 2.2.40 moved GLStateManager's methods into GLSMPrograms, GLSMTextures, GLSMFog etc.
 * with the same signatures, these call them wherever the installed Angelica has them. <br>
 * The handles are static final so the JIT inlines them like direct calls.
 */
public final class AngelicaGLStateCompat
{
	private static final String GLSM_PACKAGE = "com.gtnewhorizons.angelica.glsm.";

	private static final MethodHandle GET_ACTIVE_PROGRAM = find("GLSMPrograms", "getActiveProgram", MethodType.methodType(int.class));
	private static final MethodHandle GL_GET_UNIFORM_LOCATION = find("GLSMPrograms", "glGetUniformLocation", MethodType.methodType(int.class, int.class, CharSequence.class));
	private static final MethodHandle GL_UNIFORM_1I = find("GLSMPrograms", "glUniform1i", MethodType.methodType(void.class, int.class, int.class));
	private static final MethodHandle GET_ACTIVE_TEXTURE_UNIT = find("GLSMTextures", "getActiveTextureUnit", MethodType.methodType(int.class));
	private static final MethodHandle GL_ACTIVE_TEXTURE = find("GLSMTextures", "glActiveTexture", MethodType.methodType(void.class, int.class));
	private static final MethodHandle GL_BIND_TEXTURE = find("GLSMTextures", "glBindTexture", MethodType.methodType(void.class, int.class, int.class));
	private static final MethodHandle GET_FOG_STATE = find("GLSMFog", "getFogState", MethodType.methodType(FogStateStack.class));

	private AngelicaGLStateCompat() { }

	//=========//
	// methods //
	//=========//
	//region

	public static int getActiveProgram()
	{
		try { return (int) GET_ACTIVE_PROGRAM.invokeExact(); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static int glGetUniformLocation(int program, CharSequence name)
	{
		try { return (int) GL_GET_UNIFORM_LOCATION.invokeExact(program, name); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static void glUniform1i(int location, int value)
	{
		try { GL_UNIFORM_1I.invokeExact(location, value); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static int getActiveTextureUnit()
	{
		try { return (int) GET_ACTIVE_TEXTURE_UNIT.invokeExact(); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static void glActiveTexture(int texture)
	{
		try { GL_ACTIVE_TEXTURE.invokeExact(texture); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static void glBindTexture(int target, int texture)
	{
		try { GL_BIND_TEXTURE.invokeExact(target, texture); }
		catch (Throwable e) { throw rethrow(e); }
	}

	public static FogStateStack getFogState()
	{
		try { return (FogStateStack) GET_FOG_STATE.invokeExact(); }
		catch (Throwable e) { throw rethrow(e); }
	}

	//endregion

	//=========//
	// helpers //
	//=========//
	//region

	/** @param newClassName the class the method is in since Angelica 2.2.40, before that it's in GLStateManager */
	private static MethodHandle find(String newClassName, String methodName, MethodType type)
	{
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try
		{
			Class<?> newClass = Class.forName(GLSM_PACKAGE + newClassName, true, AngelicaGLStateCompat.class.getClassLoader());
			return lookup.findStatic(newClass, methodName, type);
		}
		catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e)
		{
			try
			{
				return lookup.findStatic(GLStateManager.class, methodName, type);
			}
			catch (NoSuchMethodException | IllegalAccessException oldException)
			{
				IllegalStateException exception = new IllegalStateException("Angelica's [" + methodName + "] wasn't found in [" + newClassName + "] or [" + GLStateManager.class.getSimpleName() + "].", oldException);
				exception.addSuppressed(e);
				throw exception;
			}
		}
	}

	private static RuntimeException rethrow(Throwable throwable)
	{
		if (throwable instanceof RuntimeException)
		{
			return (RuntimeException) throwable;
		}
		else if (throwable instanceof Error)
		{
			throw (Error) throwable;
		}
		return new RuntimeException(throwable);
	}

	//endregion
}
