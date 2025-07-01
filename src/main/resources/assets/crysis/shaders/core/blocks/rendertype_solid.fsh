#version 420
#moj_import <fog.glsl>

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

layout(binding = 1, r16) uniform image3D   u_TrailRW;
layout(binding = 2)     uniform sampler3D  TrailSampler;

in float  vertexDistance;
in float  blockLight;
in float  skyLight;
in vec3   worldPosition;
out vec4  fragColor;

// Quantized infrared‑style heat color ramp with 16 discrete levels
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);
    if (t < 0.33) {
        return mix(vec3(0.0, 0.0, 0.23),
        vec3(1.0, 0.0, 0.0),
        t / 0.33);
    } else {
        return mix(vec3(1.0, 0.0, 0.0),
        vec3(1.0, 1.0, 1.0),
        (t - 0.33) / 0.67);
    }
}

// Compute the highest “heat” from all entities at a given relative pos
float computeEntityHeatAt(vec3 pos) {
    float best = 0.0;
    for (int i = 0; i < EntityCount; i++) {
        int b = i * 3;
        vec3 ep = vec3(EntityData[b+0],
        EntityData[b+1],
        EntityData[b+2]);
        vec3 d  = pos - ep;
        vec3 nd = d / 3.5;
        float t = clamp(1.0 - dot(nd, nd) * (2.0 * 3.5), 0.0, 1.0);
        best = max(best, t);
    }
    return best;
}

void main() {
    // reconstruct world+camera pos
    vec3 absPos     = worldPosition + CameraPos;
    vec3 snappedAbs = floor(absPos);
    vec3 snappedRel = snappedAbs - CameraPos;

    // integer block index for trail texture
    ivec3 blockPos = ivec3(
    int(snappedAbs.x) - u_worldOffsetX,
    int(snappedAbs.y) - u_worldOffsetY,
    int(snappedAbs.z) - u_worldOffsetZ
    );

    bool outOfBounds =
    blockPos.x < 0 || blockPos.x >= 512
    || blockPos.y < 0 || blockPos.y >= 384
    || blockPos.z < 0 || blockPos.z >= 512;

    // base heat from light and entities
    float lightHeat  = clamp((blockLight - 0.8) * 0.8, 0.0, 1.0);
    float entityHeat = computeEntityHeatAt(snappedRel);
    float combined   = max(lightHeat, entityHeat);

    float storedHeat = combined;
    if (!outOfBounds) {
        float oldHeat   = texelFetch(TrailSampler, blockPos, 0).r;
        float fadedHeat = oldHeat * exp(-0.2 * u_deltaTime);
        if (fadedHeat < 0.001) fadedHeat = 0.0;

        ivec3 offsets[4] = ivec3[](
        ivec3( 1,  0,  0),
        ivec3(-1,  0,  0),
        ivec3( 0,  1,  0),
        ivec3( 0, -1,  0)
        );
        float neighborSum = 0.0;
        int   neighborCount = 0;
        for (int i = 0; i < 4; i++) {
            ivec3 np = blockPos + offsets[i];
            if (np.x >= 0 && np.x < 512 &&
            np.y >= 0 && np.y < 384 &&
            np.z >= 0 && np.z < 512)
            {
                neighborSum += texelFetch(TrailSampler, np, 0).r;
                neighborCount++;
            }
        }
        float neighborAvg = (neighborCount > 0)
        ? neighborSum / float(neighborCount)
        : 0.0;

        float newHeat = max(fadedHeat, max(combined, neighborAvg));
        imageStore(u_TrailRW, blockPos, vec4(newHeat, 0.0, 0.0, 0.0));
        storedHeat = newHeat;
    }

    // proximity boost (using snappedRel so it doesn’t jitter per‐pixel)
    float distanceToCamera = length(snappedRel);
    float fastFall = exp(-pow(distanceToCamera / 2.5, 4.0));
    float slowTail = exp(-distanceToCamera / 8.0);
    float proximity = mix(fastFall, slowTail, 0.4) * 0.35;

    float rawHeat = clamp(storedHeat + proximity, 0.0, 1.0);

    vec3 heatRGB = heatColor(rawHeat);
    fragColor = linear_fog(vec4(heatRGB, 1.0),
    vertexDistance,
    FogStart, FogEnd, FogColor);
}
