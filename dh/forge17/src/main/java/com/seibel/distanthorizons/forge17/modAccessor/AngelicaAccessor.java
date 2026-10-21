package com.seibel.distanthorizons.forge17.modAccessor;

import java.awt.Color;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import com.seibel.distanthorizons.forge17.modAccessor.exceptions.AngelicaVersionGuiException;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.irisshaders.iris.api.v0.IrisApi;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;

import java.nio.FloatBuffer;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;

public class AngelicaAccessor implements IAngelicaAccessor
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	public static final String ANGELICA_MOD_ID = "angelica";
	public static final String MINIMUM_ANGELICA_VERSION = "2.1.5";
	public static final VersionRange SUPPORTED_ANGELICA_RANGE = VersionParser
		.parseRange("[" + MINIMUM_ANGELICA_VERSION + ",)");
	
	@Override
	public String getModName() { return ANGELICA_MOD_ID; }
	
	
	
	//====================//
	// version validation //
	//====================//
	//region
	
	public void throwIfUnsupportedAngelicaVersion()
		throws IllegalStateException, AngelicaVersionGuiException
	{
		ModContainer angelica = Loader.instance()
			.getIndexedModList()
			.get(ANGELICA_MOD_ID);
		
		if (angelica == null)
		{
			throw new IllegalStateException("Angelica mod container could not be found.");
		}
		
		String installedVersion = angelica.getVersion();
		ArtifactVersion installedArtifactVersion = new DefaultArtifactVersion(installedVersion);
		if (SUPPORTED_ANGELICA_RANGE.containsVersion(installedArtifactVersion))
		{
			return;
		}
		
		throw new AngelicaVersionGuiException(installedVersion, MINIMUM_ANGELICA_VERSION);
	}
	
	//endregion
	
	
	
	//==================//
	// accessor methods //
	//==================//
	//region
	
	@Override
	public int getDepthTextureId() 
    {
	    final Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
		return ((IRenderTargetExt) framebuffer).iris$getDepthTextureId(); 
	}

	@Override
    public boolean canDoFadeShader() { return AngelicaConfig.enableIris; }

	@Override
	public void uploadMissingShaderUniforms()
	{
		if (this.dhProjectionUploadFailed
			|| !AngelicaConfig.enableIris
			|| !IrisApi.getInstance().isShaderPackInUse()
			|| IrisApi.getInstance().isRenderingShadowPass())
		{
			return;
		}

		try
		{
			int program = GLStateManager.getActiveProgram();
			if (program == 0)
			{
				return;
			}

			// called through reflection since the matrix is JOML, which DH relocates (see getFogColor)
			if (this.getDhProjectionMethod == null)
			{
				this.getDhProjectionMethod = DHCompat.class.getMethod("getProjection");
			}
			Object projection = this.getDhProjectionMethod.invoke(null);
			if (this.matrixGetMethod == null)
			{
				this.matrixGetMethod = projection.getClass().getMethod("get", FloatBuffer.class);
			}
			this.projectionBuffer.clear();
			this.matrixGetMethod.invoke(projection, this.projectionBuffer);

			float[] matrix = new float[16];
			this.projectionBuffer.rewind();
			this.projectionBuffer.get(matrix);
			float[] inverse = invertColumnMajor(matrix);

			uploadMatrix(program, "dhProjection", matrix);
			uploadMatrix(program, "dhPreviousProjection", matrix);
			if (inverse != null)
			{
				uploadMatrix(program, "dhProjectionInverse", inverse);
			}

			// same value Angelica uses
			WorldClient level = Minecraft.getMinecraft().theWorld;
			int worldHeight = (level != null && level.provider != null) ? level.provider.getHeight() : 256;
			uploadInt(program, "heightLimit", worldHeight);
			uploadInt(program, "logicalHeightLimit", worldHeight);
		}
		catch (Throwable e)
		{
			this.dhProjectionUploadFailed = true;
			LOGGER.warn("Unable to upload the DH projection to the shader pack, LODs may not show up without a shadow pass. Error: ["+e.getMessage()+"].", e);
		}
	}
	private boolean dhProjectionUploadFailed = false;
	private Method getDhProjectionMethod = null;
	private Method matrixGetMethod = null;
	private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer uploadBuffer = BufferUtils.createFloatBuffer(16);

	private void uploadMatrix(int program, String name, float[] matrix)
	{
		int location = GL20.glGetUniformLocation(program, name);
		if (location == -1)
		{
			return;
		}

		this.uploadBuffer.clear();
		this.uploadBuffer.put(matrix);
		this.uploadBuffer.flip();
		GL20.glUniformMatrix4(location, false, this.uploadBuffer);
	}

	private static void uploadInt(int program, String name, int value)
	{
		int location = GL20.glGetUniformLocation(program, name);
		if (location != -1)
		{
			GL20.glUniform1i(location, value);
		}
	}

	/** @return null if the matrix can't be inverted */
	private static float[] invertColumnMajor(float[] m)
	{
		float[] inv = new float[16];
		inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
		inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
		inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
		inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
		inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
		inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
		inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
		inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
		inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
		inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
		inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
		inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
		inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
		inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
		inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
		inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

		float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
		if (det == 0)
		{
			return null;
		}

		for (int i = 0; i < 16; i++)
		{
			inv[i] /= det;
		}
		return inv;
	}

	@Override
    public Color getFogColor() 
    {
        // Read the fog color buffer rather than GLStateManager.getFogColor(), which returns
        // an org.joml.Vector3d. DH shades and relocates JOML, so calling that method would
        // rewrite the descriptor in this class too and fail with a NoSuchMethodError against
        // Angelica's un-relocated JOML. Angelica keeps this buffer in sync with the vector.
        // Layout is (red, green, blue, alpha); the fog alpha isn't used here.
        FloatBuffer fogColor = GLStateManager.getFogState().getFogColorBuffer();
        return new Color(
            Math.max(0.0f, Math.min(1.0f, fogColor.get(0))),
            Math.max(0.0f, Math.min(1.0f, fogColor.get(1))),
            Math.max(0.0f, Math.min(1.0f, fogColor.get(2))));
    }
	
	//endregion
	
	
	
}
