package io.rebelengine;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.Components.CameraComponent;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.PlayerInputComponent;
import io.rebelengine.Components.TransformComponent;

public class BasePlayer {
    public static final float RADIUS = 16f;
    public static final float HEIGHT_STAND = 32f;
    public static final float HEIGHT_CROUCH = 28f;
    public static final float EYE_STAND = 48f;
    public static final float EYE_CROUCH = 20f;

    private final Entity entity;
    private final PerspectiveCamera camera;

    public BasePlayer(Vector3 spawnPosition) {
        entity = new Entity();
        entity.flags = EntityFlags.PLAYER | EntityFlags.INITIATOR; // initiator is the one that triggers 

        TransformComponent transform = new TransformComponent();
        transform.previousPosition.set(spawnPosition);
        transform.currentPosition.set(spawnPosition);
        transform.renderPosition.set(spawnPosition);

        PlayerInputComponent input = new PlayerInputComponent();
        MouseLookComponent mouseLook = new MouseLookComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.minsStand.set(-RADIUS, -HEIGHT_STAND / 2f, -RADIUS);
        physics.maxsStand.set(RADIUS, HEIGHT_STAND / 2f, RADIUS);
        physics.minsCrouch.set(-RADIUS, -HEIGHT_CROUCH / 2f, -RADIUS);
        physics.maxsCrouch.set(RADIUS, HEIGHT_CROUCH / 2f, RADIUS);
        physics.eyeStand = EYE_STAND;
        physics.eyeCrouch = EYE_CROUCH;

        camera = new PerspectiveCamera(90f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 3f;
        camera.far = 4096f;

        CameraComponent cameraComponent = new CameraComponent();
        cameraComponent.camera = camera;

        InputControlComponent control = new InputControlComponent();
        control.priority = 100; // players win focus arbitration over vehicles/spectators

        entity.add(transform);
        entity.add(input);
        entity.add(mouseLook);
        entity.add(physics);
        entity.add(cameraComponent);
        entity.add(control);
        camera.update();
    }

    /** Set input focus to this player. */
    public void focus() {
        InputFocus.set(entity);
    }

    public Entity getEntity() {
        return entity;
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }
}
