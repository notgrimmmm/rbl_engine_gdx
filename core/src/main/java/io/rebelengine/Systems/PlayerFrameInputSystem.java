package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.PlayerInputComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.InputFocus;
import io.rebelengine.RebelAshleyEngine;

public class PlayerFrameInputSystem extends EntitySystem implements RebelAshleyEngine.FrameUpdatable {
    private static final Family FAMILY = Family.all(
        InputControlComponent.class,
        PlayerInputComponent.class,
        TransformComponent.class,
        MouseLookComponent.class
    ).get();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void frameUpdate() {
        for (Entity entity : entities) {
            // InputFocus is the single source of truth. If this entity is not
            // the focused one, or input is globally locked, do nothing — and
            // also clear any stale mouse deltas so they don't snap in on regain.
            if (!InputFocus.is(entity)) {
                MouseLookComponent ml = entity.getComponent(MouseLookComponent.class);
                ml.accumulatedDeltaX = 0f;
                ml.accumulatedDeltaY = 0f;
                continue;
            }

            PlayerInputComponent input = entity.getComponent(PlayerInputComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);

            input.moveForward = 0f;
            input.moveRight = 0f;

            if (Gdx.input.isKeyPressed(Input.Keys.W)) input.moveForward += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) input.moveForward -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) input.moveRight -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) input.moveRight += 1f;

            if (mouseLook.active && Gdx.input.isCursorCatched()) {
                transform.currentYaw += mouseLook.accumulatedDeltaX * mouseLook.sensitivity;
                transform.currentPitch = MathUtils.clamp(
                    transform.currentPitch - mouseLook.accumulatedDeltaY * mouseLook.sensitivity,
                    -89f,
                    89f
                );
            }
            mouseLook.accumulatedDeltaX = 0f;
            mouseLook.accumulatedDeltaY = 0f;

            input.crouchHeld = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
            input.jumpQueued |= Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        }
    }
}
