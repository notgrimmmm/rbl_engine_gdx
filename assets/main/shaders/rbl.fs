#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
precision highp float;
#endif

uniform sampler2D u_base;
uniform sampler2D u_lightmap;

uniform float u_hasLightmap;
uniform vec3  u_lightGridColor;
uniform float u_useNormalShading;

uniform vec4  u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform float u_fogEnabled;
uniform vec3  u_cameraPos;

#define MAX_LIGHTS 15
uniform int   u_numLights;
uniform vec3  u_lightPos[MAX_LIGHTS];
uniform vec3  u_lightColor[MAX_LIGHTS];
uniform float u_lightRadius[MAX_LIGHTS];
uniform vec3  u_lightDir[MAX_LIGHTS];
uniform float u_lightCone[MAX_LIGHTS];
uniform int   u_lightType[MAX_LIGHTS];
uniform float u_lightIntensity[MAX_LIGHTS];

varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec3 v_worldPos;
varying vec3 v_normal;

// LightGrid Baked Lighting Uniforms
uniform vec3 u_gridAmbient;
uniform vec3 u_gridDirected;
uniform vec3 u_gridDir;

void main() {
    vec4 base = texture2D(u_base, v_uv0);
    vec3 N = normalize(v_normal);
    vec3 dynLight = vec3(0.0);
    vec3 indirect = vec3(0.0);

    if (u_hasLightmap > 0.5) {
        vec4 lm = texture2D(u_lightmap, v_uv1);
        indirect = lm.rgb * 2.0;
    } else {
        vec3 L_grid = length(u_gridDir) > 0.001 ? normalize(u_gridDir) : vec3(0.0, 1.0, 0.0);
        float NdotL = max(0.0, dot(N, L_grid));
        
        // Full lighting sum
        indirect = u_gridAmbient + (u_gridDirected * NdotL);
        indirect *= 2.0;
    }
    
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (i >= u_numLights) break;
        
        vec3 lightVec = u_lightPos[i] - v_worldPos;
        float dist    = length(lightVec);
        float atten   = max(0.0, 1.0 - dist / u_lightRadius[i]);
        atten = atten * atten;
        
        if (atten > 0.0) {
            vec3 L = normalize(lightVec);
            
            if (u_lightType[i] == 1) {
                vec3  toFrag    = -L;
                float cosAngle  = dot(toFrag, normalize(u_lightDir[i]));
                float inner     = u_lightCone[i];
                float outer     = inner - 0.08;
                float spotAtten = smoothstep(outer, inner, cosAngle);
                atten *= spotAtten;
            }
            
            float NdotL = max(0.0, dot(N, L));
            atten *= NdotL;
            
            dynLight += u_lightColor[i] * u_lightIntensity[i] * atten;
        }
    }
    
    vec3 finalLight = indirect + dynLight;
    vec3 litColor   = base.rgb * finalLight;

    if (u_fogEnabled > 0.5) {
        float cameraDist = length(u_cameraPos - v_worldPos);
        float fogFactor  = clamp((u_fogEnd - cameraDist) / (u_fogEnd - u_fogStart), 0.0, 1.0);
        float fogBlend   = (1.0 - fogFactor) * u_fogColor.a;
        
        litColor = mix(litColor, u_fogColor.rgb, fogBlend);
    }

    //Add banding remove with dither here
    
    gl_FragColor = vec4(litColor, base.a);
}