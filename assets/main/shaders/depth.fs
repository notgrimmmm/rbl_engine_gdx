#ifdef GL_ES
precision highp float;
#endif

// Pack a [0,1] depth value into 4 bytes of RGBA8 using 24-bit precision.
// Matches the unpack in rbl.fs (vec4 bitShifts = vec4(1.0, 1.0/255.0, ...)).
vec4 packDepth(float d) {
    const vec4 bitShifts = vec4(
        256.0 * 256.0 * 256.0,
        256.0 * 256.0,
        256.0,
        1.0
    );
    const vec4 bitMasks = vec4(
        0.0,
        1.0 / 256.0,
        1.0 / 256.0,
        1.0 / 256.0
    );
    vec4 comp = fract(d * bitShifts);
    comp -= comp.xxyz * bitMasks;
    return comp;
}

void main() {
    // gl_FragCoord.z is non-linear, but for an orthographic light camera
    // it produces acceptable results without a linearization step.
    gl_FragColor = packDepth(gl_FragCoord.z);
}