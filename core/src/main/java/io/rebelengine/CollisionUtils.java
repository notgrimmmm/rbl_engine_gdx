package io.rebelengine;

import com.badlogic.gdx.math.Vector3;

/**
 * Utility for Narrow Phase collision detection between AABBs.
 */
public class CollisionUtils {

    /**
     * Sweeps one AABB against another static AABB.
     * 
     * @param start      Start position of the moving AABB
     * @param end        End position of the moving AABB
     * @param mins       Min extents of the moving AABB
     * @param maxs       Max extents of the moving AABB
     * @param targetPos  Position of the static AABB
     * @param targetMins Min extents of the static AABB
     * @param targetMaxs Max extents of the static AABB
     * @param result     TraceResult to update if a closer hit is found
     */
    public static void sweepAABB(Vector3 start, Vector3 end, Vector3 mins, Vector3 maxs,
                                 Vector3 targetPos, Vector3 targetMins, Vector3 targetMaxs,
                                 BSPCollision.TraceResult result) {
        
        // 1. Create the expanded "Minkowski" AABB
        float eMinX = targetPos.x + targetMins.x - maxs.x;
        float eMinY = targetPos.y + targetMins.y - maxs.y;
        float eMinZ = targetPos.z + targetMins.z - maxs.z;
        
        float eMaxX = targetPos.x + targetMaxs.x - mins.x;
        float eMaxY = targetPos.y + targetMaxs.y - mins.y;
        float eMaxZ = targetPos.z + targetMaxs.z - mins.z;

        // 2. Ray vs AABB test
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;

        float tMin = 0.0f;
        float tMax = 1.0f;
        
        Vector3 hitNormal = new Vector3();

        // X axis
        if (Math.abs(dx) < 0.000001f) {
            if (start.x < eMinX || start.x > eMaxX) return;
        } else {
            float t1 = (eMinX - start.x) / dx;
            float t2 = (eMaxX - start.x) / dx;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                hitNormal.set(dx < 0 ? 1 : -1, 0, 0);
            }
            tMax = Math.min(tMax, far);
        }

        // Y axis
        if (Math.abs(dy) < 0.000001f) {
            if (start.y < eMinY || start.y > eMaxY) return;
        } else {
            float t1 = (eMinY - start.y) / dy;
            float t2 = (eMaxY - start.y) / dy;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                hitNormal.set(0, dy < 0 ? 1 : -1, 0);
            }
            tMax = Math.min(tMax, far);
        }

        // Z axis
        if (Math.abs(dz) < 0.000001f) {
            if (start.z < eMinZ || start.z > eMaxZ) return;
        } else {
            float t1 = (eMinZ - start.z) / dz;
            float t2 = (eMaxZ - start.z) / dz;
            float near = Math.min(t1, t2);
            float far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                hitNormal.set(0, 0, dz < 0 ? 1 : -1);
            }
            tMax = Math.min(tMax, far);
        }

        if (tMin < tMax && tMin < result.fraction) {
            // Check if we started inside
            if (start.x >= eMinX && start.x <= eMaxX &&
                start.y >= eMinY && start.y <= eMaxY &&
                start.z >= eMinZ && start.z <= eMaxZ) {
                result.startSolid = true;
                result.fraction = 0;
            } else {
                result.fraction = Math.max(0, tMin);
                result.normal.set(hitNormal);
            }
        }
    }
}
