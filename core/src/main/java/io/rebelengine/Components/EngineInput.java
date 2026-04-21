/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Gdx;

public class EngineInput extends InputAdapter {
    public boolean[] keys = new boolean[256];
    public float deltaX, deltaY;

    @Override
    public boolean keyDown(int k) { if(k >= 0 && k < 256) keys[k] = true; return false; }
    @Override
    public boolean keyUp(int k) { if(k >= 0 && k < 256) keys[k] = false; return false; }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        deltaX = Gdx.input.getDeltaX();
        deltaY = Gdx.input.getDeltaY();
        return false;
    }

    @Override
    public boolean touchDown(int x, int y, int p, int b) {
        Gdx.input.setCursorCatched(true);
        return false;
    }

    public void update() {
        // We poll deltas here because mouseMoved only fires on movement
        deltaX = Gdx.input.getDeltaX();
        deltaY = Gdx.input.getDeltaY();
    }
}
