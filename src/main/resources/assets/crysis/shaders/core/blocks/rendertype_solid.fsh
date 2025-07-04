#version 420
#moj_import <fog.glsl>

// Uniforms provided by Minecraft / Java mixin
uniform sampler2D Sampler0;
uniform vec4     ColorModulator;
uniform float    FogStart;
uniform float    FogEnd;
uniform vec4     FogColor;
uniform float    EntityData[256];
uniform int      EntityCount;
uniform vec3     CameraPos;

uniform int      u_worldOffsetX;
uniform int      u_worldOffsetY;
uniform int      u_worldOffsetZ;
uniform float    u_deltaTime;

in float  vertexDistance;
in float  blockLight;
in float  skyLight;
in vec3   worldPosition;
out vec4  fragColor;

// Quantized heat-style color ramp with 16 discrete levels
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);
    if (t < 0.33) {
        return mix(
        vec3(0.0, 0.0, 0.23),
        vec3(1.0, 0.0, 0.0),
        t / 0.33
        );
    } else {
        return mix(
        vec3(1.0, 0.0, 0.0),
        vec3(1.0, 1.0, 1.0),
        (t - 0.33) / 0.67
        );
    }
}

// Compute the highest "heat" from all entities at a given relative position
float computeEntityHeatAt(vec3 pos) {
    float best = 0.0;
    for (int i = 0; i < EntityCount; i++) {
        int b = i * 3;
        vec3 ep = vec3(
        EntityData[b + 0],
        EntityData[b + 1],
        EntityData[b + 2]
        );
        vec3 d = pos - ep;
        float distSq = dot(d, d);
        float t = exp(-distSq * distSq / 39.06); // ≈ (2.5^4)
        best = max(best, t);
    }
    return best;
}

void main() {
    vec3 absPos     = worldPosition + CameraPos;
    vec3 snappedAbs = floor(absPos);
    vec3 snappedRel = snappedAbs - CameraPos;

    // base heat from block light
    float lightHeat = clamp((blockLight - 0.8) * 0.8, 0.0, 1.0);

    // distance from camera for attenuation
    float distanceToCamera = length(snappedRel);

    // fade factor: no entity heat contribution beyond 50 blocks
    float entityFade = clamp(1.0 - distanceToCamera / 120.0, 0.0, 1.0);

    // compute entity proximity heat and attenuate by distance
    float entityProximity = computeEntityHeatAt(snappedRel) * 0.35 * entityFade;

    // additional proximity-based glow
    float fastFall = exp(-pow(distanceToCamera / 2.5, 4.0));
    float slowTail = exp(-distanceToCamera / 8.0);
    float proximity = mix(fastFall, slowTail, 0.4) * 0.35;

    // combine all heat sources
    float rawHeat = clamp(lightHeat + entityProximity + proximity, 0.0, 1.0);

    // apply color ramp and linear fog
    vec3 heatRGB = heatColor(rawHeat);
    fragColor = linear_fog(
    vec4(heatRGB, 1.0),
    vertexDistance,
    FogStart, FogEnd, FogColor
    );
}
