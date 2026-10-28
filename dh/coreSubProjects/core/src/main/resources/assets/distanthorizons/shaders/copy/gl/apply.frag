#version 330 core

in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D uCopyTexture;
uniform sampler2D uDepthTexture;

// inverted model view matrix and projection matrix
uniform mat4 uDhInvMvmProj;
uniform bool uDepthIsZeroToPositiveOne;
uniform bool uIsReverseZDepth;

// LODs closer than this in chunks MC drew aren't copied, 0 copies everything (see GlDhCopyShader)
uniform float uKeepMcDistance;
// 1 for chunks MC drew vanilla terrain for, centered on the camera's chunk (see VanillaChunkMask)
uniform sampler2D uVanillaChunkMask;
uniform int uVanillaChunkMaskRadius;
// the camera's position inside its chunk (only x and z are used)
uniform vec3 uCameraPosInChunk;


/**
 * this method is shared across several shaders,
 * if updated, make sure to update the other versions as well.
 */
vec3 calcViewPosition(float fragmentDepth, mat4 invMvmProj)
{
    // normalized device coordinates
    vec4 ndc = vec4(texCoord.xy, fragmentDepth, 1.0);
    if (uDepthIsZeroToPositiveOne)
    {
        // Z already in [0,1], don't remap
        ndc.xy = ndc.xy * 2.0 - 1.0;
    }
    else
    {
        // UV [0,1] -> NDC [-1,+1]
        ndc.xyz = ndc.xyz * 2.0 - 1.0;
    }

    vec4 eyeCoord = invMvmProj * ndc;
    return eyeCoord.xyz / eyeCoord.w;
}

// DH copy frag
void main()
{
    fragColor = texture(uCopyTexture, texCoord);

    if (uKeepMcDistance <= 0.0
        || uVanillaChunkMaskRadius <= 0)
    {
        return;
    }

    float fragmentDepth = texture(uDepthTexture, texCoord).r;
    bool drawnTo = uIsReverseZDepth ? (fragmentDepth != 0.0) : (fragmentDepth != 1.0);
    if (!drawnTo)
    {
        return;
    }

    // MC draws its own terrain over these LODs and the vanilla fade then blends it into the LODs,
    // see-through LODs (IE water) left under MC's own would be seen twice in the fade
    vec3 lodPos = calcViewPosition(fragmentDepth, uDhInvMvmProj);
    if (length(lodPos) < uKeepMcDistance)
    {
        ivec2 chunkOffset = ivec2(floor((lodPos.xz + uCameraPosInChunk.xz) / 16.0));
        if (abs(chunkOffset.x) <= uVanillaChunkMaskRadius
            && abs(chunkOffset.y) <= uVanillaChunkMaskRadius
            && texelFetch(uVanillaChunkMask, chunkOffset + ivec2(uVanillaChunkMaskRadius), 0).r > 0.5)
        {
            discard;
        }
    }
}
