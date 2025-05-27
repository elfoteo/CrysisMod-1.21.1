#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;       // block atlas (unused here)
uniform vec4     ColorModulator;  // (unused if you want pure heat)
uniform float    FogStart;
uniform float    FogEnd;
uniform vec4     FogColor;

// 16 × vec4(x, y, z, radius) FOR ENTITIES, all in camera‐relative space
uniform float EntityData[64];
uniform int   EntityCount;

// NEW: camera’s world‐space position (same uniform as in VSH)
uniform vec3 CameraPos;

in float vertexDistance;   // world‐space distance for fog
in float blockLight;       // ∈ [0..1]
in float skyLight;         // ∈ [0..1]
in vec2  texCoord0;        // unused here
in vec3  worldPosition;    // camera‐relative world position

out vec4 fragColor;

// “cold→hot” color ramp:
vec3 heatColor(float t) {
    if (t < 0.1)
    return mix(vec3(0.35, 0.05, 0.10),
    vec3(0.25, 0.05, 0.12),
    t / 0.1);
    else if (t < 0.3)
    return mix(vec3(0.25, 0.05, 0.12),
    vec3(0.07, 0.04, 0.18),
    (t - 0.1) / 0.2);
    else if (t < 0.5)
    return mix(vec3(0.07, 0.04, 0.18),
    vec3(0.0,  0.0,  0.23),
    (t - 0.3) / 0.2);
    else
    return vec3(0.0, 0.0, 0.23);
}

// Compute “entity heat” at a given position (pos)
// Linear fade from d = radius → d = 2*radius
float computeEntityHeatAt(vec3 pos) {
    float bestHeat = 0.0;
    for (int i = 0; i < EntityCount; i++) {
        int base   = i * 4;
        vec3 ep    = vec3(
        EntityData[base + 0],
        EntityData[base + 1],
        EntityData[base + 2]
        );
        float radius = EntityData[base + 3];
        float d      = distance(pos, ep);

        // NEW: falloff zone = radius (instead of a fixed 2.0)
        float falloff = radius;
        // Inside d ≤ radius        → t = 1.0
        // At d ≥ radius + falloff  → t = 0.0
        float t = clamp((radius + falloff - d) / falloff, 0.0, 1.0);
        bestHeat = max(bestHeat, t);
    }
    return bestHeat;
}


void main() {
    //
    // STEP A: Recover *absolute* world‐space position of this fragment
    //         worldPosition = (blockWorldPos − cameraWorldPos), so:
    vec3 absPos = worldPosition + CameraPos;
    //    Now absPos is the true (x, y, z) of this fragment in world coordinates.

    //
    // STEP B: Quantize (snap) absPos to a 1/16‐block grid that’s *anchored* at world origin.
    //   Multiply by 16, floor, then divide by 16. This forces a stepped grid of size 1/16:
    vec3 snappedAbs = floor(absPos * 16.0) / 16.0;

    //
    // STEP C: Convert that snapped absolute position back into camera‐relative space,
    //         so we can compare “snapped” vs. “entityData” (which is in camera‐relative).
    vec3 snappedRel = snappedAbs - CameraPos;

    //
    // STEP D: Compute block‐light + sky‐light heat (unchanged):
    float lightHeat = clamp(blockLight * 0.8 + skyLight * 0.2, 0.0, 1.0);

    //
    // STEP E: Compute “entity heat” at the SNAPPED, camera‐relative position:
    float entityHeat = computeEntityHeatAt(snappedRel);

    //
    // STEP F: Combine them (choose whichever is “hotter”)
    float combined = max(lightHeat, entityHeat);

    //
    // STEP G: Invert so that combined=1→red, 0→blue, then map through heatColor()
    float inv  = 1.0 - combined;
    vec3  heat = heatColor(inv);

    //
    // STEP H: Paint the entire fragment this single solid heat‐color. Alpha=1.
    vec4 color = vec4(heat, 1.0);

    //
    // STEP I: Apply vanilla linear fog and output:
    fragColor = linear_fog(color,
    vertexDistance,
    FogStart,
    FogEnd,
    FogColor);
}
