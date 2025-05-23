#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out float distanceFromCamera;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    distanceFromCamera = length(viewPos.xyz); // Distance in view/camera space

    gl_Position = ProjMat * viewPos;
    texCoord0 = UV0;
}
