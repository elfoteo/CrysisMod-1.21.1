#version 420
#moj_import <fog.glsl>

// ──────────────────────────────────────────────────────────────────────────────
// Uniforms provided by Minecraft / Java mixin
uniform sampler2D Sampler0;
uniform vec4     ColorModulator;
uniform float    FogStart;
uniform float    FogEnd;
uniform vec4     FogColor;
uniform float    EntityData[192];
uniform int      EntityCount;
uniform vec3     CameraPos;

uniform int      u_worldOffsetX;
uniform int      u_worldOffsetY;
uniform int      u_worldOffsetZ;
uniform float    u_deltaTime;

layout(binding = 1, r16) uniform image3D   u_TrailRW;    // 3D texture for read/write
layout(binding = 2)     uniform sampler3D  TrailSampler; // 3D sampler for reading

in float  vertexDistance;
in float  blockLight;
in float  skyLight;
in vec3   worldPosition;
out vec4  fragColor;

// ──────────────────────────────────────────────────────────────────────────────
// Quantized infrared‐style heat color ramp with 16 discrete levels
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);
    if (t < 0.33) {
        // Cold to warm (Blue → Red)
        return mix(vec3(0.0, 0.0, 0.23),
        vec3(1.0, 0.0, 0.0),
        t / 0.33);
    } else {
        // Red → White
        return mix(vec3(1.0, 0.0, 0.0),
        vec3(1.0, 1.0, 1.0),
        (t - 0.33) / 0.67);
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Compute the highest “heat” from all entities at a given relative pos
float computeEntityHeatAt(vec3 pos) {
    float best = 0.0;
    for (int i = 0; i < EntityCount; i++) {
        int b = i * 3;
        vec3 ep     = vec3(EntityData[b+0],
        EntityData[b+1],
        EntityData[b+2]);

        vec3 d = pos - ep;
        // now divide by the same radius in all axes
        vec3 nd = d / 3;
        // falloff = r + r = 2r
        float t = clamp(1.0 - dot(nd, nd) * 5, 0.0, 1.0);

        best = max(best, t);
    }
    return best;
}

void main() {
    vec3 absPos     = worldPosition + CameraPos;
    vec3 snappedAbs = floor(absPos);
    vec3 snappedRel = snappedAbs - CameraPos;

    float lightHeat  = clamp((blockLight - .8) * 0.8 , 0.0, 1.0);
    float entityHeat = computeEntityHeatAt(snappedRel);
    float combined   = max(lightHeat, entityHeat);

    ivec3 blockPos = ivec3(
    floor(absPos.x + 64.0),
    floor(absPos.y + 64.0),
    floor(absPos.z + 64.0)
    );
    blockPos.x -= u_worldOffsetX;
    blockPos.y -= u_worldOffsetY;
    blockPos.z -= u_worldOffsetZ;

    // Distance to camera for proximity boost
    float distanceToCamera = length(snappedRel);

    // Compute heat color here; we might override the value later
    float finalHeat = combined;

    bool outOfBounds = blockPos.x < 0 || blockPos.x >= 512 ||
    blockPos.y < 0 || blockPos.y >= 384 ||
    blockPos.z < 0 || blockPos.z >= 512;

    if (!outOfBounds) {
        float oldHeat = texelFetch(TrailSampler, blockPos, 0).r;
        float decayRate = 0.2;
        float fadedHeat = oldHeat * exp(-decayRate * u_deltaTime);
        const float minThreshold = 0.001;
        if (fadedHeat < minThreshold) fadedHeat = 0.0;
        float newHeat = max(fadedHeat, combined);
        imageStore(u_TrailRW, blockPos, vec4(newHeat,0.0,0.0,0.0));
        finalHeat = newHeat;
    }

    // Apply a proximity heat boost for visuals (only within 5 blocks)
    float proximityBoost = smoothstep(5.0, 0.0, distanceToCamera) * 0.25;
    float visualHeat = clamp(finalHeat + proximityBoost, 0.0, 1.0);

    vec3 heatRGB = heatColor(visualHeat);
    fragColor = linear_fog(vec4(heatRGB,1.0),
    vertexDistance,
    FogStart, FogEnd, FogColor);
}
