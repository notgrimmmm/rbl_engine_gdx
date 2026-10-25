attribute vec3 a_position;
attribute vec3 a_normal;    // GLB Normals
attribute vec2 a_uv0;       // BSP UVs
attribute vec2 a_texCoord0; // GLB UVs
attribute vec2 a_uv1;       // BSP Lightmap UVs

// libGDX skinning: each is (boneIndex, weight)
attribute vec2 a_boneWeight0;
attribute vec2 a_boneWeight1;
attribute vec2 a_boneWeight2;
attribute vec2 a_boneWeight3;

#define MAX_BONES 64   // keep in sync with EngineShader.MAX_BONES
uniform mat4  u_bones[MAX_BONES];
uniform float u_skinned;

uniform mat4 u_mvp;
uniform mat4 u_model;
uniform mat3 u_normalMatrix;

varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec3 v_worldPos;
varying vec3 v_normal;

void main() {
    vec4 pos = vec4(a_position, 1.0);
    vec3 nrm = a_normal;

    // Gated by a uniform: meshes without bone attributes (BSP, rigid GLB parts)
    // would otherwise read zeros and collapse to the origin.
    if (u_skinned > 0.5) {
        mat4 skin =
            a_boneWeight0.y * u_bones[int(a_boneWeight0.x)] +
            a_boneWeight1.y * u_bones[int(a_boneWeight1.x)] +
            a_boneWeight2.y * u_bones[int(a_boneWeight2.x)] +
            a_boneWeight3.y * u_bones[int(a_boneWeight3.x)];
        pos = skin * pos;
        nrm = (skin * vec4(nrm, 0.0)).xyz;
    }

    gl_Position = u_mvp * pos;
    v_worldPos  = (u_model * pos).xyz;
    v_normal    = normalize(u_normalMatrix * nrm);
    v_uv0       = a_uv0 + a_texCoord0;
    v_uv1       = a_uv1;
}
