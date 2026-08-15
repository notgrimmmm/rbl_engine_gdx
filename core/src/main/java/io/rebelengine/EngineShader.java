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

    public static final int MODE_BSP     = 0;
    public static final int MODE_DYNAMIC = 1;
    public static final int MAX_LIGHTS   = 5;

    public static final int LIGHT_POINT = 0;
    public static final int LIGHT_SPOT  = 1;

    private final ShaderProgram program;

    private final float[] lightPositions = new float[MAX_LIGHTS * 3];
    private final float[] lightColors    = new float[MAX_LIGHTS * 3];
    private final float[] lightRadii     = new float[MAX_LIGHTS];
    private final float[] lightDirs      = new float[MAX_LIGHTS * 3];
    private final float[] lightCones     = new float[MAX_LIGHTS];
    private final int[]   lightTypes     = new int[MAX_LIGHTS];
    private int numLights = 0;

    private final Color fogColor = new Color(0.1f, 0.1f, 0.1f, .4f);
    private float fogStart = 10f;
    private float fogEnd = 800f;
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
        program.setUniformi("u_base",      0);
        program.setUniformi("u_lightmap",  1);
        program.setUniformi("u_numLights", numLights);
        
        program.setUniformf("u_fogColor", fogColor.r, fogColor.g, fogColor.b, fogColor.a);
        program.setUniformf("u_fogStart", fogStart);
        program.setUniformf("u_fogEnd", fogEnd);
        program.setUniformf("u_fogEnabled", fogEnabled ? 1.0f : 0.0f);

        if (numLights > 0) {
            program.setUniform3fv("u_lightPos",    lightPositions, 0, numLights * 3);
            program.setUniform3fv("u_lightColor",  lightColors,    0, numLights * 3);
            program.setUniform1fv("u_lightRadius", lightRadii,     0, numLights);
            program.setUniform3fv("u_lightDir",    lightDirs,      0, numLights * 3);
            program.setUniform1fv("u_lightCone",   lightCones,     0, numLights);
            program.setUniform1iv("u_lightType",   lightTypes,     0, numLights);
        }
    }

    public void setMVP(Matrix4 mvp) {
        program.setUniformMatrix("u_mvp", mvp);
    }
    
    public void setModel(Matrix4 model) {
        program.setUniformMatrix("u_model", model);
    }

    public void setNormalMatrix(Matrix3 normalMatrix) {
        program.setUniformMatrix("u_normalMatrix", normalMatrix);
    }

    public void setUseNormalShading(boolean enabled) {
        program.setUniformf("u_useNormalShading", enabled ? 1.0f : 0.0f);
    }

    /** Set camera pos in world space so GLSL can compute exact camera-to-fragment distance for fog. */
    public void setCameraPosition(Vector3 cameraPos) {
        program.setUniformf("u_cameraPos", cameraPos);
    }

    public void setFog(Color color, float start, float end) {
        this.fogColor.set(color);
        this.fogStart = start;
        this.fogEnd = end;
    }

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
    }

    public void setHasLightmap(float value) {
        program.setUniformf("u_hasLightmap", value);
    }

    public void setLightGridColor(float r, float g, float b) {
        program.setUniformf("u_lightGridColor", r, g, b);
    }

    public void clearLights() {
        numLights = 0;
    }

    public void addPointLight(float x, float y, float z,
                              float r, float g, float b,
                              float radius) {
        if (numLights >= MAX_LIGHTS) return;
        int i = numLights;
        lightPositions[i*3]   = x; lightPositions[i*3+1] = y; lightPositions[i*3+2] = z;
        lightColors[i*3]      = r; lightColors[i*3+1]    = g; lightColors[i*3+2]    = b;
        lightRadii[i]         = radius;
        lightDirs[i*3]        = 0; lightDirs[i*3+1] = -1; lightDirs[i*3+2] = 0;
        lightCones[i]         = 0f;
        lightTypes[i]         = LIGHT_POINT;
        numLights++;
    }

    public void addSpotLight(Vector3 pos, float radius, Vector3 dir,
                              Color color, float coneAngleDegrees) {
        if (numLights >= MAX_LIGHTS) return;
        int i = numLights;
        lightPositions[i*3]   = pos.x; lightPositions[i*3+1] = pos.y; lightPositions[i*3+2] = pos.z;
        lightColors[i*3]      = color.r; lightColors[i*3+1]    = color.g; lightColors[i*3+2]    = color.b;
        lightRadii[i]         = radius;
        lightDirs[i*3]        = dir.x; lightDirs[i*3+1] = dir.y; lightDirs[i*3+2] = dir.z;
        lightCones[i]         = (float) Math.cos(Math.toRadians(coneAngleDegrees));
        lightTypes[i]         = LIGHT_SPOT;
        numLights++;
    }

    public void addLight(float x, float y, float z,
                         float r, float g, float b,
                         float radius) {
        addPointLight(x, y, z, r, g, b, radius);
    }

    public ShaderProgram getProgram() { return program; }

    @Override
    public void dispose() {
        if (program != null) program.dispose();
    }
}