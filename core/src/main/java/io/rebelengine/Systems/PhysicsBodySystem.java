package io.rebelengine.Systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPCollision;
import io.rebelengine.Helpers.Utils_Collision;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.EngineContext;
import io.rebelengine.RebelAshleyEngine;
import io.rebelengine.Helpers.SpatialHash;
import io.rebelengine.Instances.SFX;
import java.util.HashSet;
import java.util.Set;

public class PhysicsBodySystem extends EntitySystem implements RebelAshleyEngine.FixedUpdatable {
    private static final float STEP_EPSILON = 0.03125f;
    private static final float STEP_MIN_RISE = 0.1f;
    private static final float STEP_PROGRESS_EPSILON = 0.01f;
    private static final int OVERLAP_RECOVERY_STEPS = 3;

    private static final Family FAMILY = Family.all(
        TransformComponent.class,
        PhysicsBodyComponent.class
    ).get();

    private ImmutableArray<Entity> entities;
    private final SpatialHash spatialHash = new SpatialHash(128f);
    private final Set<Integer> dynamicModelIndices = new HashSet<>();

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void fixedUpdate(float fixedDt) {
        spatialHash.clear();
        dynamicModelIndices.clear();
        for (Entity entity : entities) {
            PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
            if (body.bspModelIndexReference > 0) {
                dynamicModelIndices.add(body.bspModelIndexReference);
            }
            spatialHash.insert(entity);
        }

        for (Entity entity : entities) {
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);

            transform.previousPosition.set(transform.currentPosition);
            transform.previousYaw = transform.currentYaw;
            transform.previousPitch = transform.currentPitch;

            updateCrouchState(entity, transform, body);
            
            if (body.grounded){
                body.velocity.x -= body.velocity.x * body.frictionCoeff * fixedDt;
                body.velocity.z -= body.velocity.z * body.frictionCoeff * fixedDt;
                
                if (Math.abs(body.velocity.x) < 0.1f) body.velocity.x = 0f;
                if (Math.abs(body.velocity.z) < 0.1f) body.velocity.z = 0f;
                
                //reset roll sign to new 
                if (!body.ignore_physics_transformations){
                    body.roll_sign *= MathUtils.randomSign();
                    if (transform.currentYaw != 0 && transform.currentPitch != 0){
                        body.wantsCrouch = true;
                        transform.currentPitch = transform.currentPitch < 0 ? -90f : 90f;
                        // do yaw ?
                    }
                }
            }

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
                
                if (!body.ignore_physics_transformations){
                    float horizontalVelocity = (body.velocity.x * body.velocity.x) + (body.velocity.z * body.velocity.z);
                    float threshold = 15f;
                    if (horizontalVelocity > (threshold * threshold)){
                        transform.currentYaw += body.roll_sign;
                        transform.currentPitch += body.roll_sign;
                    }
                }
            }

            if (body.noclip || EngineContext.get().COLLISION() == null) {
                transform.currentPosition.mulAdd(body.velocity, fixedDt);
            } else {
                moveAndCollide(entity, transform, body, fixedDt);
            }
        }
    }

    private void updateCrouchState(Entity entity, TransformComponent transform, PhysicsBodyComponent body) {
        if (body.wantsCrouch == body.crouching) {
            return;
        }

        float heightDiff = body.maxsStand.y - body.maxsCrouch.y;
        boolean targetCrouch = body.wantsCrouch;
        Vector3 targetMins = targetCrouch ? body.minsCrouch : body.minsStand;
        Vector3 targetMaxs = targetCrouch ? body.maxsCrouch : body.maxsStand;
        float originOffset = targetCrouch ? -heightDiff : heightDiff;

        if (EngineContext.get().COLLISION() == null) {
            transform.currentPosition.y += originOffset;
            body.crouching = targetCrouch;
            return;
        }

        body.savedPosition.set(transform.currentPosition).add(0f, originOffset, 0f);
        body.traceResult.reset();
        traceBox(entity, body.savedPosition, body.savedPosition, targetMins, targetMaxs, body.traceResult);
        if (!body.traceResult.startSolid && !body.traceResult.allSolid) {
            transform.currentPosition.set(body.savedPosition);
            body.crouching = targetCrouch;
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

    public void traceBox(Entity owner, Vector3 start, Vector3 end, Vector3 mins, Vector3 maxs, BSPCollision.TraceResult result) {
        
        int instanceModelIndex = owner.getComponent(PhysicsBodyComponent.class).bspModelIndexReference;
        EngineContext.get().COLLISION().traceBox(start, end, mins, maxs, result, instanceModelIndex, dynamicModelIndices);
        
        java.util.Set<Entity> nearby = spatialHash.query(start, end, mins, maxs);
        for (Entity other : nearby) {
            if (other == owner) continue;
            float previousFraction = result.fraction;
            
            TransformComponent tOther = other.getComponent(TransformComponent.class);
            PhysicsBodyComponent pOther = other.getComponent(PhysicsBodyComponent.class);
            if (pOther.noclip) continue;

            Vector3 mOther = pOther.crouching ? pOther.minsCrouch : pOther.minsStand;
            Vector3 xOther = pOther.crouching ? pOther.maxsCrouch : pOther.maxsStand;

            Utils_Collision.sweepAABB(start, end, mins, maxs, tOther.currentPosition, mOther, xOther, result);

            if (result.fraction < previousFraction) {
                result.hitEntity = other;
            }
        }
    }

    private boolean slideMove(Entity entity, Vector3 position, Vector3 velocity, Vector3 mins, Vector3 maxs, float fixedDt) {
        float timeLeft = fixedDt;
        Vector3 end = new Vector3();
        BSPCollision.TraceResult tr = new BSPCollision.TraceResult();
        Vector3[] planes = new Vector3[4];
        for(int i=0; i<4; i++) planes[i] = new Vector3();
        int numPlanes = 0;
        boolean blocked = false;

        for (int bump = 0; bump < 4 && timeLeft > 0; bump++) {
            end.set(position).add(velocity.x * timeLeft, velocity.y * timeLeft, velocity.z * timeLeft);
            tr.reset();
            traceBox(entity, position, end, mins, maxs, tr);

            if (tr.startSolid) {
                blocked = true;
                if (recoverFromOverlap(entity, position, mins, maxs, tr)) {
                    continue;
                }
                BSPCollision.clipVelocity(velocity, tr.normal, velocity, 1.0f);
                continue;
            }

            if (tr.fraction > 0) {
                position.add(velocity.x * timeLeft * tr.fraction, velocity.y * timeLeft * tr.fraction, velocity.z * timeLeft * tr.fraction);
            }
            
            // sound impact 
            if (tr.fraction < 1.0f){
                float impactVel = Math.abs(velocity.dot(tr.normal));
                
                if (impactVel > 100f){
                    if (tr.hitEntity != null){
                    }else{
                        // play sound
                        new SFX("pwr_click_reverb").play3d(position, 300f);
                    }
                }
            }

            if (tr.fraction == 1f) break;
            blocked = true;

            timeLeft -= timeLeft * tr.fraction;

            if (numPlanes < 4) {
                planes[numPlanes].set(tr.normal);
                numPlanes++;
            }

            for (int i = 0; i < numPlanes; i++) {
                BSPCollision.clipVelocity(velocity, planes[i], velocity, 1.01f);
            }
        }
        return blocked;
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

        boolean normalBlocked = slideMove(entity, position, velocity, mins, maxs, fixedDt);
        float normalProgress = horizontalDistance2(body.savedPosition, position);
        float intendedProgress = horizontalDistance2(0f, 0f, body.savedVelocity.x * fixedDt, body.savedVelocity.z * fixedDt);
        if (!normalBlocked || intendedProgress <= STEP_PROGRESS_EPSILON) {
            return;
        }

        body.stepPosition.set(body.savedPosition).add(0f, stepHeight, 0f);
        body.traceResult.reset();
        traceBox(entity, body.savedPosition, body.stepPosition, mins, maxs, body.traceResult);
        if (body.traceResult.startSolid || body.traceResult.fraction < 1f) {
            return;
        }

        position.set(body.stepPosition);
        velocity.set(body.savedVelocity);
        slideMove(entity, position, velocity, mins, maxs, fixedDt);
        if (horizontalDistance2(body.savedPosition, position) <= normalProgress + STEP_PROGRESS_EPSILON) {
            position.set(body.savedPosition);
            velocity.set(body.savedVelocity);
            slideMove(entity, position, velocity, mins, maxs, fixedDt);
            return;
        }

        body.downPosition.set(position).sub(0f, stepHeight, 0f);
        body.traceResult.reset();
        traceBox(entity, position, body.downPosition, mins, maxs, body.traceResult);
        if (body.traceResult.fraction < 1f && body.traceResult.normal.y > 0.7f) {
            float landingY = position.y + (body.downPosition.y - position.y) * body.traceResult.fraction;
            float rise = landingY - body.savedPosition.y;
            if (rise >= STEP_MIN_RISE && rise <= stepHeight + STEP_EPSILON) {
                position.lerp(body.downPosition, body.traceResult.fraction);
                return;
            }
        }

        position.set(body.savedPosition);
        velocity.set(body.savedVelocity);
        slideMove(entity, position, velocity, mins, maxs, fixedDt);
    }

    private boolean recoverFromOverlap(Entity entity, Vector3 position, Vector3 mins, Vector3 maxs,
                                       BSPCollision.TraceResult trace) {

        if (trace.normal.isZero()) { // this first so bodies dont get stuck on world start
            return false;
        }

        for (int i = 0; i < OVERLAP_RECOVERY_STEPS; i++) {
            position.mulAdd(trace.normal, STEP_EPSILON);
            trace.reset();
            traceBox(entity, position, position, mins, maxs, trace);
            
            if (!trace.startSolid && !trace.allSolid) {
                return true;
            }
            
            /**
             * NOTE: might cause issue separate if bodies get stuck and properly define when entity is not null
             */
            if (trace.hitEntity == null || trace.normal.isZero()) { 
                return false;
            }
        }
        return true;
    }

    private float horizontalDistance2(Vector3 from, Vector3 to) {
        return horizontalDistance2(from.x, from.z, to.x, to.z);
    }

    private float horizontalDistance2(float fromX, float fromZ, float toX, float toZ) {
        float dx = toX - fromX;
        float dz = toZ - fromZ;
        return dx * dx + dz * dz;
    }
}
