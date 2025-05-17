#version 150

uniform sampler2D Sampler0;

in float distanceFromCamera;

out vec4 fragColor;

vec3 heatColor(float t) {
    if (t < 0.2)
    return mix(vec3(1.0, 1.0, 1.0), vec3(1.0, 0.0, 0.0), t / 0.2); // White → Red
    else if (t < 0.5)
    return mix(vec3(1.0, 0.0, 0.0), vec3(0.47, 0, 0.06), (t - 0.2) / 0.3); // Red → Dark Green
    else if (t < 0.8)
    return mix(vec3(0.47, 0.0, 0.06), vec3(0.0, 0.0, 0.254), (t - 0.5) / 0.3); // Dark Green → Blue
    else
    return vec3(0.0, 0.0, 0.254);
}

void main() {
    float fadeStart = 15.0;
    float fadeEnd   = 30.0;

    float fadeT = clamp((distanceFromCamera - fadeStart) / (fadeEnd - fadeStart), 0.0, 1.0);
    float normalizedHeat = clamp(distanceFromCamera / fadeEnd, 0.0, 1.0);

    vec3 color = heatColor(normalizedHeat);
    float alpha = 1.0 - fadeT;

    if (alpha <= 0.01) discard;

    fragColor = vec4(color, alpha);
}
