/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.render.openGl.postProcessing.copy;

import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer;
import com.seibel.distanthorizons.common.render.openGl.glObject.GLState;
import com.seibel.distanthorizons.common.render.openGl.glObject.shader.GlShaderProgram;
import com.seibel.distanthorizons.common.render.openGl.postProcessing.GlScreenQuad;
import com.seibel.distanthorizons.common.render.openGl.terrain.VanillaChunkMask;
import com.seibel.distanthorizons.common.render.openGl.util.GlAbstractShaderRenderer;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftGLWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.util.math.DhVec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * Copies {@link com.seibel.distanthorizons.core.render.renderer.LodRenderer}'s
 * currently active color texture to Minecraft's framebuffer.
 */
public class GlDhCopyShader extends GlAbstractShaderRenderer
{
	public static GlDhCopyShader INSTANCE = new GlDhCopyShader();

	private static final MinecraftGLWrapper GLMC = MinecraftGLWrapper.INSTANCE;
	private static final AbstractDhRenderApiDefinition RENDER_DEF = SingletonInjector.INSTANCE.get(AbstractDhRenderApiDefinition.class);


	// uniforms
	public int uCopyTexture;
	public int uDepthTexture;
	public int uDhInvMvmProj;
	public int uDepthIsZeroToPositiveOne;
	public int uIsReverseZDepth;
	public int uKeepMcDistance;
	public int uVanillaChunkMask;
	public int uVanillaChunkMaskRadius;
	public int uCameraPosInChunk;



	//=======//
	// setup //
	//=======//
	//region

	private GlDhCopyShader() { }

	@Override
	public void onInit()
	{
		this.shader = new GlShaderProgram(
			"assets/distanthorizons/shaders/shared/gl/quad_apply.vert",
			"assets/distanthorizons/shaders/copy/gl/apply.frag",
			"vPosition"
		);

		// uniform setup
		this.uCopyTexture = this.shader.getUniformLocation("uCopyTexture");
		// try get, these are optimized out if the shader never uses them
		this.uDepthTexture = this.shader.tryGetUniformLocation("uDepthTexture");
		this.uDhInvMvmProj = this.shader.tryGetUniformLocation("uDhInvMvmProj");
		this.uDepthIsZeroToPositiveOne = this.shader.tryGetUniformLocation("uDepthIsZeroToPositiveOne");
		this.uIsReverseZDepth = this.shader.tryGetUniformLocation("uIsReverseZDepth");
		this.uKeepMcDistance = this.shader.tryGetUniformLocation("uKeepMcDistance");
		this.uVanillaChunkMask = this.shader.tryGetUniformLocation("uVanillaChunkMask");
		this.uVanillaChunkMaskRadius = this.shader.tryGetUniformLocation("uVanillaChunkMaskRadius");
		this.uCameraPosInChunk = this.shader.tryGetUniformLocation("uCameraPosInChunk");

	}

	//endregion



	//==========//
	// uniforms //
	//==========//
	//region

	@Override
	protected void onApplyUniforms(RenderParams renderParams)
	{
		this.shader.setUniform(this.uDhInvMvmProj, renderParams.dhInverseMvmProjectionMatrix);
		this.shader.setUniform(this.uDepthIsZeroToPositiveOne, (RENDER_DEF.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE) ? 1 : 0);
		this.shader.setUniform(this.uIsReverseZDepth, (RENDER_DEF.getDepthDirection() == EDhApiDepthDirection.REVERSE_Z) ? 1 : 0);

		// LODs are dithered in and the vanilla fade blends MC's terrain into them up to this distance,
		// where MC draws its own terrain over them they aren't copied, otherwise see-through LODs (IE water)
		// would be seen through MC's own while it fades, looking twice as bright
		this.shader.setUniform(this.uKeepMcDistance, isVanillaFadeActive() ? RenderUtil.getLodFadeStartDistanceInBlocks() * 1.9f : 0.0f);

		VanillaChunkMask chunkMask = VanillaChunkMask.INSTANCE;
		this.shader.setUniform(this.uVanillaChunkMask, VanillaChunkMask.GL_BOUND_INDEX);
		this.shader.setUniform(this.uVanillaChunkMaskRadius, chunkMask.getRadius());
		DhVec3d cameraPos = MC_RENDER.getCameraExactPosition();
		this.shader.setUniform(this.uCameraPosInChunk, new DhVec3f(
			(float) (cameraPos.x - (chunkMask.getCameraChunkX() * 16.0)),
			0.0f,
			(float) (cameraPos.z - (chunkMask.getCameraChunkZ() * 16.0))));
	}

	/** the same conditions MC's terrain is faded into the LODs under, see ClientApi#renderFadeTransparent and RenderHelper#renderFade */
	private static boolean isVanillaFadeActive()
	{
		if (Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DEFAULT
			|| Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() == EDhApiMcRenderingFadeMode.NONE
			|| Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			return false;
		}

		IIrisAccessor irisAccessor = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		if (irisAccessor != null
			&& irisAccessor.isShaderPackInUse())
		{
			return false;
		}

		IAngelicaAccessor angelicaAccessor = ModAccessorInjector.INSTANCE.get(IAngelicaAccessor.class);
		return angelicaAccessor == null || angelicaAccessor.canDoFadeShader();
	}

	//endregion



	//========//
	// render //
	//========//
	//region

	@Override
	protected void onRender()
	{
		if (MC_RENDER.mcRendersToFrameBuffer())
		{
			this.renderToFrameBuffer();
		}
		else
		{
			this.renderToMcTexture();
		}
	}

	@SuppressWarnings({"try", "unused"})
	private void renderToFrameBuffer()
	{
		int targetFrameBuffer = MC_RENDER.getTargetFramebuffer();
		if (targetFrameBuffer == -1)
		{
			return;
		}


		try (GLState state = new GLState())
		{

			GLMC.disableDepthTest();

			// blending isn't needed, we're manually merging the MC and DH textures
			// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
			// however this also fixes transparent LODs from glowing when rendered against the sky during the day
			GLMC.disableBlend();

			GLMC.glActiveTexture(GL13.GL_TEXTURE0);
			GLMC.glBindTexture(GlDhMetaRenderer.INSTANCE.getActiveColorTextureId());
			LWJGL.glUniform1i(this.uCopyTexture, 0);
			this.bindDepthAndChunkMask();

			// Copy to MC's framebuffer
			GLMC.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFrameBuffer);

			GlScreenQuad.INSTANCE.render();
		}
		// everything's been restored, except at this point the MC framebuffer should now be used instead
		GLMC.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFrameBuffer);

	}
	private void bindDepthAndChunkMask()
	{
		GLMC.glActiveTexture(GL13.GL_TEXTURE1);
		GLMC.glBindTexture(GlDhMetaRenderer.INSTANCE.getActiveDepthTextureId());
		LWJGL.glUniform1i(this.uDepthTexture, 1);

		// unbound in the render pass cleanup
		VanillaChunkMask.INSTANCE.bind();
		GLMC.glActiveTexture(GL13.GL_TEXTURE0);
	}

	@SuppressWarnings({"try", "unused"})
	private void renderToMcTexture()
	{
		int targetColorTextureId = MC_RENDER.getGlColorTextureId();
		if (targetColorTextureId == -1)
		{
			return;
		}

		int dhFrameBufferId = GlDhMetaRenderer.INSTANCE.getActiveFramebufferId();
		if (dhFrameBufferId == -1)
		{
			return;
		}

		int mcFrameBufferId = MC_RENDER.getTargetFramebuffer();
		if (mcFrameBufferId == -1)
		{
			return;
		}

		try (GLState state = new GLState())
		{
			GLMC.disableDepthTest();

			// blending isn't needed, we're just directly merging the MC and DH textures
			// Note: this prevents the sun/moon and stars from rendering through transparent LODs,
			// but it also resolves some other issues, so it's likely not an issue
			GLMC.disableBlend();

			GLMC.glActiveTexture(GL13.GL_TEXTURE0);
			GLMC.glBindTexture(GlDhMetaRenderer.INSTANCE.getActiveColorTextureId());
			LWJGL.glUniform1i(this.uCopyTexture, 0);
			this.bindDepthAndChunkMask();



			LWJGL.glFramebufferTexture(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, targetColorTextureId, 0);

			// Copy to MC's texture via MC's framebuffer
			GLMC.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dhFrameBufferId);

			GlScreenQuad.INSTANCE.render();
		}
		// everything's been restored, except at this point the MC framebuffer should now be used instead
		GLMC.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFrameBufferId);

	}

	//endregion



}
