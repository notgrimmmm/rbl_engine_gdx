attribute vec3 a_position;
attribute vec2 a_uv0;
attribute vec2 a_uv1;

uniform mat4 u_mvp;

varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec3 v_worldPos;

void main() {
    gl_Position = u_mvp * vec4(a_position, 1.0);
    v_worldPos  = a_position;
    v_uv0 = a_uv0;
    v_uv1 = a_uv1;
}