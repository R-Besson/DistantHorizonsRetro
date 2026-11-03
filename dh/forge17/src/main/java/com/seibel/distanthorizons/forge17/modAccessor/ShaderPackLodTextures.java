package com.seibel.distanthorizons.forge17.modAccessor;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.forge17.RenderHelper;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
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
 * geometry or tessellation stages, old GLSL) are left untouched. <br><br>
 *
 * Angelica's vertex setup is also fixed for every pack: the normals of plants and see-through leaves,
 * and the LOD shapes (fence and wall posts, bottom slabs).
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
	/** Angelica's _vert_init (see DHTerrainTransformer), its transformer prints it as "( vPosition . xyz + vec3 ( mx , my , mz ) )" */
	private static final Pattern VERT_INIT_PATTERN = Pattern.compile("void\\s+_vert_init\\s*\\(");
	private static final Pattern VERT_POSITION_PATTERN = Pattern.compile("_vert_position\\s*=\\s*\\(\\s*vPosition\\s*\\.\\s*xyz\\s*\\+\\s*vec3\\s*\\(\\s*mx\\s*,\\s*my\\s*,\\s*mz\\s*\\)\\s*\\)\\s*;");
	/** Complementary's LOD water skips pixels with any vanilla terrain: texture2D(depthtex0, screenPos.xy).r < 1.0, Angelica prints texture2D as texture */
	private static final Pattern VANILLA_DEPTH_CHECK_PATTERN = Pattern.compile("texture(?:2D)?\\s*\\(\\s*depthtex0\\s*,\\s*([^()]*?)\\s*\\)\\s*\\.\\s*r\\s*<\\s*1(?:\\.0*)?f?");
	private static final Pattern TAA_JITTER_PATTERN = Pattern.compile("TAAJitter\\s*\\(");
	private static final Pattern SCREEN_TO_VIEW_PATTERN = Pattern.compile("vec3\\s+ScreenToView\\s*\\(\\s*vec3");
	private static final Pattern VIEW_TO_PLAYER_PATTERN = Pattern.compile("vec3\\s+ViewToPlayer\\s*\\(\\s*vec3");
	private static final Pattern FAR_UNIFORM_PATTERN = Pattern.compile("uniform\\s+float\\s+far\\s*;");

	/**
	 * The LOD shape (fence and wall posts, bottom slabs), the same inset as DH's own vertex shader (see terrain/gl/vert.vert).
	 * It's stored in the 2 bits above the micro offset (see LodQuadBuilder), which Angelica ignores.
	 */
	private static final String SHAPE_OFFSET_FUNCTION = "vec3 dhr_shapeOffset() {\n"
		+ "    uint shape = (vPosition.a >> 14u) & 3u;\n"
		+ "    if (shape == 0u) return vec3(0.0);\n"
		+ "    uint mirco = (vPosition.a >> 8u) & 0x3Fu;\n"
		// which edge of the block this vertex is on, -1 = min, 1 = max.
		// the micro offset gives the edges along the face, the normal gives the face's own edge
		+ "    vec3 edge = vec3(\n"
		+ "        (mirco & 1u) != 0u ? ((mirco & 2u) != 0u ? -1.0 : 1.0) : 0.0,\n"
		+ "        (mirco & 4u) != 0u ? ((mirco & 8u) != 0u ? -1.0 : 1.0) : 0.0,\n"
		+ "        (mirco & 16u) != 0u ? ((mirco & 32u) != 0u ? -1.0 : 1.0) : 0.0);\n"
		+ "    uint face = (irisExtra.y >= 8u) ? (irisExtra.y - 8u) : irisExtra.y;\n"
		+ "    if (face == 0u) edge.y = -1.0;\n"
		+ "    else if (face == 1u) edge.y = 1.0;\n"
		+ "    else if (face == 2u) edge.z = -1.0;\n"
		+ "    else if (face == 3u) edge.z = 1.0;\n"
		+ "    else if (face == 4u) edge.x = -1.0;\n"
		+ "    else edge.x = 1.0;\n"
		// thin post (fences) 4/16 wide, thick post (walls) 8/16 wide, bottom half (slabs) only moves the top down
		+ "    if (shape == 1u) return vec3(-edge.x, 0.0, -edge.z) * (6.0 / 16.0);\n"
		+ "    if (shape == 2u) return vec3(-edge.x, 0.0, -edge.z) * (4.0 / 16.0);\n"
		+ "    return vec3(0.0, edge.y > 0.0 ? -0.5 : 0.0, 0.0);\n"
		+ "}\n";

	/**
	 * Complementary's water (shared with vanilla water) fades out past the "far" uniform where it finds nothing under it,
	 * which is meant for vanilla water at the edge of the render distance.
	 * Under LOD water that check reads the opaque LODs' depth (dhDepthTex1), when that's empty
	 * every LOD water past "far" (a chunk short of the render distance) turned see-through.
	 * LOD water is never at the edge of anything, so the fade is removed there.
	 */
	private static final Pattern WATER_FAR_FADE_PATTERN = Pattern.compile("if\\s*\\(\\s*depthT\\s*==\\s*1\\.0\\s*\\)\\s*color\\s*\\.\\s*a\\s*\\*=\\s*smoothstep\\s*\\(\\s*far\\s*,\\s*far\\s*\\*\\s*0\\.9\\s*,\\s*lViewPos\\s*\\)\\s*;");

	/**
	 * Complementary lights leaves (shared with MC's, see leaves.glsl) through their back with the sun
	 * and gives them a strong highlight, both meant to be hidden by its shadow map where the sun can't reach.
	 * LODs aren't in the shadow map, so their leaves glowed white even behind a mountain.
	 * LOD leaves are lit like the rest of the LODs instead, only on the side facing the sun and without the highlight.
	 */
	private static final Pattern LEAVES_SUBSURFACE_PATTERN = Pattern.compile("subsurfaceMode\\s*=\\s*2\\s*,\\s*isFoliage\\s*=\\s*true\\s*;");
	private static final Pattern LEAVES_HIGHLIGHT_PATTERN = Pattern.compile("highlightMult\\s*=\\s*factor\\s*\\*\\s*4\\.0\\s*\\+\\s*2\\.0\\s*;");

	private static final String MC_PROJECTION_INVERSE_UNIFORM = "dhr_mcProjectionInverse";
	private static final String ATLAS_UNIFORM = "dhr_BlockAtlas";
	private static final String ENABLED_UNIFORM = "dhr_TexturedLods";
	private static final int ILLUMINATED_MATERIAL_ID = EDhApiBlockMaterial.ILLUMINATED.index;

	private static final String VERTEX_CODE = "out vec3 dhr_BlockPos;\n"
		+ "flat out uint dhr_NormalIndex;\n"
		+ "flat out uint dhr_TileId;\n"
		+ "flat out uint dhr_MaterialId;\n"
		+ "flat out vec3 dhr_FlatColor;\n"
		+ "void main() {\n"
		+ "    dhr_packMain();\n"
		+ "    dhr_BlockPos = vec3(vPosition.xyz) + DHR_SHAPE_OFFSET;\n"
		+ "    dhr_NormalIndex = irisExtra.y;\n"
		+ "    dhr_TileId = irisExtra.z | (irisExtra.w << 8u);\n"
		+ "    dhr_MaterialId = irisExtra.x;\n"
		+ "    dhr_FlatColor = iris_color.rgb;\n"
		+ "}\n";

	/** the same 4x4 ordered dither as DH's own LOD shader */
	private static final String BAYER_FUNCTION = "float dhr_bayer4x4(vec2 pos) {\n"
		+ "    int x = int(mod(pos.x, 4.0));\n"
		+ "    int y = int(mod(pos.y, 4.0));\n"
		+ "    float bayer[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);\n"
		+ "    return bayer[y * 4 + x] / 16.0;\n"
		+ "}\n";

	/**
	 * Complementary's vanilla water (and other see-through terrain) fades out by "far" with its alpha (DH_BLENDING),
	 * unlike its solid terrain which is dithered out. Faded out it still writes depth,
	 * so the pack treats it as vanilla water with nothing under it (the solid terrain was dithered out),
	 * drawing neither water's color: the water past "far" was see-through. <br>
	 * It's dithered out instead, with the exact opposite pattern of the LOD water (see VANILLA_SHOWN_FUNCTION),
	 * so the LOD water fills every pixel it leaves.
	 */
	private static final Pattern VANILLA_WATER_FADE_PATTERN = Pattern.compile("fog\\s*=\\s*exp\\s*\\(\\s*-\\s*3\\.0\\s*\\*\\s*fog\\s*\\)\\s*;\\s*color\\s*\\.\\s*a\\s*\\*=\\s*fog\\s*;");
	private static final String VANILLA_WATER_DITHER = "fog = exp(-3.0 * fog); if (fog <= dhr_bayer4x4(gl_FragCoord.xy) + 0.001) discard;";

	/** placed before the pack's main so its code can use them */
	private static final String FRAGMENT_DECLARATIONS = "uniform sampler2D " + ATLAS_UNIFORM + ";\n"
		+ "uniform int " + ENABLED_UNIFORM + ";\n"
		+ "in vec3 dhr_BlockPos;\n"
		+ "flat in uint dhr_NormalIndex;\n"
		+ "flat in uint dhr_TileId;\n"
		+ "flat in uint dhr_MaterialId;\n"
		+ "flat in vec3 dhr_FlatColor;\n"
		+ "bool dhr_hasTile() { return " + ENABLED_UNIFORM + " != 0 && dhr_TileId != 0u; }\n"
		+ BAYER_FUNCTION;

	/**
	 * Placed before the pack's main, after its own functions. <br>
	 * Complementary (with TAA on) dithers vanilla terrain out by its "far" uniform (DH_BLENDING),
	 * fading vanilla water out the same way, and draws LOD water only where there's no vanilla terrain at all.
	 * Angelica makes "far" a chunk shorter than the render distance with DH installed,
	 * so past it the vanilla water was faded out while the LOD water under it was skipped,
	 * leaving a ring of water you could see straight through. <br>
	 * The LOD water is only skipped where the vanilla terrain is still drawn,
	 * with the same fade so the two waters dither into each other. <br>
	 * Angelica swaps gbufferProjection for DH's projection in LOD programs (so ScreenToView reads LOD depths),
	 * MC's own projection is uploaded separately to read the vanilla depth (see bindUniforms).
	 */
	private static final String VANILLA_SHOWN_FUNCTION = "uniform mat4 " + MC_PROJECTION_INVERSE_UNIFORM + ";\n"
		+ "bool dhr_vanillaTerrainShown(vec2 coord) {\n"
		+ "    float dhr_depth = texture(depthtex0, coord).r;\n"
		+ "    if (dhr_depth >= 1.0) return false;\n"
		+ "    vec4 dhr_viewPos = " + MC_PROJECTION_INVERSE_UNIFORM + " * vec4(vec3(coord, dhr_depth) * 2.0 - 1.0, 1.0);\n"
		+ "    vec3 dhr_playerPos = ViewToPlayer(dhr_viewPos.xyz / dhr_viewPos.w);\n"
		+ "    float dhr_distance = max(length(dhr_playerPos.xz), abs(dhr_playerPos.y)) / far;\n"
		+ "    float dhr_shown = exp(-3.0 * pow(dhr_distance, 16.0));\n"
		+ "    return dhr_shown > dhr_bayer4x4(gl_FragCoord.xy) + 0.001;\n"
		+ "}\n";

	/** OUTPUT is replaced with the pack's color output */
	private static final String FRAGMENT_CODE = "vec2 dhr_blockFaceUvAt(vec3 pos) {\n"
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
		// the unwrapped coordinate is only used for its derivatives, the wrapped one jumps at block edges
		// which would pick the blurriest mipmap there and leave lines (see frag.frag)
		+ "vec2 dhr_blockFaceUv() { return dhr_blockFaceUvAt(fract(dhr_BlockPos)); }\n"
		+ "vec2 dhr_blockFaceUvUnwrapped() { return dhr_blockFaceUvAt(dhr_BlockPos); }\n"
		+ "void main() {\n"
		+ "    dhr_packMain();\n"
		+ "    if (dhr_hasTile()) {\n"
		+ "        vec2 atlasSize = vec2(textureSize(" + ATLAS_UNIFORM + ", 0));\n"
		+ "        vec2 tileOrigin = vec2(float(dhr_TileId % 256u), float(dhr_TileId / 256u)) * 16.0;\n"
		+ "        vec2 dhr_uvUnwrapped = dhr_blockFaceUvUnwrapped() * 16.0 / atlasSize;\n"
		+ "        vec4 tile = textureGrad(" + ATLAS_UNIFORM + ", (tileOrigin + dhr_blockFaceUv() * 16.0) / atlasSize, dFdx(dhr_uvUnwrapped), dFdy(dhr_uvUnwrapped));\n"
		// plants are cut out like MC's
		+ "        if (dhr_NormalIndex >= 6u && dhr_NormalIndex < 8u && tile.a < 0.5) discard;\n"
		// See-through leaves cut out their gaps like DH's own shader (see frag.frag), they're marked with 254 alpha.
		// Only that mark counts, leaf-like blocks that aren't leaves (IE cactus) have other see-through texels.
		// Exact texels are checked since mipmaps blur the marks (and neighboring tiles) together.
		// Further away several texels fall in a pixel, like MC's mipmapped leaves the pixel is only cut out
		// when all of it is gap, so leaves fill in with distance
		+ "        if (dhr_NormalIndex >= 8u) {\n"
		+ "            vec2 dhr_texelPos = dhr_blockFaceUv() * 16.0;\n"
		+ "            vec2 dhr_texelDx = dFdx(dhr_blockFaceUvUnwrapped() * 16.0);\n"
		+ "            vec2 dhr_texelDy = dFdy(dhr_blockFaceUvUnwrapped() * 16.0);\n"
		+ "            if (max(length(dhr_texelDx), length(dhr_texelDy)) < 8.0) {\n"
		+ "                float dhr_gaps = 0.0;\n"
		+ "                for (int dhr_i = 0; dhr_i < 4; dhr_i++) {\n"
		+ "                    vec2 dhr_offset = vec2((dhr_i & 1) == 0 ? -0.25 : 0.25, (dhr_i & 2) == 0 ? -0.25 : 0.25);\n"
		+ "                    vec2 dhr_texel = mod(floor(dhr_texelPos + dhr_texelDx * dhr_offset.x + dhr_texelDy * dhr_offset.y), 16.0);\n"
		+ "                    dhr_gaps += (abs(texelFetch(" + ATLAS_UNIFORM + ", ivec2(tileOrigin + dhr_texel), 0).a - 254.0 / 255.0) < 0.002) ? 0.25 : 0.0;\n"
		+ "                }\n"
		+ "                if (dhr_gaps > 0.999) discard;\n"
		+ "            }\n"
		+ "        }\n"
		// 128 gray is the LOD's own color.
		// Brightened colors are scaled down as a whole (keeping their hue) to the pack's own brightest channel,
		// at least 1 since most packs write to 8 bit buffers, clamping per channel would tint bright green leaves yellow
		+ "        vec3 dhr_textured = max(OUTPUT.rgb * (tile.rgb * 2.0), 0.0);\n"
		// Packs make the whole LOD of a light block glow evenly (IE Complementary's emission of 2.5),
		// for MC's light blocks they only make the bright parts of the texture glow (IE glowstone's max(green - 0.3, 0)),
		// otherwise the dark parts glow too and the block washes out.
		// The glow is moved from the dark pixels to the bright ones the same way, relative to the LOD's own color
		+ "        if (dhr_MaterialId == " + ILLUMINATED_MATERIAL_ID + "u) {\n"
		+ "            vec3 dhr_pixel = dhr_FlatColor * tile.rgb * 2.0;\n"
		+ "            float dhr_pixelGlow = max(max(dhr_pixel.r, dhr_pixel.g) - 0.3, 0.0) + 0.05;\n"
		+ "            float dhr_flatGlow = max(max(dhr_FlatColor.r, dhr_FlatColor.g) - 0.3, 0.0) + 0.05;\n"
		+ "            dhr_textured *= dhr_pixelGlow / dhr_flatGlow;\n"
		+ "        }\n"
		+ "        float dhr_limit = max(1.0, max(OUTPUT.r, max(OUTPUT.g, OUTPUT.b)));\n"
		+ "        float dhr_brightest = max(dhr_textured.r, max(dhr_textured.g, dhr_textured.b));\n"
		+ "        dhr_textured *= dhr_limit / max(dhr_brightest, dhr_limit);\n"
		+ "        OUTPUT.rgb = mix(OUTPUT.rgb, dhr_textured, tile.a);\n"
		+ "    }\n"
		+ "}\n";

	private static boolean loggedSkip = false;
	/** set when the pack's LOD water program is patched, see {@link #isVanillaTerrainFadedAtFar()} */
	private static volatile boolean vanillaTerrainFadedAtFar = false;

	//==========//
	// patching //
	//==========//
	//region

	/**
	 * @return true if the last loaded shader pack dithers vanilla terrain out by its "far" uniform
	 * and draws LODs past it (Complementary with TAA on, see VANILLA_SHOWN_FUNCTION)
	 */
	public static boolean isVanillaTerrainFadedAtFar() { return vanillaTerrainFadedAtFar; }

	/** @return Angelica's terrain program with its see-through blocks dithered out by "far", see VANILLA_WATER_FADE_PATTERN */
	public static Map<PatchShaderType, String> patchVanillaTranslucents(Map<PatchShaderType, String> shaders)
	{
		try
		{
			if (shaders == null)
			{
				return null;
			}

			String vertex = shaders.get(PatchShaderType.VERTEX);
			String fragment = shaders.get(PatchShaderType.FRAGMENT);
			if (vertex == null
				|| fragment == null
				|| !VANILLA_WATER_FADE_PATTERN.matcher(fragment).find()
				|| !MAIN_PATTERN.matcher(fragment).find())
			{
				return shaders;
			}

			String patchedFragment = VANILLA_WATER_FADE_PATTERN.matcher(fragment).replaceAll(Matcher.quoteReplacement(VANILLA_WATER_DITHER));
			Matcher mainMatcher = MAIN_PATTERN.matcher(patchedFragment);
			mainMatcher.find();
			patchedFragment = patchedFragment.substring(0, mainMatcher.start()) + BAYER_FUNCTION + patchedFragment.substring(mainMatcher.start());
			// Not test compiled like the LOD program, Angelica patches its terrain programs on worker threads without a GL context.
			// Only one line is swapped for plain GLSL and a self contained function is added

			Map<PatchShaderType, String> patched = new EnumMap<>(PatchShaderType.class);
			patched.putAll(shaders);
			patched.put(PatchShaderType.FRAGMENT, patchedFragment);
			return patched;
		}
		catch (Throwable e)
		{
			LOGGER.warn("Unable to fix the shader pack's vanilla water fade, error: [" + e + "].", e);
			return shaders;
		}
	}

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
		String originalVertex = vertex;
		Matcher normalMatcher = NORMAL_LOOKUP_PATTERN.matcher(vertex);
		if (normalMatcher.find())
		{
			vertex = normalMatcher.replaceAll("(irisExtra.y >= 8u ? irisNormals[int(irisExtra.y - 8u)] : (irisExtra.y >= 6u ? vec3(0.0, 1.0, 0.0) : irisNormals[int(irisExtra.y)]))");
		}

		// LOD shapes move the vertex inside its block, this is needed even if textures can't be added
		boolean shapesPatched = false;
		Matcher vertInitMatcher = VERT_INIT_PATTERN.matcher(vertex);
		if (vertInitMatcher.find()
			&& VERT_POSITION_PATTERN.matcher(vertex).find())
		{
			vertex = vertex.substring(0, vertInitMatcher.start()) + SHAPE_OFFSET_FUNCTION + vertex.substring(vertInitMatcher.start());
			vertex = VERT_POSITION_PATTERN.matcher(vertex).replaceFirst("_vert_position = (vPosition.xyz + vec3(mx, my, mz) + dhr_shapeOffset());");
			shapesPatched = true;
		}

		if (!vertex.equals(originalVertex))
		{
			Map<PatchShaderType, String> vertexFixed = new EnumMap<>(PatchShaderType.class);
			vertexFixed.putAll(shaders);
			vertexFixed.put(PatchShaderType.VERTEX, vertex);
			shaders = vertexFixed;
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
			MAIN_PATTERN.matcher(vertex).replaceFirst("void dhr_packMain()") + "\n"
				// the textures are placed on the shape's faces, not the full block's
				+ VERTEX_CODE.replace("DHR_SHAPE_OFFSET", shapesPatched ? "dhr_shapeOffset()" : "vec3(0.0)"));
		// Complementary adds a fake 4 per block noise to flat LODs, textured faces don't need it
		String packFragment = COMPLEMENTARY_NOISE_PATTERN.matcher(fragment).replaceAll(Matcher.quoteReplacement("(dhr_hasTile() ? 0.5 : Noise3D(noisePos))"));
		packFragment = WATER_FAR_FADE_PATTERN.matcher(packFragment).replaceAll("");
		packFragment = LEAVES_SUBSURFACE_PATTERN.matcher(packFragment).replaceAll("subsurfaceMode = 0, isFoliage = true;");
		packFragment = LEAVES_HIGHLIGHT_PATTERN.matcher(packFragment).replaceAll("highlightMult = 0.0;");
		String fragmentDeclarations = FRAGMENT_DECLARATIONS;
		if (VANILLA_DEPTH_CHECK_PATTERN.matcher(packFragment).find())
		{
			// only Complementary's LOD water program checks for vanilla terrain (see VANILLA_SHOWN_FUNCTION),
			// its DH_BLENDING is on with TAA, which jitters the vertices
			vanillaTerrainFadedAtFar = TAA_JITTER_PATTERN.matcher(vertex).find();
			if (vanillaTerrainFadedAtFar
				&& SCREEN_TO_VIEW_PATTERN.matcher(packFragment).find()
				&& VIEW_TO_PLAYER_PATTERN.matcher(packFragment).find()
				&& FAR_UNIFORM_PATTERN.matcher(packFragment).find())
			{
				packFragment = VANILLA_DEPTH_CHECK_PATTERN.matcher(packFragment).replaceAll("dhr_vanillaTerrainShown($1)");
				fragmentDeclarations += VANILLA_SHOWN_FUNCTION;
			}
		}
		patched.put(PatchShaderType.FRAGMENT,
			MAIN_PATTERN.matcher(packFragment).replaceFirst(Matcher.quoteReplacement(fragmentDeclarations + "void dhr_packMain()"))
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
		uploadMcProjectionInverse(programId);

		int atlasUniform = AngelicaGLStateCompat.glGetUniformLocation(programId, ATLAS_UNIFORM);
		int enabledUniform = AngelicaGLStateCompat.glGetUniformLocation(programId, ENABLED_UNIFORM);
		if (atlasUniform == -1 || enabledUniform == -1)
		{
			// this program wasn't patched
			return;
		}

		int atlasTextureId = DhApiRenderProxy.getDhBlockRatioAtlasTextureGlId;
		boolean enabled = Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& atlasTextureId > 0;
		AngelicaGLStateCompat.glUniform1i(enabledUniform, enabled ? 1 : 0);
		if (!enabled)
		{
			return;
		}

		// the pack's samplers are given the lowest units, so the last one is free
		int unit = GLStateManager.MAX_TEXTURE_UNITS - 1;
		int previousUnit = AngelicaGLStateCompat.getActiveTextureUnit();
		AngelicaGLStateCompat.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		AngelicaGLStateCompat.glBindTexture(GL11.GL_TEXTURE_2D, atlasTextureId);
		AngelicaGLStateCompat.glActiveTexture(GL13.GL_TEXTURE0 + previousUnit);
		AngelicaGLStateCompat.glUniform1i(atlasUniform, unit);
	}

	/** see VANILLA_SHOWN_FUNCTION */
	private static void uploadMcProjectionInverse(int programId)
	{
		int uniform = AngelicaGLStateCompat.glGetUniformLocation(programId, MC_PROJECTION_INVERSE_UNIFORM);
		if (uniform == -1)
		{
			return;
		}

		Matrix4f projectionInverse = RenderHelper.getProjectionMatrix().createJomlMatrix().invert();
		// put manually, JOML's get(FloatBuffer) can segfault (see McObjectConverter), GL expects column major
		MATRIX_BUFFER.clear();
		for (int column = 0; column < 4; column++)
		{
			for (int row = 0; row < 4; row++)
			{
				MATRIX_BUFFER.put(projectionInverse.get(column, row));
			}
		}
		MATRIX_BUFFER.flip();
		GL20.glUniformMatrix4(uniform, false, MATRIX_BUFFER);
	}
	private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

	//endregion
}
