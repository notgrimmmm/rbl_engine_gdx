package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPCollision;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.RebelAshleyEngine;

public class PhysicsBodySystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable {
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class
    ).get();

    private final Vector3 savedPosition = new Vector3();
    private final Vector3 savedVelocity = new Vector3();
    private final Vector3 stepPosition = new Vector3();
    private final Vector3 downPosition = new Vector3();
    private final BSPCollision.TraceResult traceResult = new BSPCollision.TraceResult();

    private ImmutableArray<Entity> entities;
    private BSPCollision collision;

    public void setCollision(BSPCollision collision) {
        this.collision = collision;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void fixedUpdate(float fixedDt) {
        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);

            transform.previousPosition.set(transform.currentPosition);
            updateCrouchState(transform, body);

            if (body.jumpRequested && body.grounded) {
                body.velocity.y = body.jumpSpeed;
                body.grounded = false;
            }
            body.jumpRequested = false;

            if (!body.grounded) {
                body.velocity.y -= body.gravity * fixedDt;
                if (body.velocity.y < body.terminalVelocity) {
                    body.velocity.y = body.terminalVelocity;
                }
            }

            if (body.noclip || collision == null) {
                transform.currentPosition.mulAdd(body.velocity, fixedDt);
            } else {
                moveAndCollide(transform, body, fixedDt);
            }
        }
    }

    private void updateCrouchState(TransformComponent transform, PhysicsBodyComponent body) {
        if (body.wantsCrouch == body.crouching) {
            return;
        }

        float heightDiff = body.maxsStand.y - body.maxsCrouch.y;
        if (body.wantsCrouch) {
            transform.currentPosition.y -= heightDiff;
            body.crouching = true;
            return;
        }

        if (collision == null) {
            transform.currentPosition.y += heightDiff;
            body.crouching = false;
            return;
        }

        savedPosition.set(transform.currentPosition).add(0f, heightDiff, 0f);
        traceResult.reset();
        collision.traceBox(savedPosition, savedPosition, body.minsStand, body.maxsStand, traceResult);
        if (!traceResult.startSolid && !traceResult.allSolid) {
            transform.currentPosition.y += heightDiff;
            body.crouching = false;
        }
    }

    private void moveAndCollide(TransformComponent transform, PhysicsBodyComponent body, float fixedDt) {
        Vector3 mins = body.crouching ? body.minsCrouch : body.minsStand;
        Vector3 maxs = body.crouching ? body.maxsCrouch : body.maxsStand;

        if (body.grounded && (body.velocity.x != 0f || body.velocity.z != 0f)) {
            tryStepUp(transform.currentPosition, body.velocity, mins, maxs, body.stepHeight, fixedDt);
        } else {
            collision.slideMove(transform.currentPosition, body.velocity, mins, maxs, fixedDt);
        }

        body.grounded = collision.isOnGround(transform.currentPosition, mins, maxs, traceResult);
        if (body.grounded && body.velocity.y < 0f) {
            body.velocity.y = 0f;
        }
    }

    private void tryStepUp(Vector3 position, Vector3 velocity, Vector3 mins, Vector3 maxs,
                           float stepHeight, float fixedDt) {
        savedPosition.set(position);
        savedVelocity.set(velocity);

        collision.slideMove(position, velocity, mins, maxs, fixedDt);
        if (position.dst2(savedPosition) > 0.01f) {
            return;
        }

        stepPosition.set(savedPosition).add(0f, stepHeight, 0f);
        traceResult.reset();
        collision.traceBox(savedPosition, stepPosition, mins, maxs, traceResult);
        if (traceResult.fraction <= 0f) {
            return;
        }

        position.set(savedPosition).add(0f, stepHeight * traceResult.fraction, 0f);
        velocity.set(savedVelocity);
        collision.slideMove(position, velocity, mins, maxs, fixedDt);

        downPosition.set(position).sub(0f, stepHeight, 0f);
        traceResult.reset();
        collision.traceBox(position, downPosition, mins, maxs, traceResult);
        if (traceResult.fraction < 1f) {
            position.lerp(downPosition, traceResult.fraction);
        }
    }
}
