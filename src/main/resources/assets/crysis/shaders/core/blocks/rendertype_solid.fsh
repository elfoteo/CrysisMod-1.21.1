#version 420
#moj_import <fog.glsl>

// ──────────────────────────────────────────────────────────────────────────────
// Uniforms provided by Minecraft / Java mixin
uniform sampler2D Sampler0;
uniform vec4     ColorModulator;
uniform float    FogStart;
uniform float    FogEnd;
uniform vec4     FogColor;
uniform float    EntityData[128];
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
// Quantized infrared-style heat color ramp with 16 discrete levels
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);

    if (t < 0.33) {
        // Cold to warm (Blue to Red) - levels 0-5
        return mix(vec3(0.0, 0.0, 0.23), vec3(1.0, 0.0, 0.0), t / 0.33);
    } else {
        // Red to hot white - levels 6-15
        return mix(vec3(1.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0), (t - 0.33) / 0.67);
    }
}

float computeEntityHeatAt(vec3 pos) {
    float bestHeat = 0.0;
    for (int i = 0; i < EntityCount; i++) {
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
        float dist2 = dx * dx + dy * dy + dz * dz;
        float t = clamp(1.0 - dist2 * falloff, 0.0, 1.0);

        bestHeat = max(bestHeat, t);
    }
    return bestHeat;
}

void main() {
    // ─── 1) Compute base heat ───────────────────────────────────────────────
    vec3 absPos     = worldPosition + CameraPos;
    vec3 snappedAbs = floor(absPos);  // No longer multiply by 16, just floor to block coords
    vec3 snappedRel = snappedAbs - CameraPos;

    float lightHeat  = clamp(max((blockLight - 0.3) * 0.3, 0), 0.0, 1.0);
    float entityHeat = computeEntityHeatAt(snappedRel);
    float combined   = max(lightHeat, entityHeat);

    // ─── 2) Block position with +64 offset to avoid negatives ───────────────────
    ivec3 blockPos = ivec3(
    floor(absPos.x + 64.0),  // Add 64 to avoid negative coordinates
    floor(absPos.y + 64.0),  // Add 64 to avoid negative coordinates
    floor(absPos.z + 64.0)   // Add 64 to avoid negative coordinates
    );

    // ─── 3) Subtract Java‐side world offset ──────────────────────────────────
    blockPos.x -= u_worldOffsetX;
    blockPos.y -= u_worldOffsetY;
    blockPos.z -= u_worldOffsetZ;

    // Skip rendering if out of bounds of our 512×512×384 texture
    if (blockPos.x < 0 || blockPos.x >= 512 ||
    blockPos.y < 0 || blockPos.y >= 384 ||
    blockPos.z < 0 || blockPos.z >= 512) {
        vec3 heat = heatColor(combined);
        fragColor = linear_fog(vec4(heat, 1.0), vertexDistance, FogStart, FogEnd, FogColor);
        return;
    }

    // ─── 4) GPU Trail Heat Fade + Accumulation in 3D ────────────────────────
    // Read the single red channel containing the heat value (0.0-1.0) from 3D texture
    float oldHeat = texelFetch(TrailSampler, blockPos, 0).r;

    // Exponential decay - decays faster when hot, slower when cool
    float decayRate = 0.2; // Controls how fast the exponential decay is
    float fadedHeat = oldHeat * exp(-decayRate * u_deltaTime);

    // Apply a minimum threshold to ensure it eventually reaches zero
    // This prevents floating point precision issues from keeping tiny values alive
    const float minThreshold = 0.001;
    if (fadedHeat < minThreshold) {
        fadedHeat = 0.0;
    }

    float newHeat = max(fadedHeat, combined); // Accumulate heat

    // Store only the heat value in the red channel of the 3D texture
    imageStore(u_TrailRW, blockPos, vec4(newHeat, 0.0, 0.0, 0.0));

    // ─── 5) Final shaded output with fog ─────────────────────────────────────
    // Convert the heat value back to color for rendering
    vec3 heatRGB = heatColor(newHeat);
    fragColor = linear_fog(
    vec4(heatRGB, 1.0),
    vertexDistance,
    FogStart, FogEnd, FogColor
    );
}