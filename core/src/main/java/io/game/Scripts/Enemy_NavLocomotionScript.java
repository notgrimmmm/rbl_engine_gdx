package io.game.Scripts;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.game.Units.BaseEnemy;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.NavMeshGraph;

public class Enemy_NavLocomotionScript implements EntityScript {
    private final BaseEnemy owner;
    private final NavMeshGraph navGraph;

    private final Array<Vector3> targetQueue = new Array<>();
    private final Array<Vector3> waypoints = new Array<>();
    private int currentWaypointIndex = 0;

    public float moveSpeed = 30.0f;
    public float arrivalRadius = 24.0f;
    
    public Enemy_NavLocomotionScript(BaseEnemy entOwner){
        this.owner = entOwner;
        this.navGraph = owner.NAV_GRAPH;
    }


    /**
     * Rebuilds the A* path toward the next target at the head of targetQueue.
     */
    private void buildPathToCurrentTarget() {
        waypoints.clear();
        currentWaypointIndex = 0;

        if (targetQueue.size == 0 || owner == null || owner.transform == null || navGraph == null) {
            return;
        }

        Vector3 nextDestination = targetQueue.first();
        Array<Vector3> rawPath = navGraph.findPath(owner.transform.currentPosition, nextDestination);

        if (rawPath.size > 0) {
            waypoints.addAll(rawPath);

            // Skip waypoint 0 if entity is already standing inside the arrival radius of start
            if (waypoints.size > 1 && waypoints.get(0).dst2(owner.transform.currentPosition) < arrivalRadius * arrivalRadius) {
                currentWaypointIndex = 1;
            }
        }
    }

    /**
     * Modular Execution Commands
     * Handles single Vector3 targets, Array<Vector3> target queues, or raw commands.
     */
    @Override
    public void execute(Object parameter) {
        if (parameter instanceof Vector3 singleTarget) {
            targetQueue.clear();
            targetQueue.add(new Vector3(singleTarget));
            buildPathToCurrentTarget();
        } else if (parameter instanceof Array<?> list) {
            targetQueue.clear();
            for (Object item : list) {
                if (item instanceof Vector3 v) {
                    targetQueue.add(new Vector3(v));
                }
            }
            buildPathToCurrentTarget();
        } else if (parameter == null && targetQueue.size > 0) {
            // Trigger path calculation on current queue state
            buildPathToCurrentTarget();
        }
    }

    @Override
    public void execute_off() {
        targetQueue.clear();
        waypoints.clear();
        currentWaypointIndex = 0;
        if (owner != null && owner.body != null) {
            owner.body.velocity.x = 0;
            owner.body.velocity.z = 0;
        }
    }

    // Helper utilities for external modular callers
    public void addTarget(Vector3 newTarget) {
        targetQueue.add(new Vector3(newTarget));
        if (waypoints.size == 0 || currentWaypointIndex >= waypoints.size) {
            buildPathToCurrentTarget();
        }
    }

    public void clearTargets() {
        execute_off();
    }

    @Override
    public void update_fixed(float fixedDt) {
        if (owner == null || owner.transform == null || owner.body == null || navGraph == null) return;

        // Idle state if no path or all waypoints reached
        if (waypoints.size == 0 || currentWaypointIndex >= waypoints.size) {
            return;
        }

        Vector3 currentPos = owner.transform.currentPosition;
        Vector3 targetWaypoint = waypoints.get(currentWaypointIndex);

        float dx = targetWaypoint.x - currentPos.x;
        float dz = targetWaypoint.z - currentPos.z;
        float distSq = dx * dx + dz * dz;

        // Waypoint arrival check
        if (distSq < arrivalRadius * arrivalRadius) {
            currentWaypointIndex++;

            // Reached end of current path
            if (currentWaypointIndex >= waypoints.size) {
                if (targetQueue.size > 0) {
                    targetQueue.removeIndex(0); // Pop reached target
                }

                // If more targets exist in queue, rebuild path to next location
                if (targetQueue.size > 0) {
                    buildPathToCurrentTarget();
                    return;
                } else {
                    // Queue finished - stop movement
                    owner.body.velocity.x = 0;
                    owner.body.velocity.z = 0;
                    return;
                }
            }

            targetWaypoint = waypoints.get(currentWaypointIndex);
            dx = targetWaypoint.x - currentPos.x;
            dz = targetWaypoint.z - currentPos.z;
        }

        // Apply velocity toward active waypoint
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length > 0.0001f) {
            owner.body.velocity.x = (dx / length) * moveSpeed;
            owner.body.velocity.z = (dz / length) * moveSpeed;
            
        }
        
        // Rotate to face direction of movement
        // Option A: Extract from existing velocity
        Vector3 direction = new Vector3(owner.body.velocity).nor(); // LibGDX nor() normalizes in-place
        float yawAngleDeg = MathUtils.atan2Deg(direction.x, direction.z);
        owner.transform.currentYaw = yawAngleDeg;
    }
    
    @Override
    public void set_components(Array<Object> components) {}
}