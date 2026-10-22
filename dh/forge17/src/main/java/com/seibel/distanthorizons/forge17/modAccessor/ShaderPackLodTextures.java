package com.seibel.distanthorizons.forge17.modAccessor;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds DH's block textures to the shader pack's LOD program (dh_terrain). <br><br>
 *
 * Shader packs only get the LOD's flat color, so after the pack's own code runs
 * the color it wrote is multiplied by the block texture's ratio tile,
 * the same way DH's own LOD shader does (see terrain/gl/frag.frag). <br>
 * Packs whose LOD program can't be patched (no float output at location 0,
 * geometry or tessellation stages, old GLSL) are left untouched.
 */
public class ShaderPackLodTextures
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	private static final Pattern MAIN_PATTERN = Pattern.compile("void\\s+main\\s*\\(\\s*(void)?\\s*\\)");
	private static final Pattern VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)");
	private static final Pattern NORMAL_LOOKUP_PATTERN = Pattern.compile("irisNormals\\s*\\[\\s*int\\s*\\(\\s*irisExtra\\.y\\s*\\)\\s*\\]");
	/** Complementary's (and its forks') fake LOD texture: Noise3D(noisePos) + 0.5, where 0.5 + 0.5 changes nothing */
	private static final Pattern COMPLEMENTARY_NOISE_PATTERN = Pattern.compile("Noise3D\\s*\\(\\s*noisePos\\s*\\)");
	private static final Pattern OUTPUT_PATTERN = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*0\\s*\\)\\s*out\\s+vec4\\s+(\\w+)\\s*;");

	private static final String ATLAS_UNIFORM = "dhr_BlockAtlas";
	private static final String ENABLED_UNIFORM = "dhr_TexturedLods";

	private static final String VERTEX_CODE = ""
		+ "out vec3 dhr_BlockPos;\n"
		+ "flat out uint dhr_NormalIndex;\n"
		+ "flat out uint dhr_TileId;\n"
		+ "void main() {\n"
		+ "    dhr_packMain();\n"
		+ "    dhr_BlockPos = vec3(vPosition.xyz);\n"
		+ "    dhr_NormalIndex = irisExtra.y;\n"
		+ "    dhr_TileId = irisExtra.z | (irisExtra.w << 8u);\n"
		+ "}\n";

	/** placed before the pack's main so its code can use them */
	private static final String FRAGMENT_DECLARATIONS = ""
		+ "uniform sampler2D " + ATLAS_UNIFORM + ";\n"
		+ "uniform int " + ENABLED_UNIFORM + ";\n"
		+ "in vec3 dhr_BlockPos;\n"
		+ "flat in uint dhr_NormalIndex;\n"
		+ "flat in uint dhr_TileId;\n"
		+ "bool dhr_hasTile() { return " + ENABLED_UNIFORM + " != 0 && dhr_TileId != 0u; }\n";

	/** OUTPUT is replaced with the pack's color output */
	private static final String FRAGMENT_CODE = ""
		+ "vec2 dhr_blockFaceUv() {\n"
		+ "    vec3 pos = fract(dhr_BlockPos);\n"
		// 6 and 7 are the diagonal planes of crossed plants, see-through leaves add 8 (see LodQuadBuilder)
		+ "    uint face = (dhr_NormalIndex >= 8u) ? (dhr_NormalIndex - 8u) : dhr_NormalIndex;\n"
		+ "    if (face >= 6u) return vec2(pos.x, 1.0 - pos.y);\n"
		+ "    if (face == 0u) return vec2(pos.x, 1.0 - pos.z);\n"
		+ "    if (face == 1u) return vec2(pos.x, pos.z);\n"
		+ "    if (face == 2u) return vec2(1.0 - pos.x, 1.0 - pos.y);\n"
		+ "    if (face == 3u) return vec2(pos.x, 1.0 - pos.y);\n"
		+ "    if (face == 4u) return vec2(pos.z, 1.0 - pos.y);\n"
		+ "    return vec2(1.0 - pos.z, 1.0 - pos.y);\n"
		+ "}\n"
		// 4x4 ordered dither, 0 to 15/16
		+ "float dhr_bayer(vec2 fragCoord) {\n"
		+ "    ivec2 p = ivec2(mod(fragCoord, 4.0));\n"
		+ "    int b = ((p.x ^ p.y) & 1) * 8 + (p.y & 1) * 4 + (((p.x ^ p.y) >> 1) & 1) * 2 + ((p.y >> 1) & 1);\n"
		+ "    return float(b) / 16.0;\n"
		+ "}\n"
		+ "void main() {\n"
		+ "    dhr_packMain();\n"
		+ "    if (dhr_hasTile()) {\n"
		+ "        vec2 atlasSize = vec2(textureSize(" + ATLAS_UNIFORM + ", 0));\n"
		+ "        vec2 tileOrigin = vec2(float(dhr_TileId % 256u), float(dhr_TileId / 256u)) * 16.0;\n"
		+ "        vec4 tile = texture(" + ATLAS_UNIFORM + ", (tileOrigin + dhr_blockFaceUv() * 16.0) / atlasSize);\n"
		// plants are cut out like MC's
		+ "        if (dhr_NormalIndex >= 6u && dhr_NormalIndex < 8u && tile.a < 0.5) discard;\n"
		// see-through leaves also cut out their gaps (254 alpha), checked on exact texels since mipmaps blur the marks.
		// Further away the share of gaps in a pixel is dithered out, fading to solid leaves in the distance
		+ "        if (dhr_NormalIndex >= 8u) {\n"
		+ "            vec2 dhr_texelPos = dhr_blockFaceUv() * 16.0;\n"
		+ "            vec2 dhr_texelDx = dFdx(dhr_texelPos);\n"
		+ "            vec2 dhr_texelDy = dFdy(dhr_texelPos);\n"
		+ "            float dhr_fade = 1.0 - smoothstep(4.0, 8.0, max(length(dhr_texelDx), length(dhr_texelDy)));\n"
		+ "            if (dhr_fade > 0.0) {\n"
		+ "                float dhr_gaps = 0.0;\n"
		+ "                for (int i = 0; i < 4; i++) {\n"
		+ "                    vec2 dhr_offset = vec2((i & 1) == 0 ? -0.25 : 0.25, (i & 2) == 0 ? -0.25 : 0.25);\n"
		+ "                    vec2 dhr_texel = mod(floor(dhr_texelPos + dhr_texelDx * dhr_offset.x + dhr_texelDy * dhr_offset.y), 16.0);\n"
		+ "                    dhr_gaps += (texelFetch(" + ATLAS_UNIFORM + ", ivec2(tileOrigin + dhr_texel), 0).a < 0.998) ? 0.25 : 0.0;\n"
		+ "                }\n"
		+ "                if (dhr_gaps * dhr_fade > dhr_bayer(gl_FragCoord.xy) + 0.001) discard;\n"
		+ "            }\n"
		+ "        }\n"
		// 128 gray is the LOD's own color.
		// Brightened colors are scaled down as a whole (keeping their hue) to the pack's own brightest channel,
		// at least 1 since most packs write to 8 bit buffers, clamping per channel would tint bright green leaves yellow
		+ "        vec3 dhr_textured = max(OUTPUT.rgb * (tile.rgb * 2.0), 0.0);\n"
		+ "        float dhr_limit = max(1.0, max(OUTPUT.r, max(OUTPUT.g, OUTPUT.b)));\n"
		+ "        float dhr_brightest = max(dhr_textured.r, max(dhr_textured.g, dhr_textured.b));\n"
		+ "        dhr_textured *= dhr_limit / max(dhr_brightest, dhr_limit);\n"
		// leaf gaps that aren't cut out keep their dark color like MC's leaves
		+ "        OUTPUT.rgb = mix(OUTPUT.rgb, dhr_textured, tile.a);\n"
		+ "    }\n"
		+ "}\n";

	private static boolean loggedSkip = false;



	//==========//
	// patching //
	//==========//
	//region

	/** @return the patched shaders, or the original ones if the pack's program can't be patched */
	public static Map<PatchShaderType, String> patch(Map<PatchShaderType, String> shaders)
	{
		try
		{
			return tryPatch(shaders);
		}
		catch (Throwable e)
		{
			// a broken patch must never break the shader pack
			LOGGER.warn("Unable to add textured LODs to the shader pack, error: [" + e + "].", e);
			return shaders;
		}
	}

	private static Map<PatchShaderType, String> tryPatch(Map<PatchShaderType, String> shaders)
	{
		if (shaders == null)
		{
			return null;
		}

		String vertex = shaders.get(PatchShaderType.VERTEX);
		String fragment = shaders.get(PatchShaderType.FRAGMENT);
		if (vertex == null || fragment == null)
		{
			return shaders;
		}

		// Angelica looks the normal up in a 6 entry array, plants use 6 and 7 for their diagonal planes (given an up normal like MC's plants)
		// and see-through leaves add 8 to their face's index. This is needed even if textures can't be added.
		Matcher normalMatcher = NORMAL_LOOKUP_PATTERN.matcher(vertex);
		if (normalMatcher.find())
		{
			vertex = normalMatcher.replaceAll("(irisExtra.y >= 8u ? irisNormals[int(irisExtra.y - 8u)] : (irisExtra.y >= 6u ? vec3(0.0, 1.0, 0.0) : irisNormals[int(irisExtra.y)]))");
			Map<PatchShaderType, String> normalFixed = new EnumMap<>(PatchShaderType.class);
			normalFixed.putAll(shaders);
			normalFixed.put(PatchShaderType.VERTEX, vertex);
			shaders = normalFixed;
		}

		String skipReason = null;
		if (shaders.get(PatchShaderType.GEOMETRY) != null
			|| shaders.get(PatchShaderType.TESS_CONTROL) != null
			|| shaders.get(PatchShaderType.TESS_EVAL) != null)
		{
			skipReason = "it has geometry or tessellation stages";
		}

		Matcher versionMatcher = VERSION_PATTERN.matcher(fragment);
		if (skipReason == null
			&& (!versionMatcher.find() || Integer.parseInt(versionMatcher.group(1)) < 130))
		{
			skipReason = "its GLSL version is too old";
		}

		Matcher outputMatcher = OUTPUT_PATTERN.matcher(fragment);
		if (skipReason == null && !outputMatcher.find())
		{
			skipReason = "it has no color output at location 0";
		}

		if (skipReason == null
			&& (!MAIN_PATTERN.matcher(vertex).find() || !MAIN_PATTERN.matcher(fragment).find()))
		{
			skipReason = "its main function wasn't found";
		}

		if (skipReason != null)
		{
			if (!loggedSkip)
			{
				loggedSkip = true;
				LOGGER.info("Textured LODs aren't available with this shader pack, " + skipReason + ".");
			}
			return shaders;
		}

		// the pack's main is renamed and called from ours, so its code is kept as is
		Map<PatchShaderType, String> patched = new EnumMap<>(PatchShaderType.class);
		patched.putAll(shaders);
		patched.put(PatchShaderType.VERTEX,
			MAIN_PATTERN.matcher(vertex).replaceFirst("void dhr_packMain()") + "\n" + VERTEX_CODE);
		// Complementary adds a fake 4 per block noise to flat LODs, textured faces don't need it
		String packFragment = COMPLEMENTARY_NOISE_PATTERN.matcher(fragment).replaceAll(Matcher.quoteReplacement("(dhr_hasTile() ? 0.5 : Noise3D(noisePos))"));
		patched.put(PatchShaderType.FRAGMENT,
			MAIN_PATTERN.matcher(packFragment).replaceFirst(Matcher.quoteReplacement(FRAGMENT_DECLARATIONS + "void dhr_packMain()"))
				+ "\n" + FRAGMENT_CODE.replace("OUTPUT", outputMatcher.group(1)));

		// Angelica throws (disabling the whole pack) if its program doesn't compile,
		// so the patched program is tested first
		String compileError = compileAndLink(patched.get(PatchShaderType.VERTEX), patched.get(PatchShaderType.FRAGMENT));
		if (compileError != null)
		{
			LOGGER.warn("Textured LODs aren't available with this shader pack, the patched LOD program doesn't compile: [" + compileError + "].");
			return shaders;
		}
		return patched;
	}

	/** @return null if the program links, otherwise the error */
	private static String compileAndLink(String vertex, String fragment)
	{
		int program = GL20.glCreateProgram();
		int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
		int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
		try
		{
			GL20.glShaderSource(vertexShader, vertex);
			GL20.glCompileShader(vertexShader);
			if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE)
			{
				return "vertex: " + GL20.glGetShaderInfoLog(vertexShader, 4096);
			}

			GL20.glShaderSource(fragmentShader, fragment);
			GL20.glCompileShader(fragmentShader);
			if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE)
			{
				return "fragment: " + GL20.glGetShaderInfoLog(fragmentShader, 4096);
			}

			// same attribute locations as Angelica's IrisLodRenderProgram
			GL20.glBindAttribLocation(program, 0, "vPosition");
			GL20.glBindAttribLocation(program, 1, "iris_color");
			GL20.glBindAttribLocation(program, 2, "irisExtra");
			GL20.glAttachShader(program, vertexShader);
			GL20.glAttachShader(program, fragmentShader);
			GL20.glLinkProgram(program);
			if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE)
			{
				return "link: " + GL20.glGetProgramInfoLog(program, 4096);
			}
			return null;
		}
		finally
		{
			GL20.glDeleteShader(vertexShader);
			GL20.glDeleteShader(fragmentShader);
			GL20.glDeleteProgram(program);
		}
	}

	//endregion



	//==========//
	// uniforms //
	//==========//
	//region

	/** Binds the block atlas for the shader pack program, which must be in use */
	public static void bindAtlas(int programId)
	{
		if (bindFailed)
		{
			return;
		}

		try
		{
			tryBindAtlas(programId);
		}
		catch (Throwable e)
		{
			bindFailed = true;
			LOGGER.warn("Unable to bind the LOD textures for the shader pack, textured LODs are disabled. Error: [" + e + "].", e);
		}
	}
	private static boolean bindFailed = false;

	private static void tryBindAtlas(int programId)
	{
		int atlasUniform = GLStateManager.glGetUniformLocation(programId, ATLAS_UNIFORM);
		int enabledUniform = GLStateManager.glGetUniformLocation(programId, ENABLED_UNIFORM);
		if (atlasUniform == -1 || enabledUniform == -1)
		{
			// this program wasn't patched
			return;
		}

		int atlasTextureId = DhApiRenderProxy.getDhBlockRatioAtlasTextureGlId;
		boolean enabled = Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& atlasTextureId > 0;
		GLStateManager.glUniform1i(enabledUniform, enabled ? 1 : 0);
		if (!enabled)
		{
			return;
		}

		// the pack's samplers are given the lowest units, so the last one is free
		int unit = GLStateManager.MAX_TEXTURE_UNITS - 1;
		int previousUnit = GLStateManager.getActiveTextureUnit();
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, atlasTextureId);
		GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + previousUnit);
		GLStateManager.glUniform1i(atlasUniform, unit);
	}

	//endregion

}
