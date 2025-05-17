#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in float cameraDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 heatColor(float t) {
    if (t < 0.2)
    return mix(vec3(1.0), vec3(1.0, 0.0, 0.0), t / 0.2);
    else if (t < 0.5)
    return mix(vec3(1.0, 0.0, 0.0), vec3(0.47, 0.0, 0.06), (t - 0.2) / 0.3);
    else if (t < 0.8)
    return mix(vec3(0.47, 0.0, 0.06), vec3(0.0, 0.0, 0.254), (t - 0.5) / 0.3);
    else
    return vec3(0.0, 0.0, 0.254);
}

void main() {
    vec4 src = texture(Sampler0, texCoord0)
    * vertexColor
    * ColorModulator;

    // Early-out on almost-transparent
    if (src.a < 0.1) {
        discard;
    }

    // Fade parameters
    const float fadeStart = 15.0;
    const float fadeEnd   = 30.0;

    float fadeT        = clamp((cameraDistance - fadeStart) / (fadeEnd - fadeStart), 0.0, 1.0);
    float normalizedH  = clamp(cameraDistance / fadeEnd, 0.0, 1.0);

    vec3 hc    = heatColor(normalizedH);
    float a    = src.a * (1.0 - fadeT);

    if (a <= 0.01) discard;

    fragColor = vec4(hc, a);
}
