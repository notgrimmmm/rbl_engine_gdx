package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.PlayerInputComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.RebelAshleyEngine;

public class PlayerLocomotionSystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable {
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class,
        PlayerInputComponent.class
    ).get();

    private final Vector3 forward = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 movement = new Vector3();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void fixedUpdate(float fixedDt) {
        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
            PlayerInputComponent input = entity.getComponent(PlayerInputComponent.class);

            body.wantsCrouch = input.crouchHeld;

            float yawRad = transform.currentYaw * MathUtils.degreesToRadians;
            forward.set(MathUtils.sin(yawRad), 0f, -MathUtils.cos(yawRad));
            right.set(forward.z, 0f, -forward.x);

            movement.setZero();
            movement.mulAdd(forward, input.moveForward);
            movement.mulAdd(right, input.moveRight);

            if (!movement.isZero()) {
                movement.nor().scl(body.crouching ? body.moveSpeed * 0.5f : body.moveSpeed);
                body.velocity.x = movement.x;
                body.velocity.z = movement.z;
            } else if (body.grounded) {
                body.velocity.x = 0f;
                body.velocity.z = 0f;
            }

            if (input.jumpQueued) {
                body.jumpRequested = true;
                input.jumpQueued = false;
            }
        }
    }
}
