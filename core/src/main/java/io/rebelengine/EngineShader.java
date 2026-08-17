package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class EngineShader implements Disposable {

    // --- Mode Constants ---
    public static final int MODE_BSP     = 0;
    public static final int MODE_DYNAMIC = 1;

    // --- Light Constants ---
    public static final int MAX_LIGHTS  = 5;
    public static final int LIGHT_POINT = 0;
    public static final int LIGHT_SPOT  = 1;

    // --- Core Shader ---
    private final ShaderProgram program;

    // --- Dynamic Light Buffers ---
    private final float[] lightPositions = new float[MAX_LIGHTS * 3];
    private final float[] lightColors    = new float[MAX_LIGHTS * 3];
    private final float[] lightRadii     = new float[MAX_LIGHTS];
    private final float[] lightDirs      = new float[MAX_LIGHTS * 3];
    private final float[] lightCones     = new float[MAX_LIGHTS];
    private final int[]   lightTypes     = new int[MAX_LIGHTS];
    private int numLights = 0;

    // --- Fog Fields ---
    private final Color fogColor = new Color(0.1f, 0.1f, 0.1f, 0.4f);
    private float fogStart = 10.0f;
    private float fogEnd   = 800.0f;
    private boolean fogEnabled = true;

    // --- Shadow Map Fields ---
    private final Matrix4 shadowMapProjViewTrans = new Matrix4();
    private float shadowPCFOffset  = 1.0f / 2048.0f; // Default for 2048x2048 texture
    private float shadowBias       = 0.003f;         // Constant depth bias
    private float shadowSlopeBias  = 2.5f;           // Slope-scale multiplier
    private int shadowTextureUnit  = 2;              // Default unit 2 (0=base, 1=lightmap)
    private boolean receiveShadows = true;          // Toggle shadow map sampling
    private final Vector3 sunDir   = new Vector3(-0.5f, -1.0f, -0.3f);

    public EngineShader() {
        ShaderProgram.pedantic = false;

        program = new ShaderProgram(
            Gdx.files.internal("main/shaders/rbl.vs"),
            Gdx.files.internal("main/shaders/rbl.fs")
        );

        if (!program.isCompiled()) {
            throw new GdxRuntimeException("EngineShader compile error:\n" + program.getLog());
        }
    }
    
    
    public Vector3 getSunDir(){
        return sunDir;
    }

    public void bind() {
        program.bind();

        // Samplers
        program.setUniformi("u_base", 0);
        program.setUniformi("u_lightmap", 1);
        program.setUniformi("u_shadowTexture", shadowTextureUnit);

        // Shadows
        program.setUniformf("u_shadowPCFOffset", shadowPCFOffset);
        program.setUniformf("u_shadowBias", shadowBias);
        program.setUniformf("u_shadowSlopeBias", shadowSlopeBias);
        program.setUniformf("u_receiveShadows", receiveShadows ? 1.0f : 0.0f);
        program.setUniformMatrix("u_shadowMapProjViewTrans", shadowMapProjViewTrans);
        program.setUniformf("u_sunDir", sunDir.x, sunDir.y, sunDir.z);

        // Fog
        program.setUniformf("u_fogColor", fogColor.r, fogColor.g, fogColor.b, fogColor.a);
        program.setUniformf("u_fogStart", fogStart);
        program.setUniformf("u_fogEnd", fogEnd);
        program.setUniformf("u_fogEnabled", fogEnabled ? 1.0f : 0.0f);

        // Dynamic Lighting
        program.setUniformi("u_numLights", numLights);
        if (numLights > 0) {
            program.setUniform3fv("u_lightPos", lightPositions, 0, numLights * 3);
            program.setUniform3fv("u_lightColor", lightColors, 0, numLights * 3);
            program.setUniform1fv("u_lightRadius", lightRadii, 0, numLights);
            program.setUniform3fv("u_lightDir", lightDirs, 0, numLights * 3);
            program.setUniform1fv("u_lightCone", lightCones, 0, numLights);
            program.setUniform1iv("u_lightType", lightTypes, 0, numLights);
        }
    }

    // --- Transform & Rendering Setters ---

    public void setMVP(Matrix4 mvp) {
        program.setUniformMatrix("u_mvp", mvp);
    }

    public void setModel(Matrix4 model) {
        program.setUniformMatrix("u_model", model);
    }

    public void setNormalMatrix(Matrix3 normalMatrix) {
        program.setUniformMatrix("u_normalMatrix", normalMatrix);
    }

    public void setCameraPosition(Vector3 cameraPos) {
        program.setUniformf("u_cameraPos", cameraPos);
    }

    public void setUseNormalShading(boolean enabled) {
        program.setUniformf("u_useNormalShading", enabled ? 1.0f : 0.0f);
    }

    public void setHasLightmap(float value) {
        program.setUniformf("u_hasLightmap", value);
    }

    public void setLightGridColor(float r, float g, float b) {
        program.setUniformf("u_lightGridColor", r, g, b);
    }

    // --- Shadow Setters ---

    /** Toggle whether current geometry receives shadows from the shadow map. */
    public void setReceiveShadows(boolean enabled) {
        this.receiveShadows = enabled;
        program.setUniformf("u_receiveShadows", enabled ? 1.0f : 0.0f);
    }

    /** Sets the shadow map light camera matrix (Light Proj * Light View * Model). */
    public void setShadowMapProjViewTrans(Matrix4 shadowMatrix) {
        this.shadowMapProjViewTrans.set(shadowMatrix);
        program.setUniformMatrix("u_shadowMapProjViewTrans", this.shadowMapProjViewTrans);
    }

    /** Sets the PCF sampling offset based on the shadow map resolution. */
    public void setShadowMapResolution(float shadowMapSize) {
        this.shadowPCFOffset = 1.0f / shadowMapSize;
        program.setUniformf("u_shadowPCFOffset", this.shadowPCFOffset);
    }

    /** Sets which GL texture unit slot the shadow map FrameBuffer texture is bound to (default 2). */
    public void setShadowTextureUnit(int unit) {
        this.shadowTextureUnit = unit;
        program.setUniformi("u_shadowTexture", unit);
    }

    /** Sets a constant depth bias (in light-space [0,1] units). Default 0.003. */
    public void setShadowBias(float bias) {
        this.shadowBias = bias;
        program.setUniformf("u_shadowBias", bias);
    }

    /** Sets the slope-scaled bias multiplier. Default 2.5. */
    public void setShadowSlopeBias(float slopeBias) {
        this.shadowSlopeBias = slopeBias;
        program.setUniformf("u_shadowSlopeBias", slopeBias);
    }

    public void setSunDirection(Vector3 direction) {
        this.sunDir.set(direction);
        program.setUniformf("u_sunDir", sunDir.x, sunDir.y, sunDir.z);
    }

    // --- Fog Setters ---

    public void setFog(Color color, float start, float end) {
        this.fogColor.set(color);
        this.fogStart = start;
        this.fogEnd = end;
    }

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
    }

    // --- Light Management ---

    public void clearLights() {
        this.numLights = 0;
    }

    public void addPointLight(float x, float y, float z,
                              float r, float g, float b,
                              float radius) {
        if (numLights >= MAX_LIGHTS) return;

        int i = numLights;
        int idx3 = i * 3;

        lightPositions[idx3]     = x;
        lightPositions[idx3 + 1] = y;
        lightPositions[idx3 + 2] = z;

        lightColors[idx3]        = r;
        lightColors[idx3 + 1]    = g;
        lightColors[idx3 + 2]    = b;

        lightRadii[i]            = radius;

        lightDirs[idx3]          = 0;
        lightDirs[idx3 + 1]      = -1;
        lightDirs[idx3 + 2]      = 0;

        lightCones[i]            = 0.0f;
        lightTypes[i]            = LIGHT_POINT;

        numLights++;
    }

    public void addSpotLight(Vector3 pos, float radius, Vector3 dir,
                             Color color, float coneAngleDegrees) {
        if (numLights >= MAX_LIGHTS) return;

        int i = numLights;
        int idx3 = i * 3;

        lightPositions[idx3]     = pos.x;
        lightPositions[idx3 + 1] = pos.y;
        lightPositions[idx3 + 2] = pos.z;

        lightColors[idx3]        = color.r;
        lightColors[idx3 + 1]    = color.g;
        lightColors[idx3 + 2]    = color.b;

        lightRadii[i]            = radius;

        lightDirs[idx3]          = dir.x;
        lightDirs[idx3 + 1]      = dir.y;
        lightDirs[idx3 + 2]      = dir.z;

        lightCones[i]            = (float) Math.cos(Math.toRadians(coneAngleDegrees));
        lightTypes[i]            = LIGHT_SPOT;

        numLights++;
    }

    public void addLight(float x, float y, float z,
                         float r, float g, float b,
                         float radius) {
        addPointLight(x, y, z, r, g, b, radius);
    }

    // --- Getters & Disposal ---

    public ShaderProgram getProgram() {
        return program;
    }

    @Override
    public void dispose() {
        if (program != null) {
            program.dispose();
        }
    }
}