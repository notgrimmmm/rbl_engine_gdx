package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.PlayerInputComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.RebelAshleyEngine;

public class PlayerFrameInputSystem extends EntitySystem implements RebelAshleyEngine.FrameUpdatable {
    private static final Family FAMILY = Family.all(
        PlayerInputComponent.class,
        TransformComponent.class,
        MouseLookComponent.class
    ).get();

    private ImmutableArray<Entity> entities;
    private boolean inputBlocked;

    public void setInputBlocked(boolean inputBlocked) {
        this.inputBlocked = inputBlocked;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void frameUpdate() {
        for (Entity entity : entities) {
            PlayerInputComponent input = entity.getComponent(PlayerInputComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);

            if (!input.active) {
                continue;
            }

            if (inputBlocked) {
                input.moveForward = 0f;
                input.moveRight = 0f;
                input.crouchHeld = false;
                mouseLook.accumulatedDeltaX = 0f;
                mouseLook.accumulatedDeltaY = 0f;
                continue;
            }

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
