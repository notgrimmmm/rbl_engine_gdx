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
    public static final int MAX_LIGHTS  = 15;
    public static final int LIGHT_POINT = 0;
    public static final int LIGHT_SPOT  = 1;

    // --- Skinning Constants (must match MAX_BONES in rbl.vs) ---
    public static final int MAX_BONES = 64;

    // --- Core Shader ---
    private final ShaderProgram program;

    // --- Dynamic Light Buffers ---
    private final float[] lightPositions = new float[MAX_LIGHTS * 3];
    private final float[] lightColors    = new float[MAX_LIGHTS * 3];
    private final float[] lightRadii     = new float[MAX_LIGHTS];
    private final float[] lightDirs      = new float[MAX_LIGHTS * 3];
    private final float[] lightCones     = new float[MAX_LIGHTS];
    private final int[]   lightTypes     = new int[MAX_LIGHTS];
    private final float[] lightIntensities = new float[MAX_LIGHTS];
    private int numLights = 0;

    // --- Skinning ---
    private final float[] boneBuffer = new float[MAX_BONES * 16];
    private boolean skinnedState = false; // cached to avoid redundant uniform uploads
    private boolean warnedBoneLimit = false;

    // --- Fog Fields ---
    private final Color fogColor = new Color(0.1f, 0.1f, 0.1f, 0.4f);
    private float fogStart = 10.0f;
    private float fogEnd   = 800.0f;
    private boolean fogEnabled = true;

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

    public void bind() {
        program.bind();
        program.setUniformf("u_useNormalShading", 0.0f);

        // Skinning off by default
        skinnedState = false;
        program.setUniformf("u_skinned", 0.0f);

        // Samplers
        program.setUniformi("u_base", 0);
        program.setUniformi("u_lightmap", 1);

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
            program.setUniform1fv("u_lightIntensity", lightIntensities, 0, numLights);
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

    // --- Skinning Setters ---

    /** Enables/disables bone skinning in the vertex shader. Cached, so cheap to call often. */
    public void setSkinned(boolean skinned) {
        if (skinned == skinnedState) return;
        skinnedState = skinned;
        program.setUniformf("u_skinned", skinned ? 1.0f : 0.0f);
    }

    /** Uploads bone matrices (from NodePart.bones) to u_bones. Extra bones beyond MAX_BONES are ignored. */
    public void setBones(Matrix4[] bones) {
        int count = Math.min(bones.length, MAX_BONES);
        if (bones.length > MAX_BONES && !warnedBoneLimit) {
            warnedBoneLimit = true;
            Gdx.app.error("EngineShader", "Model has " + bones.length + " bones, limit is "
                    + MAX_BONES + ". Extra bones ignored (raise MAX_BONES here AND in rbl.vs).");
        }
        for (int i = 0; i < count; i++) {
            if (bones[i] != null) {
                System.arraycopy(bones[i].val, 0, boneBuffer, i * 16, 16);
            }
        }
        program.setUniformMatrix4fv("u_bones", boneBuffer, 0, count * 16);
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

    public void setLightGridData(Vector3 ambient, Vector3 directed, Vector3 lightDir) {
        program.setUniformf("u_gridAmbient", ambient);
        program.setUniformf("u_gridDirected", directed);
        program.setUniformf("u_gridDir", lightDir);
    }

    /**
     * This one kinda sucks, use spotlight for more full light
     */
    public void addPointLight(float x, float y, float z,
                              float r, float g, float b,
                              float radius, float intensity) {
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
        lightIntensities[i]      = intensity;
        lightTypes[i]            = LIGHT_POINT;

        numLights++;
    }

    public void addSpotLight(Vector3 pos, float radius, Vector3 dir,
                             Color color, float intensity, float coneAngleDegrees) {
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
        lightIntensities[i]      = intensity;
        lightTypes[i]            = LIGHT_SPOT;

        numLights++;
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