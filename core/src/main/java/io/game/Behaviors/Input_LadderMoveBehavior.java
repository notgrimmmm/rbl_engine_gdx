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
 *
 * Since its an input this should be by frame update
 */
public class Input_LadderMoveBehavior implements InputBehavior{

    public float baseYaw = 0f;
    public float lookYaw = 0f;
    @Override
    public void update(Entity entity) {
        
        
        InputControlComponent input = entity.getComponent(InputControlComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        MouseLookComponent mouseLook = entity.getComponent(MouseLookComponent.class);
        
        input.Up = 0f;
        
        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.Up += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.Up -= 1f;
        
        if (mouseLook.active && Gdx.input.isCursorCatched()) {
            //BUG: this is not working, transform seems to stay at the yaw it was at spawn somehow
            lookYaw = MathUtils.clamp(lookYaw + mouseLook.accumulatedDeltaX * mouseLook.sensitivity, -70, 70);
            transform.currentYaw = baseYaw + lookYaw;
            
            transform.currentPitch = MathUtils.clamp(
                transform.currentPitch - mouseLook.accumulatedDeltaY * mouseLook.sensitivity,
                -30f,
                60f
            );
        }
        mouseLook.accumulatedDeltaX = 0f;
        mouseLook.accumulatedDeltaY = 0f;
    }
    
}
