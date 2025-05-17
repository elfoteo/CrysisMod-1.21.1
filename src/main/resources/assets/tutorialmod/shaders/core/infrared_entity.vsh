#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float distanceFromCamera;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    distanceFromCamera = length(viewPos.xyz); // Distance in view/camera space

    gl_Position = ProjMat * viewPos;
    texCoord0 = UV0;
}
