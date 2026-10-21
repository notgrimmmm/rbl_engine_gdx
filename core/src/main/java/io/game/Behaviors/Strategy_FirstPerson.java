/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Behaviors;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.CameraComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.StrategyLogic;
import io.rebelengine.Components.TransformComponent;

/**
 *
 */
public class Strategy_FirstPerson implements StrategyLogic{

    @Override
    public void update(PhysicsBodyComponent physics, TransformComponent transform, CameraComponent cameraComponent) {
        float eyeHeight = physics.crouching ? physics.eyeCrouch : physics.eyeStand;
        cameraComponent.camera.position.set(transform.renderPosition).add(cameraComponent.offsetX, eyeHeight, cameraComponent.offsetZ);

        float yawRad = transform.currentYaw * MathUtils.degreesToRadians;
        float pitchRad = transform.currentPitch * MathUtils.degreesToRadians;
        float cosPitch = MathUtils.cos(pitchRad);

        cameraComponent.camera.direction.set(
            cosPitch * MathUtils.sin(yawRad),
            MathUtils.sin(pitchRad),
            -cosPitch * MathUtils.cos(yawRad)
        ).nor();
        cameraComponent.camera.up.set(Vector3.Y);
    }
    
}
