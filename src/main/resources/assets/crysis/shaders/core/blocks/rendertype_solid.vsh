#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;      // vanilla lightmap (lightmap texture)

uniform mat4 ModelViewMat;       // view transform
uniform mat4 ProjMat;            // projection transform
uniform vec3 ChunkOffset;        // (chunkOrigin − cameraPosition) in world‐space
uniform int FogShape;

// NEW: camera’s world‐space position, passed from Java each frame
uniform vec3 CameraPos;

out float vertexDistance;        // for fog in fragment
out float cameraDistance;        // (eye‐space distance) in fragment
out float blockLight;            // block‐light ∈ [0..1]
out float skyLight;              // sky‐light    ∈ [0..1]
out vec4  vertexColor;           // vertex tint × brightness
out vec2  texCoord0;             // block atlas UV
out vec3  worldPosition;         // camera‐relative world position of this vertex

void main() {
    // 1) Compute camera‐relative world position
    //    worldPosition = (blockWorldPos − cameraWorldPos)
    vec3 wp = Position + ChunkOffset;
    worldPosition = wp;

    // 2) Transform to eye space, then clip space
    vec4 viewPos = ModelViewMat * vec4(wp, 1.0);
    gl_Position = ProjMat * viewPos;

    // 3) Pass fog distance (based on world position)
    vertexDistance = fog_distance(wp, FogShape);

    // 4) Pass camera distance (eye‐space length) if needed
    cameraDistance = length(viewPos.xyz);

    // 5) Sample the vanilla lightmap (packed block‐light + sky‐light)
    vec4 lm = minecraft_sample_lightmap(Sampler2, UV2);
    blockLight = lm.r;  // block light / 15 → [0..1]
    skyLight   = lm.g;  // sky   light / 15 → [0..1]

    // 6) Compute vertex color = original vertex tint × max(block, sky)
    float rawBright = max(blockLight, skyLight);
    vertexColor = Color * rawBright;

    // 7) Pass through the texture coords
    texCoord0 = UV0;
}
