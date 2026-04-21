#ifdef GL_ES
precision highp float;
#endif

uniform sampler2D u_base;
uniform sampler2D u_lightmap;

uniform float u_hasLightmap;
uniform vec3  u_lightGridColor;

#define MAX_LIGHTS 5
uniform int   u_numLights;
uniform vec3  u_lightPos[MAX_LIGHTS];
uniform vec3  u_lightColor[MAX_LIGHTS];
uniform float u_lightRadius[MAX_LIGHTS];
uniform vec3  u_lightDir[MAX_LIGHTS];
uniform float u_lightCone[MAX_LIGHTS];
uniform int   u_lightType[MAX_LIGHTS];

varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec3 v_worldPos;

void main() {
    vec4 base = texture2D(u_base, v_uv0);
    vec4 lm   = texture2D(u_lightmap, v_uv1);
    
    // Calculate Indirect/Baked Light
    vec3 lmLight = lm.rgb * 2.0;
    vec3 indirect = mix(u_lightGridColor, lmLight, u_hasLightmap);
    
    vec3 dynLight = vec3(0.0);
    
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= u_numLights) break;
        
        float dist  = length(u_lightPos[i] - v_worldPos);
        float atten = max(0.0, 1.0 - dist / u_lightRadius[i]);
        atten = atten * atten;
        
        if (u_lightType[i] == 1) {
            vec3  toFrag    = normalize(v_worldPos - u_lightPos[i]);
            float cosAngle  = dot(toFrag, normalize(u_lightDir[i]));
            float inner     = u_lightCone[i];
            float outer     = inner - 0.08;
            float spotAtten = smoothstep(outer, inner, cosAngle);
            atten *= spotAtten;
        }
        
        dynLight += u_lightColor[i] * atten;
    }
    
    // Additive logic: indirect + dynamic lights
    // This ensures that even if indirect is (0,0,0), dynLight still illuminates the base
    vec3 finalLight = indirect + dynLight;
    
    gl_FragColor = vec4(base.rgb * finalLight, base.a);
}