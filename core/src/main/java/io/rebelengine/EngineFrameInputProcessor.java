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
        // If the cursor is uncaptured (e.g. console open), don't accumulate
        // mouse motion — releasing/capturing will reseed the baseline below.
        if (!Gdx.input.isCursorCatched()) {
            resetMouseTracking();
            return false;
        }

        // Globally locked (console, pause, cutscene) — drop the deltas.
        if (InputFocus.isLocked()) {
            return false;
        }

        Entity focused = InputFocus.get();
        // No focused entity — nothing to do, but make sure stale state is gone.
        if (focused == null) {
            resetMouseTracking();
            return false;
        }

        // Apply only to the focused entity (if it has a MouseLookComponent).
        MouseLookComponent mouseLook = focused.getComponent(MouseLookComponent.class);
        if (mouseLook == null) {
            return false;
        }

        if (!mouseLook.active) {
            return false;
        }

        if (!mouseLook.hasLastMousePosition) {
            mouseLook.lastMouseX = screenX;
            mouseLook.lastMouseY = screenY;
            mouseLook.hasLastMousePosition = true;
            return false;
        }

        mouseLook.accumulatedDeltaX += screenX - mouseLook.lastMouseX;
        mouseLook.accumulatedDeltaY += screenY - mouseLook.lastMouseY;
        mouseLook.lastMouseX = screenX;
        mouseLook.lastMouseY = screenY;
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
            mouseLook.accumulatedDeltaX = 0f;
            mouseLook.accumulatedDeltaY = 0f;
        }
    }
}
