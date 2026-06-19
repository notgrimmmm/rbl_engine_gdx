/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Behaviors;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.LocomotionBehavior;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;

/**
 *
 * @author me
 */
public class Locomotion_LadderMove implements LocomotionBehavior{
    private final Vector3 up = new Vector3(0, 1, 0);
    private final Vector3 movement = new Vector3();

    @Override
    public void fixedUpdate(Entity entity) {
        
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
        InputControlComponent input = entity.getComponent(InputControlComponent.class);
        
        //body.noclip = true;
        body.grounded = true;
        
        movement.setZero();
        movement.mulAdd(up, input.Up);
        
        if (!movement.isZero()){
            movement.nor().scl(body.moveSpeed);
        }
        
        body.velocity.y = movement.y;
        body.velocity.x = 0;
        body.velocity.z = 0;
    }
    
}
