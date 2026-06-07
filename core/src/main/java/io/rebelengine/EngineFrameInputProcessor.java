package io.rebelengine;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import io.rebelengine.Components.MouseLookComponent;

public class EngineFrameInputProcessor extends InputAdapter {
    private static final Family FAMILY = Family.all(MouseLookComponent.class).get();

    private final RebelAshleyEngine engine;

    public EngineFrameInputProcessor(RebelAshleyEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (!Gdx.input.isCursorCatched()) {
            resetMouseTracking();
            return false;
        }

        ImmutableArray<Entity> entities = engine.getEntitiesFor(FAMILY);
        for (Entity entity : entities) {
            MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);
            if (!mouseLook.active) {
                continue;
            }

            if (!mouseLook.hasLastMousePosition) {
                mouseLook.lastMouseX = screenX;
                mouseLook.lastMouseY = screenY;
                mouseLook.hasLastMousePosition = true;
                continue;
            }

            mouseLook.accumulatedDeltaX += screenX - mouseLook.lastMouseX;
            mouseLook.accumulatedDeltaY += screenY - mouseLook.lastMouseY;
            mouseLook.lastMouseX = screenX;
            mouseLook.lastMouseY = screenY;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return mouseMoved(screenX, screenY);
    }

    private void resetMouseTracking() {
        ImmutableArray<Entity> entities = engine.getEntitiesFor(FAMILY);
        for (Entity entity : entities) {
            MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);
            mouseLook.hasLastMousePosition = false;
        }
    }
}
