#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_uv;

uniform vec4  u_shadowColor; // Base shadow color (e.g., r=0, g=0, b=0, a=0.6)
uniform float u_innerRadius; // Core solid radius (0.0 to 0.8)
uniform float u_outerRadius; // Soft edge radius (0.8 to 1.0)
uniform vec2  u_aspectScale; // X/Z scale ratio for directional light stretching

void main() {
    // Center UV coordinates to [-1.0, 1.0]
    vec2 st = (v_uv - vec2(0.5)) * 2.0;
    
    // Apply aspect scaling for directional stretching
    st *= u_aspectScale;

    // Calculate distance from center
    float dist = length(st);

    // If pixel is outside unit circle, discard/alpha out early
    if (dist > 1.0) {
        discard;
    }

    // Smooth Hermite interpolation from inner radius to outer edge
    float alpha = 1.0 - smoothstep(u_innerRadius, u_outerRadius, dist);

    gl_FragColor = vec4(u_shadowColor.rgb, alpha * u_shadowColor.a);
}
