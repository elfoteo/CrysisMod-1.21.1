#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float u_Heat;

in float cameraDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 heatColor(float t) {
    if (t < 0.2)
    return mix(vec3(1.0,1.0,1.0), vec3(1.0,0.0,0.0), t/0.2);
    else if (t < 0.5)
    return mix(vec3(1.0,0.0,0.0), vec3(0.47,0.0,0.06), (t-0.2)/0.3);
    else if (t < 0.8)
    return mix(vec3(0.47,0.0,0.06), vec3(0.0,0.0,0.254), (t-0.5)/0.3);
    else
    return vec3(0.0,0.0,0.254);
}

float sampleHeat(vec2 coord, float offset) {
    float acc = 0.0;
    float count = 0.0;

    vec2 offsetCoord = coord + vec2(offset, 0.0);
    vec4 tex = texture(Sampler0, offsetCoord);
    if (tex.a > 0.1) {
        float brightness = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
        acc += brightness;
        count += 1.0;
    }

    return (count > 0.0) ? acc/count : 0.0;
}

void main() {
    vec4 src = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (src.a < 0.01) discard;

    const float fadeEnd   = 125.0;

    // 2) compute blended heat (distance + texture)
    float baseHeat = clamp(cameraDistance / fadeEnd, 0.0, 1.0) - u_Heat;
    float surroundingHeat = sampleHeat(texCoord0, 1.0/512.0);
    float blendedHeat = clamp(baseHeat * 0.7 + surroundingHeat * 0.3, 0.0, 1.0);

    // 3) blue‐based fade: starts at t=0.8 (blue threshold), ends at t=1.0
    float blueFade = smoothstep(0.7, 0.9, blendedHeat);
    // blueFade = 0 for t<=0.8, 1 for t>=1.0, smooth in between

    // final alpha: original × distance‐fade × blue‐fade invert
    float a = src.a * (1.0 - blueFade);

    if (a <= 0.01) discard;

    // output thermal color
    vec3 hc = heatColor(blendedHeat);
    fragColor = vec4(hc, a);
}
