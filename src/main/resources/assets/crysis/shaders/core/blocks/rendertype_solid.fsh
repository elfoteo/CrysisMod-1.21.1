#version 420
#moj_import <fog.glsl>

// Minecraft uniforms
uniform sampler2D Sampler0;
uniform vec4     ColorModulator;
uniform float    FogStart;
uniform float    FogEnd;
uniform vec4     FogColor;
uniform float    EntityData[128];
uniform int      EntityCount;
uniform vec3     CameraPos;

uniform int      u_worldOffsetX;
uniform int      u_worldOffsetZ;
uniform float    u_deltaTime;

// Trail heat buffers
layout(binding = 1, r16f) uniform image3D u_TrailRW;
layout(binding = 2)       uniform sampler3D TrailSampler;

in float  vertexDistance;
in float  blockLight;   // (we will ignore this)
in float  skyLight;     // (ignore this)
in vec3   worldPosition;  // camera-relative
out vec4  fragColor;
flat in ivec3 blockPos;

// Heat color ramp: cold=blue(0,0,0.26), then red→white
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);
    if (t <= 0.0) {
        // Pure blue for zero heat
        return vec3(0.0, 0.0, 0.26);
    }
    if (t < 0.33) {
        float f = t / 0.33;
        return mix(vec3(0.0, 0.0, 0.26), vec3(1.0, 0.0, 0.0), f);
    } else {
        float f = (t - 0.33) / 0.67;
        return mix(vec3(1.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0), f);
    }
}

// Compute heat from nearby entities at a position
float computeEntityHeatAt(vec3 pos) {
    float bestHeat = 0.0;
    for (int i = 0; i < EntityCount; ++i) {
        int base = i * 8;
        vec3 ep = vec3(
        EntityData[base + 0],
        EntityData[base + 1],
        EntityData[base + 2]
        );
        float width  = EntityData[base + 3];
        float height = EntityData[base + 4];

        vec3 diff = pos - ep;
        float dx = diff.x / width;
        float dy = diff.y / height;
        float dz = diff.z / width;
        float falloff = width + height;
        float dist2 = dx*dx + dy*dy + dz*dz;
        float t = clamp(1.0 - dist2 * falloff, 0.0, 1.0);
        bestHeat = max(bestHeat, t);
    }
    return bestHeat;
}

void main() {
    if (!gl_FrontFacing) {
        fragColor = vec4(1.0, 0.0, 1.0, 1.0); // magenta for backfaces
        return;
    }

    // 1) Compute absolute world position
    vec3 absPos = worldPosition + CameraPos;

    // 2) Compute block position and center directly from absPos
    ivec3 blockPos = ivec3(floor(absPos));
    vec3 blockCenterAbs = vec3(blockPos) + 0.5;          // Absolute block center
    vec3 blockCenterRel = blockCenterAbs - CameraPos;    // Camera-relative block center

    // 4) Compute entity heat at block center (only entity heat)
    float entityHeat = computeEntityHeatAt(blockCenterRel);
    float combined = entityHeat;  // Ignoring blockLight/skyLight

    // 5) Compute texture coordinates
    ivec3 texCoord = ivec3(
    blockPos.x - u_worldOffsetX,
    blockPos.y,
    blockPos.z - u_worldOffsetZ
    );

    // 6) Texture dimensions
    const int texWidth  = 512;
    const int texHeight = 380;
    const int texDepth  = 512;

    // 7) If outside bounds, use entityHeat directly
    if (texCoord.x < 0 || texCoord.x >= texWidth ||
    texCoord.y < 0 || texCoord.y >= texHeight ||
    texCoord.z < 0 || texCoord.z >= texDepth) {
        vec3 fallbackHeat = heatColor(combined);
        fragColor = linear_fog(vec4(fallbackHeat, 1.0),
        vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // 8) Read old heat from 3D texture
    float oldHeat = texelFetch(TrailSampler, texCoord, 0).r;

    // 9) Apply decay
    float decayRate = 0.2;
    float fadedHeat = oldHeat * exp(-decayRate * u_deltaTime);
    if (fadedHeat < 0.001) fadedHeat = 0.0;

    // 10) Compute new heat
    float newHeat = max(fadedHeat, combined);

    // 11) Store back to 3D texture
    imageStore(u_TrailRW, texCoord, vec4(newHeat, 0.0, 0.0, 0.0));

    // 12) Render with fog
    vec3 finalRGB = heatColor(newHeat);
    fragColor = linear_fog(vec4(finalRGB, 1.0),
    vertexDistance, FogStart, FogEnd, FogColor);
}