/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Behaviors;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.LocomotionBehavior;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;

/**
 *
 * @author me
 */
public class Locomotion_PlayerMove implements LocomotionBehavior{

    private final Vector3 forward = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 movement = new Vector3();
    
    @Override
    public void fixedUpdate(Entity entity) {
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
        InputControlComponent input = entity.getComponent(InputControlComponent.class);

        body.wantsCrouch = input.CrouchHeld;

        float yawRad = transform.currentYaw * MathUtils.degreesToRadians;
        forward.set(MathUtils.sin(yawRad), 0f, -MathUtils.cos(yawRad));
        right.set(forward.z, 0f, -forward.x);

        movement.setZero();
        movement.mulAdd(forward, input.Forward);
        movement.mulAdd(right, input.Right);

        if (!movement.isZero()) {
            movement.nor().scl(body.crouching ? body.moveSpeed * 0.5f : body.moveSpeed);
            body.velocity.x = movement.x;
            body.velocity.z = movement.z;
        } else if (body.grounded) {
            body.velocity.x = 0f;
            body.velocity.z = 0f;
        }

        if (input.JumpQueued) {
            body.jumpRequested = true;
            input.JumpQueued = false;
        }
    }
    
}
