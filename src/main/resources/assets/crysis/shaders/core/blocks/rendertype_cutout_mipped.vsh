#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float cameraDistance;

void main() {
    vec3 worldPos = Position + ChunkOffset;
    vec4 viewPos = ModelViewMat * vec4(worldPos, 1.0); // View-space position

    gl_Position = ProjMat * viewPos;

    vertexDistance = fog_distance(worldPos, FogShape); // For fog
    cameraDistance = length(viewPos.xyz);

    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
