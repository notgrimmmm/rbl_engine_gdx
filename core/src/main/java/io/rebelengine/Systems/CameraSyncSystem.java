package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.CameraComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.RebelAshleyEngine;

public class CameraSyncSystem extends EntitySystem implements RebelAshleyEngine.AlphaUpdatable {
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class,
        CameraComponent.class
    ).get();

    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void alphaUpdate(float alpha) {
        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
            CameraComponent cameraComponent = entity.getComponent(CameraComponent.class);
            if (!cameraComponent.active || cameraComponent.camera == null) {
                continue;
            }

            float eyeHeight = physics.crouching ? physics.eyeCrouch : physics.eyeStand;
            cameraComponent.camera.position.set(transform.renderPosition).add(0f, eyeHeight, 0f);

            float yawRad = transform.currentYaw * MathUtils.degreesToRadians;
            float pitchRad = transform.currentPitch * MathUtils.degreesToRadians;
            float cosPitch = MathUtils.cos(pitchRad);

            cameraComponent.camera.direction.set(
                cosPitch * MathUtils.sin(yawRad),
                MathUtils.sin(pitchRad),
                -cosPitch * MathUtils.cos(yawRad)
            ).nor();
            cameraComponent.camera.up.set(Vector3.Y);
            cameraComponent.camera.update();
        }
    }
}
