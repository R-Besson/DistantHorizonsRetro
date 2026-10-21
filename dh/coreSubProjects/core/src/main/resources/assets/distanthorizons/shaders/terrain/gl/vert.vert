#version 330 core

in uvec4 vPosition;
in vec4 color;
// x: iris material id, y: face normal index, zw: block texture tile id (little endian)
in uvec4 irisData;

out vec4 vPos;
out vec4 vertexColor;
out vec3 vertexWorldPos;
out float vertexYPos;
// block-grid position used to generate texture UVs, fract() of this repeats per block
out vec3 vBlockPos;
flat out uint vNormalIndex;
flat out uint vTextureTileId;

uniform bool uIsWhiteWorld;

uniform mat4 uCombinedMatrix;
uniform vec3 uModelOffset;
uniform float uWorldYOffset;

uniform sampler2D uLightMap;
uniform float uMircoOffset;

uniform float uEarthRadius;

uniform float uFrameMod8;
uniform float uViewWidth;
uniform float uViewHeight;


vec2 jitterOffsets[8] = vec2[8](
    vec2( 0.125, -0.375),
    vec2(-0.125,  0.375),
    vec2( 0.625,  0.125),
    vec2( 0.375, -0.625),
    vec2(-0.625,  0.625),
    vec2(-0.875, -0.125),
    vec2( 0.375, -0.875),
    vec2( 0.875,  0.875)
);

vec2 TAAJitter(vec2 coord, float w)
{
    vec2 offset = jitterOffsets[int(uFrameMod8)] * (w / vec2(uViewWidth, uViewHeight));
    return coord + offset;
}

/** 
 * Vertex Shader
 * 
 * author: James Seibel
 * author: TomTheFurry
 * author: stduhpf
 * updated: coolGi
 *
 * version: 2025-12-22
 */
void main()
{
    vPos = vPosition; // This is so it can be passed to the fragment shader

    vNormalIndex = irisData.y;
    vTextureTileId = irisData.z | (irisData.w << 8u);

    uint meta = vPosition.a;

    uint mirco = (meta & 0x3F00u) >> 8u; // mirco offset which is a xyz 2bit value
    // 0b00 = no offset
    // 0b01 = positive offset
    // 0b11 = negative offset
    // format is: 0b00zzyyxx

    // LOD shape, moves the vertex in from the edge of its block (see RenderDataPointUtil.SHAPE_NONE)
    vec3 shapeOffset = vec3(0.0);
    uint shape = (meta >> 14u) & 3u;
    if (shape != 0u)
    {
        // which edge of the block this vertex is on, -1 = min, 1 = max.
        // the micro offset gives the edges along the face, the normal gives the face's own edge
        vec3 edge = vec3(
            (mirco & 1u) != 0u ? ((mirco & 2u) != 0u ? -1.0 : 1.0) : 0.0,
            (mirco & 4u) != 0u ? ((mirco & 8u) != 0u ? -1.0 : 1.0) : 0.0,
            (mirco & 16u) != 0u ? ((mirco & 32u) != 0u ? -1.0 : 1.0) : 0.0);
        // 0 down, 1 up, 2 north, 3 south, 4 west, 5 east
        if (vNormalIndex == 0u) { edge.y = -1.0; }
        else if (vNormalIndex == 1u) { edge.y = 1.0; }
        else if (vNormalIndex == 2u) { edge.z = -1.0; }
        else if (vNormalIndex == 3u) { edge.z = 1.0; }
        else if (vNormalIndex == 4u) { edge.x = -1.0; }
        else { edge.x = 1.0; }

        if (shape == 1u)
        {
            // thin post (fences), 4/16 wide
            shapeOffset.xz = -edge.xz * (6.0 / 16.0);
        }
        else if (shape == 2u)
        {
            // thick post (walls), 8/16 wide
            shapeOffset.xz = -edge.xz * (4.0 / 16.0);
        }
        else if (edge.y > 0.0)
        {
            // bottom half (slabs), only the top moves down
            shapeOffset.y = -0.5;
        }
    }

    vPos.xyz += shapeOffset;
    vBlockPos = vec3(vPosition.xyz) + shapeOffset;

    vertexWorldPos = vPosition.xyz + uModelOffset + shapeOffset;

    vertexYPos = vPosition.y + uWorldYOffset + shapeOffset.y;
    float mx = (mirco & 1u)!=0u ? uMircoOffset : 0.0;
    mx = (mirco & 2u)!=0u ? -mx : mx;
    // the y offset isn't applied
    float mz = (mirco & 16u)!=0u ? uMircoOffset : 0.0;
    mz = (mirco & 32u)!=0u ? -mz : mz;

    vertexWorldPos.x += mx;
    vertexWorldPos.z += mz;
    
    // apply the earth curvature if needed
    if (uEarthRadius < -1.0f || uEarthRadius > 1.0f)
    {
        // vertex transformation logic - stduhpf
        float localRadius = uEarthRadius + vertexYPos;
        float phi = length(vertexWorldPos.xz) / localRadius;
        vertexWorldPos.y += (cos(phi) - 1.0) * localRadius;
        vertexWorldPos.xz = vertexWorldPos.xz * sin(phi) / phi;
    }
    
    uint lights = meta & 0xFFu;
    float skyLight = (float(lights/16u)+0.5) / 16.0;
    float blockLight = (mod(float(lights), 16.0)+0.5) / 16.0;
    vertexColor = vec4(texture(uLightMap, vec2(skyLight, blockLight)).xyz, 1.0);
    
    if (!uIsWhiteWorld)
    {
        vertexColor *= color;
    }
    
    gl_Position = uCombinedMatrix * vec4(vertexWorldPos, 1.0);

    // -1 if TAA is diabled
    if (uFrameMod8 > 0)
    {
        // jittering the model around is necessary to smooth out TAA properly
        gl_Position.xy = TAAJitter(gl_Position.xy, gl_Position.w);
    }
}
