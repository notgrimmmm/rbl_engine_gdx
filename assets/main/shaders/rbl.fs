#ifdef GL_ES
precision highp float;
#endif

// =========================================================================
// SHADOW DISTANCE & DARKNESS CONTROLS (EDIT THESE VALUES)
// =========================================================================
// 1. Max camera distance where shadows remain visible (Increase to draw farther away)
#define SHADOW_MAX_DISTANCE 1500.0  

// 2. Fade window distance before MAX_DISTANCE to prevent abrupt shadow clipping
#define SHADOW_FADE_RANGE    300.0  

// 3. Darkness multiplier in shadow (0.0 = pitch black shadow, 0.4 = 40% ambient light remains)
#define SHADOW_DARKNESS      0.60   
// =========================================================================

uniform sampler2D u_base;
uniform sampler2D u_lightmap;

// --- Shadow Uniforms & Varyings ---
uniform sampler2D u_shadowTexture;
uniform float u_shadowPCFOffset;
uniform float u_shadowBias;       
uniform float u_shadowSlopeBias;  
uniform vec3  u_sunDir;           

varying vec4 v_shadowPos;

uniform float u_hasLightmap;
uniform vec3  u_lightGridColor;

uniform vec4  u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;
uniform float u_fogEnabled;
uniform vec3  u_cameraPos;

uniform float u_useNormalShading; // 1.0 = GLB Model / Prop, 0.0 = BSP Brush

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
varying vec3 v_normal;

float unpackDepth(vec4 enc) {
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
    return dot(enc, bitShifts);
}

float getShadow() {
    // Distance check: clip/fade shadow past SHADOW_MAX_DISTANCE
    float camDist = length(u_cameraPos - v_worldPos);
    if (camDist > SHADOW_MAX_DISTANCE) {
        return 1.0;
    }

    vec3 projCoords = v_shadowPos.xyz / v_shadowPos.w;
    projCoords = projCoords * 0.5 + 0.5;

    // Out of frustum bounds // For Debug changes 1.0 to 0.0 to see the bounds
    if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || 
        projCoords.y < 0.0 || projCoords.y > 1.0) {
        return 1.0;
    }

    vec3 N = normalize(v_normal);
    vec3 L = normalize(-u_sunDir);
    float NdotL = max(0.0, dot(N, L));
    float bias = max(u_shadowBias * (1.0 - NdotL) * u_shadowSlopeBias, u_shadowBias);

    float currentDepth = projCoords.z;

    float shadow = 0.0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            vec2 offset = vec2(float(x), float(y)) * u_shadowPCFOffset;
            float pcfDepth = unpackDepth(texture2D(u_shadowTexture, projCoords.xy + offset));
            shadow += (currentDepth - bias > pcfDepth) ? 0.0 : 1.0;
        }
    }
    shadow /= 9.0;

    // Smooth distance fade out
    float fadeStart = SHADOW_MAX_DISTANCE - SHADOW_FADE_RANGE;
    if (camDist > fadeStart) {
        float fadeFactor = (camDist - fadeStart) / SHADOW_FADE_RANGE;
        shadow = mix(shadow, 1.0, clamp(fadeFactor, 0.0, 1.0));
    }

    return shadow;
}

void main() {
    vec4 base = texture2D(u_base, v_uv0);
    vec4 lm   = texture2D(u_lightmap, v_uv1);

    // Bypass shadow lookup for props to eliminate self-shadowing
    float shadowSample = (u_useNormalShading > 0.5) ? 1.0 : getShadow();
    
    // Scale shadow sample between ambient darkness level and 1.0
    float shadowLighting = mix(SHADOW_DARKNESS, 1.0, shadowSample);

    // --- Ambient / Lightmaps ---
    vec3 lmLight = lm.rgb * 2.0;
    vec3 rawAmbient = mix(u_lightGridColor, lmLight, u_hasLightmap);
    vec3 indirect   = (u_useNormalShading > 0.5) ? (rawAmbient * 0.80) : rawAmbient;

    // SUN SHADOW: Applies directly to indirect/ambient lighting
    // Works globally regardless of dynamic spot/point lights
    vec3 globalLight = indirect * shadowLighting;
    
    // --- Dynamic Point / Spot Lights ---
    vec3 N = normalize(v_normal);
    vec3 dynLight = vec3(0.0);
    
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
            
            if (u_useNormalShading > 0.5) {
                float NdotL = max(0.0, dot(N, L));
                NdotL = pow(NdotL, 1.8);
                atten *= NdotL;
            }
            
            dynLight += u_lightColor[i] * atten;
        }
    }
    
    vec3 finalLight = globalLight + dynLight;
    vec3 litColor   = base.rgb * finalLight;

    // --- Fog ---
    if (u_fogEnabled > 0.5) {
        float cameraDist = length(u_cameraPos - v_worldPos);
        float fogFactor  = clamp((u_fogEnd - cameraDist) / (u_fogEnd - u_fogStart), 0.0, 1.0);
        float fogBlend   = (1.0 - fogFactor) * u_fogColor.a;
        
        litColor = mix(litColor, u_fogColor.rgb, fogBlend);
    }
    
    gl_FragColor = vec4(litColor, base.a);
}