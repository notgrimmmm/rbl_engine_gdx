package io.game.Units;

import io.rebelengine.Helpers.InputFocus;
import io.rebelengine.Helpers.EntityFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import io.game.Behaviors.Input_PlayerMoveBehavior;
import io.game.Behaviors.Locomotion_PlayerMove;
import io.rebelengine.Components.CameraComponent;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.MouseLookComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.Helpers.PhysicsType;

public class BasePlayer {
    public static final float RADIUS = 16f;
    public static final float HEIGHT_STAND = 64f;
    public static final float HEIGHT_CROUCH = 32f;
    public static final float EYE_STAND = 50f;
    public static final float EYE_CROUCH = 30f;

    private final Entity entity;
    private final PerspectiveCamera camera;

    public BasePlayer(Vector3 spawnPosition) {
        entity = new Entity();
        entity.flags = EntityFlags.PLAYER | EntityFlags.INITIATOR; // initiator is the one that triggers 

        TransformComponent transform = new TransformComponent();
        transform.previousPosition.set(spawnPosition);
        transform.currentPosition.set(spawnPosition);
        transform.renderPosition.set(spawnPosition);

        InputControlComponent input = new InputControlComponent();
        input.inputBehavior = new Input_PlayerMoveBehavior();
        input.locomotionBehavior = new Locomotion_PlayerMove();
        input.priority = 100;
        
        MouseLookComponent mouseLook = new MouseLookComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.minsStand.set(-RADIUS, -HEIGHT_STAND / 2f, -RADIUS);
        physics.maxsStand.set(RADIUS, HEIGHT_STAND / 2f, RADIUS);
        physics.minsCrouch.set(-RADIUS, -HEIGHT_CROUCH / 2f, -RADIUS);
        physics.maxsCrouch.set(RADIUS, HEIGHT_CROUCH / 2f, RADIUS);
        physics.eyeStand = EYE_STAND;
        physics.eyeCrouch = EYE_CROUCH;
        physics.jumpSpeed = 180f;
        physics.Physics = PhysicsType.Mutable; // mutable specifically for dynamic CONTROL of player or for scripted

        camera = new PerspectiveCamera(90f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 3f;
        camera.far = 4096f;

        CameraComponent cameraComponent = new CameraComponent();
        cameraComponent.camera = camera;

        entity.add(transform);
        entity.add(input);
        entity.add(mouseLook);
        entity.add(physics);
        entity.add(cameraComponent);
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
