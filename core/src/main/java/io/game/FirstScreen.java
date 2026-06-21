package io.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import games.rednblack.miniaudio.MiniAudio;
import io.rebelengine.RebelEngineBase;

public class FirstScreen implements Screen {

    private static final String MAP_PATH     = "main/zoo.bsp"; // change bsp
    private static final String TEXTURE_ROOT = "main/baseq3";

    private final RebelEngineBase ENGINE;
    private final MiniAudio AUDIO_ENGINE;
    
    public FirstScreen(){
        AUDIO_ENGINE = new MiniAudio();
        ENGINE = new RebelEngineBase(AUDIO_ENGINE);
    }

    @Override
    public void show() {
        // --- Load map ---
        ENGINE.loadMap(MAP_PATH, TEXTURE_ROOT);
        Gdx.input.setInputProcessor(ENGINE.getInputProcessor());
        
        // Mouse options
        Gdx.input.setCursorCatched(true);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit(); // Safe exit 
        }

        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        ENGINE.render(delta);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        ENGINE.resize(width, height);
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        System.out.println("[ SCREEN DISPOSE ]");
        if (AUDIO_ENGINE != null) AUDIO_ENGINE.dispose();
        if (ENGINE != null) ENGINE.dispose();
    }
}
