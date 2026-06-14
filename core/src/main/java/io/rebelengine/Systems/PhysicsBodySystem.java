package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPCollision;
import io.rebelengine.CollisionUtils;
import io.rebelengine.Components.InstanceComponent;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.GameContext;
import io.rebelengine.RebelAshleyEngine;
import io.rebelengine.SpatialHash;

public class PhysicsBodySystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable {
    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class
    ).get();

    private ImmutableArray<Entity> entities;
    private final SpatialHash spatialHash = new SpatialHash(128f);

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void fixedUpdate(float fixedDt) {
        spatialHash.clear();
        for (Entity entity : entities) {
            spatialHash.insert(entity);
        }

        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);

            transform.previousPosition.set(transform.currentPosition);
            updateCrouchState(entity, transform, body);

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

            if (body.noclip || GameContext.get().COLLISION() == null) {
                transform.currentPosition.mulAdd(body.velocity, fixedDt);
            } else {
                moveAndCollide(entity, transform, body, fixedDt);
            }
            
            // TODO: probably some interpolation, put this before and after phsycis movement ? 
            if (entity.getComponent(InstanceComponent.class) != null){
                InstanceComponent instance = entity.getComponent(InstanceComponent.class);
                instance.instance.position.set(transform.currentPosition);
            }
        }
    }

    private void updateCrouchState(Entity entity, TransformComponent transform, PhysicsBodyComponent body) {
        if (body.wantsCrouch == body.crouching) {
            return;
        }

        float heightDiff = body.maxsStand.y - body.maxsCrouch.y;
        if (body.wantsCrouch) {
            transform.currentPosition.y -= heightDiff;
            body.crouching = true;
            return;
        }

        if (GameContext.get().COLLISION() == null) {
            transform.currentPosition.y += heightDiff;
            body.crouching = false;
            return;
        }

        body.savedPosition.set(transform.currentPosition).add(0f, heightDiff, 0f);
        body.traceResult.reset();
        traceBox(entity, transform.currentPosition, body.savedPosition, body.minsStand, body.maxsStand, body.traceResult);
        if (!body.traceResult.startSolid && !body.traceResult.allSolid) {
            transform.currentPosition.y += heightDiff;
            body.crouching = false;
        }
    }

    private void moveAndCollide(Entity entity, TransformComponent transform, PhysicsBodyComponent body, float fixedDt) {
        Vector3 mins = body.crouching ? body.minsCrouch : body.minsStand;
        Vector3 maxs = body.crouching ? body.maxsCrouch : body.maxsStand;

        if (body.grounded && (body.velocity.x != 0f || body.velocity.z != 0f)) {
            tryStepUp(entity, transform.currentPosition, body.velocity, mins, maxs, body.stepHeight, fixedDt, body);
        } else {
            slideMove(entity, transform.currentPosition, body.velocity, mins, maxs, fixedDt);
        }

        body.grounded = isOnGround(entity, transform.currentPosition, mins, maxs, body.traceResult);
        if (body.grounded && body.velocity.y < 0f) {
            body.velocity.y = 0f;
        }
    }

    private void traceBox(Entity owner, Vector3 start, Vector3 end, Vector3 mins, Vector3 maxs, BSPCollision.TraceResult result) {
        
        int instanceModelIndex = owner.getComponent(PhysicsBodyComponent.class).bspModelIndexReference;
        GameContext.get().COLLISION().traceBox(start, end, mins, maxs, result, instanceModelIndex);
        
        java.util.Set<Entity> nearby = spatialHash.query(start, end, mins, maxs);
        for (Entity other : nearby) {
            if (other == owner) continue;
            
            TransformComponent tOther = other.getComponent(TransformComponent.class);
            PhysicsBodyComponent pOther = other.getComponent(PhysicsBodyComponent.class);
            Vector3 mOther = pOther.crouching ? pOther.minsCrouch : pOther.minsStand;
            Vector3 xOther = pOther.crouching ? pOther.maxsCrouch : pOther.maxsStand;
            
            CollisionUtils.sweepAABB(start, end, mins, maxs, tOther.currentPosition, mOther, xOther, result);
        }
    }

    private void slideMove(Entity entity, Vector3 position, Vector3 velocity, Vector3 mins, Vector3 maxs, float fixedDt) {
        float timeLeft = fixedDt;
        Vector3 end = new Vector3();
        BSPCollision.TraceResult tr = new BSPCollision.TraceResult();
        Vector3[] planes = new Vector3[4];
        for(int i=0; i<4; i++) planes[i] = new Vector3();
        int numPlanes = 0;

        for (int bump = 0; bump < 4 && timeLeft > 0; bump++) {
            end.set(position).add(velocity.x * timeLeft, velocity.y * timeLeft, velocity.z * timeLeft);
            tr.reset();
            traceBox(entity, position, end, mins, maxs, tr);

            if (tr.startSolid) {
                BSPCollision.clipVelocity(velocity, tr.normal, velocity, 1.0f);
                continue;
            }

            if (tr.fraction > 0) {
                position.add(velocity.x * timeLeft * tr.fraction, velocity.y * timeLeft * tr.fraction, velocity.z * timeLeft * tr.fraction);
            }

            if (tr.fraction == 1f) break;

            timeLeft -= timeLeft * tr.fraction;

            if (numPlanes < 4) {
                planes[numPlanes].set(tr.normal);
                numPlanes++;
            }

            for (int i = 0; i < numPlanes; i++) {
                BSPCollision.clipVelocity(velocity, planes[i], velocity, 1.01f);
            }
        }
    }

    private boolean isOnGround(Entity entity, Vector3 position, Vector3 mins, Vector3 maxs, BSPCollision.TraceResult result) {
        Vector3 down = new Vector3(position).sub(0, 0.25f, 0);
        result.reset();
        traceBox(entity, position, down, mins, maxs, result);
        return (result.fraction < 1f || result.startSolid) && result.normal.y > 0.7f;
    }

    private void tryStepUp(Entity entity, Vector3 position, Vector3 velocity, Vector3 mins, Vector3 maxs,
                           float stepHeight, float fixedDt, PhysicsBodyComponent body) {
        body.savedPosition.set(position);
        body.savedVelocity.set(velocity);

        slideMove(entity, position, velocity, mins, maxs, fixedDt);
        if (position.dst2(body.savedPosition) > 0.01f) {
            return;
        }

        body.stepPosition.set(body.savedPosition).add(0f, stepHeight, 0f);
        body.traceResult.reset();
        traceBox(entity, body.savedPosition, body.stepPosition, mins, maxs, body.traceResult);
        if (body.traceResult.fraction <= 0f) {
            return;
        }

        position.set(body.savedPosition).add(0f, stepHeight * body.traceResult.fraction, 0f);
        velocity.set(body.savedVelocity);
        slideMove(entity, position, velocity, mins, maxs, fixedDt);

        body.downPosition.set(position).sub(0f, stepHeight, 0f);
        body.traceResult.reset();
        traceBox(entity, position, body.downPosition, mins, maxs, body.traceResult);
        if (body.traceResult.fraction < 1f) {
            position.lerp(body.downPosition, body.traceResult.fraction);
        }
    }
}
