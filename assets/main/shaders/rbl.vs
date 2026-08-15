attribute vec3 a_position;
attribute vec3 a_normal;    // GLB Normals
attribute vec2 a_uv0;       // BSP UVs
attribute vec2 a_texCoord0; // GLB UVs
attribute vec2 a_uv1;       // BSP Lightmap UVs

uniform mat4 u_mvp;
uniform mat4 u_model;
uniform mat3 u_normalMatrix; // Transforms normal to world space

varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec3 v_worldPos;
varying vec3 v_normal;

void main() {
    gl_Position = u_mvp * vec4(a_position, 1.0);
    v_worldPos  = (u_model * vec4(a_position, 1.0)).xyz;
    v_normal    = normalize(u_normalMatrix * a_normal);
    v_uv0       = a_uv0 + a_texCoord0;
    v_uv1       = a_uv1;
}