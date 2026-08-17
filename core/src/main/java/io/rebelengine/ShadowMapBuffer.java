package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class ShadowMapBuffer implements Disposable {

    private final FrameBuffer fbo;
    private final ShaderProgram depthShader;
    private final OrthographicCamera lightCamera;
    private final int resolution;

    public ShadowMapBuffer(int resolution, float viewportWidth, float viewportHeight) {
        this.resolution = resolution;
        this.fbo = new FrameBuffer(Pixmap.Format.RGBA8888, resolution, resolution, true);

        this.depthShader = new ShaderProgram(
            Gdx.files.internal("main/shaders/depth.vs"),
            Gdx.files.internal("main/shaders/depth.fs")
        );

        if (!depthShader.isCompiled()) {
            throw new GdxRuntimeException("Depth Shader compile error:\n" + depthShader.getLog());
        }

        this.lightCamera = new OrthographicCamera(viewportWidth, viewportHeight);
        // Symmetric near/far improves depth precision for ortho shadow maps.
        this.lightCamera.near = -1024f;
        this.lightCamera.far  =  1024f;
    }

    public void updateLightPosition(Vector3 targetPos, Vector3 lightDir) {
        // lightDir is the direction light travels (e.g. (-0.5, -1, -0.3)).
        // The light camera sits on the side light is *coming from*, i.e.
        // targetPos + dir * distance, looking back at targetPos.
        Vector3 dirNormalized = lightDir.cpy().nor();
        float   offset        = 1000f;
        float yOffset = 200f;

        Vector3 adjustedTarget = targetPos.cpy().add(0, yOffset, 0);
        lightCamera.position.set(adjustedTarget).add(dirNormalized.cpy().scl(offset)); // do add here to point down towards the map. doing sub will invert the cam
        lightCamera.lookAt(adjustedTarget);
        lightCamera.up.set(0, 1, 0);
        lightCamera.update();
    }

    public void begin() {
        fbo.begin();
        Gdx.gl.glViewport(0, 0, resolution, resolution);
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        
        depthShader.bind();
    }

    public void end(int width, int height) {
        fbo.end();
        Gdx.gl.glViewport(0, 0, width, height);
    }

    public void bindShadowTexture(int unit) {
        fbo.getColorBufferTexture().bind(unit);
    }

    public Matrix4 getProjViewTrans() {
        return lightCamera.combined;
    }
    
    public void setMVP(Matrix4 mvp) {
        depthShader.setUniformMatrix("u_mvp", mvp);
    }
    
    public void setModel(Matrix4 model) {
        depthShader.setUniformMatrix("u_model", model);
    }

    public ShaderProgram getDepthShader() {
        return depthShader;
    }

    public int getResolution() {
        return resolution;
    }
    
    public OrthographicCamera getLightCamera() {
        return lightCamera;
    }

    @Override
    public void dispose() {
        fbo.dispose();
        depthShader.dispose();
    }
}