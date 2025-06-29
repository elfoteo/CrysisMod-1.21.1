#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float u_Heat;

in float vertexDistance;
in float distanceFromCamera;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec3 heatColor(float t) {
    t = 1.0 - t; // Invert the input
    if (t < 0.2) {
        // White → Red
        return mix(vec3(1.0, 1.0, 1.0), vec3(1.0, 0.0, 0.0), t / 0.2);
    } else if (t < 0.5) {
        // Red → Dark Green
        return mix(vec3(1.0, 0.0, 0.0), vec3(0.47, 0.0, 0.06), (t - 0.2) / 0.3);
    } else if (t < 0.8) {
        // Dark Green → Blue
        return mix(vec3(0.47, 0.0, 0.06), vec3(0.0, 0.0, 0.254), (t - 0.5) / 0.3);
    } else {
        return vec3(0.0, 0.0, 0.254);
    }
}

void main() {
    vec4 src = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (src.a < 0.1) {
        discard;
    }

    // Keep your fog/fade logic if you want:
    float fadeStart = 40.0;
    float fadeEnd   = 55.0;
    float fadeT = clamp((distanceFromCamera - fadeStart) / (fadeEnd - fadeStart), 0.0, 1.0);
    float normalizedHeat = (1 - clamp(distanceFromCamera / fadeEnd, 0.0, 1.0))*u_Heat*1.5;

    vec3 color = heatColor(normalizedHeat);
    float alpha = src.a * (1.0 - fadeT) * 0.8; // Always a bit transparent

    if (alpha <= 0.01) discard;
    fragColor = vec4(color, alpha);
}
