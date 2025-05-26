#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float cameraDistance;
out vec2 texCoord0;

void main() {
    // Compute view-space position & distance
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    cameraDistance = length(viewPos.xyz);

    // Standard transform
    gl_Position = ProjMat * viewPos;

    texCoord0 = UV0;
}
