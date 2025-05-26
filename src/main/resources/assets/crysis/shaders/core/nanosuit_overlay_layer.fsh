#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 ModeModulator;
uniform float Time;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 scrollUV = texCoord0 + vec2(Time * 0.05, Time * 0.05); // scroll horizontally over time
    vec4 src = texture(Sampler0, scrollUV) * ColorModulator * ModeModulator;

    if (src.a < 0.1) {
        discard;
    }

    fragColor = src;
}
