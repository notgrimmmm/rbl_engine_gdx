/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Behaviors;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import io.rebelengine.Components.InputBehavior;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.TransformComponent;

/**
 * Requires InputControl, Transform, MouseLook
 */
public class Input_PlayerMoveBehavior implements InputBehavior{

    @Override
    public void update(Entity entity) {
        InputControlComponent input = entity.getComponent(InputControlComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);

        input.Forward = 0f;
        input.Right = 0f;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.Forward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.Forward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) input.Right -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) input.Right += 1f;

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

        input.CrouchHeld = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);
        input.JumpQueued |= Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
    }
    
}
