package io.rebelengine;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.Set;

/**
 * BSP brush collision — sweeps a capsule/AABB against solid brush planes.
 *
 * This is the core collision system used by the player, enemies, and
 * any dynamic object that needs to interact with the BSP world.
 *
 * Based on Quake 3's trace system:
 *   - A trace sweeps from a start point to an end point
 *   - Returns the fraction of movement completed before hitting geometry
 *   - Returns the plane normal at the collision point for sliding
 *
 * Usage:
 *   BSPCollision col = new BSPCollision(bsp);
 *   TraceResult result = col.traceBox(start, end, mins, maxs);
 *   if (result.fraction < 1.0f) { // hit something }
 */
public class BSPCollision {

    private final BSPFile bsp;

    // Scratch vectors — reused to avoid allocation
    private final Vector3 tmp1 = new Vector3();
    private final TraceResult tmpTraceResult = new TraceResult(); // used for overlaps where we dont want a physical collision

    // -----------------------------------------------------------------------
    // Trace result
    // -----------------------------------------------------------------------
    public static class TraceResult {
        /** 0.0 = blocked immediately, 1.0 = no collision */
        public float   fraction   = 1.0f;
        /** Surface normal at collision point */
        public Vector3 normal     = new Vector3();
        /** True if started inside solid */
        public boolean startSolid = false;
        /** True if entire trace is inside solid */
        public boolean allSolid   = false;

        public Entity hitEntity = null;
        
        public String texture_hit = "";
        public String texture_own = "";

        public Array<Integer> nonSolidOverlapIndices = new Array<>(false, 4); // collect brush index of non solid to compare later

        public void reset() {
            fraction   = 1.0f;
            normal.set(0, 0, 0);
            startSolid = false;
            allSolid   = false;
            hitEntity = null;
            
            nonSolidOverlapIndices.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public BSPCollision(BSPFile bsp) {
        this.bsp = bsp;
    }

    // -----------------------------------------------------------------------
    // Main trace — sweep an AABB from start to end
    // -----------------------------------------------------------------------

    /**
     * Sweep an axis-aligned box from start to end against the BSP world.
     *
     * @param start  start position (GL coords, Y-up)
     * @param end    end position   (GL coords, Y-up)
     * @param mins   box min extents relative to origin (e.g. -15, -15, -15)
     * @param maxs   box max extents relative to origin (e.g.  15,  15,  56)
     * @param result output — reused each call, reset before use
     */
    public void traceBox(Vector3 start, Vector3 end,
                         Vector3 mins,  Vector3 maxs,
                         TraceResult result) {
        result.reset();
        // Check all solid brushes in the world model
        // only collide with the static brushes in the bsp ( models[0] )
        // other models like brush entities are in models *1, *2, etc
        BSPFile.Model world = bsp.models[0]; // unused bc we want to collide with triggers which are not in [0]
        
        for (int bi = 0; bi < world.numBrushes; bi++) { // probably change this to all models

            BSPFile.Brush brush = bsp.brushes[world.firstBrush + bi];
            // Only test solid brushes
            if (!isSolid(brush)) continue;
            
            traceThroughBrush(brush, start, end, mins, maxs, result);
            if (result.allSolid) return;
        }
    }
    public void traceBox(Vector3 start, Vector3 end,
                        Vector3 mins,  Vector3 maxs,
                        TraceResult result, int skipModelIndex) {
        traceBox(start, end, mins, maxs, result, skipModelIndex, null);
    }

    public void traceBox(Vector3 start, Vector3 end,
                        Vector3 mins,  Vector3 maxs,
                        TraceResult result, int skipModelIndex,
                        Set<Integer> skipModelIndices) {
        result.reset();

        // trace against the world geometry (models[0]) first
        BSPFile.Model world = bsp.models[0];
        for (int bi = 0; bi < world.numBrushes; bi++) {
            BSPFile.Brush brush = bsp.brushes[world.firstBrush + bi];
            if (!isSolid(brush)) continue;
            
            traceThroughBrush(brush, start, end, mins, maxs, result);
            if (result.allSolid) return;
        }

        // loop through all OTHER sub-models (dynamic entities/triggers)
        for (int mi = 1; mi < bsp.models.length; mi++) {

            if (mi == skipModelIndex) continue;
            if (skipModelIndices != null && skipModelIndices.contains(mi)) continue;

            BSPFile.Model subModel = bsp.models[mi];

            // Loop ONLY through the brushes that belong to this specific sub-model
            for (int bi = 0; bi < subModel.numBrushes; bi++) {
                BSPFile.Brush brush = bsp.brushes[subModel.firstBrush + bi];
                tmpTraceResult.reset(); // reset temp for overlap

                if (!isSolid(brush)) {
                    // Collect trigger zones / items if hit
                    traceThroughBrush(brush, start, end, mins, maxs, tmpTraceResult);
                    if (tmpTraceResult.startSolid || tmpTraceResult.fraction < 1.0){
                        result.nonSolidOverlapIndices.add(mi); // mi is the real submodel index, bi is the index inside that mi brush
                        break;
                    }

                    continue;
                }

                traceThroughBrush(brush, start, end, mins, maxs, result);
                if (result.allSolid) return;
            }
        }
    }

    /**
     * Simplified point trace — no box, just a ray.
     * Useful for line-of-sight checks.
     */
    public void traceRay(Vector3 start, Vector3 end, TraceResult result, int indexModel) {
        Vector3 zero = new Vector3(0, 0, 0);
        traceBox(start, end, zero, zero, result, indexModel);
    }

    // -----------------------------------------------------------------------
    // Per-brush trace
    // -----------------------------------------------------------------------

    private void traceThroughBrush(BSPFile.Brush brush,
                                    Vector3 start, Vector3 end,
                                    Vector3 mins, Vector3 maxs,
                                    TraceResult result) {
        float enterFrac = -1f;
        float leaveFrac =  1f;
        Vector3 clipNormal = new Vector3();
        boolean startOut = false;

        float maxDist1 = -Float.MAX_VALUE;
        Vector3 bestNormal = new Vector3();

        for (int si = 0; si < brush.numSides; si++) {
            BSPFile.BrushSide side  = bsp.brushSides[brush.firstSide + si];
            BSPFile.Plane     plane = bsp.planes[side.planeIndex];

            float pnx = plane.nx;
            float pny = plane.nz;
            float pnz = -plane.ny;
            float pd   = plane.dist;

            float offset = 0f;
            if (pnx > 0) offset += maxs.x * pnx; else offset += mins.x * pnx;
            if (pny > 0) offset += maxs.y * pny; else offset += mins.y * pny;
            if (pnz > 0) offset += maxs.z * pnz; else offset += mins.z * pnz;

            float dist1 = start.x * pnx + start.y * pny + start.z * pnz - (pd + offset);
            float dist2 = end.x   * pnx + end.y   * pny + end.z   * pnz - (pd + offset);

            if (dist1 > 0) startOut = true;
            if (dist1 > maxDist1) {
                maxDist1 = dist1;
                bestNormal.set(pnx, pny, pnz);
            }

            if (dist1 > 0 && dist2 > 0) return;
            if (dist1 <= 0 && dist2 <= 0) continue;

            if (dist1 > dist2) {
                float f = (dist1 - EPSILON) / (dist1 - dist2);
                if (f > enterFrac) {
                    enterFrac = f;
                    clipNormal.set(pnx, pny, pnz);
                }
            } else {
                float f = (dist1 + EPSILON) / (dist1 - dist2);
                if (f < leaveFrac) leaveFrac = f;
            }
        }

        if (!startOut) {
            result.startSolid = true;
            result.normal.set(bestNormal);
            return;
        }

        if (enterFrac < leaveFrac) {
            if (enterFrac > -1f && enterFrac < result.fraction) {
                result.fraction = Math.max(0f, enterFrac);
                result.normal.set(clipNormal);
                result.texture_hit = bsp.textures[brush.textureIndex].name;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sliding move — resolves velocity against collisions (up to 4 planes)
    // -----------------------------------------------------------------------

    private static final int   MAX_CLIP_PLANES = 4;
    private static final float EPSILON         = 0.03125f; // 1/32 unit
    private static final float STOP_EPSILON    = 0.1f;

    /**
     * Move with full sliding collision resolution.
     * Clips velocity against hit planes and continues movement.
     *
     * @param position  current position (modified in place)
     * @param velocity  current velocity (modified in place — zeroed on axis of collision)
     * @param mins      box mins
     * @param maxs      box maxs
     * @param fixedstep     time step
     */
    public void slideMove(Vector3 position, Vector3 velocity,
                          Vector3 mins, Vector3 maxs, float fixedstep) {
        Vector3[] planes   = new Vector3[MAX_CLIP_PLANES];
        for (int i = 0; i < MAX_CLIP_PLANES; i++) planes[i] = new Vector3();
        int numPlanes = 0;

        float timeLeft = fixedstep;
        Vector3 end    = new Vector3();
        TraceResult tr = new TraceResult();

        for (int bumpCount = 0; bumpCount < 4 && timeLeft > 0; bumpCount++) {
            end.set(position).add(tmp1.set(velocity).scl(timeLeft));
            traceBox(position, end, mins, maxs, tr);

            if (tr.startSolid) {
                // Already inside something, clip velocity and try to move anyway
                clipVelocity(velocity, tr.normal, velocity, 1.0f);
                continue;
            }

            if (tr.fraction > 0) {
                position.add(tmp1.set(velocity).scl(timeLeft * tr.fraction));
            }

            if (tr.fraction == 1f) break;

            timeLeft -= timeLeft * tr.fraction;

            if (numPlanes < MAX_CLIP_PLANES) {
                planes[numPlanes].set(tr.normal);
                numPlanes++;
            }

            // Clip velocity against all hit planes
            for (int i = 0; i < numPlanes; i++) {
                clipVelocity(velocity, planes[i], velocity, 1.01f);
            }
        }
    }

    /**
     * Clip a velocity vector against a plane normal.
     * overbounce > 1.0 adds bounce, 1.0 = perfect slide.
     */
    public static void clipVelocity(Vector3 in, Vector3 normal,
                                     Vector3 out, float overbounce) {
        float backoff = in.dot(normal);
        if (backoff >= 0) return; // Already moving away or parallel
        
        backoff *= overbounce;
        out.set(in).sub(tmp3.set(normal).scl(backoff));

        // Kill tiny components to prevent jitter
        if (Math.abs(out.x) < STOP_EPSILON) out.x = 0;
        if (Math.abs(out.y) < STOP_EPSILON) out.y = 0;
        if (Math.abs(out.z) < STOP_EPSILON) out.z = 0;
    }
    private static final Vector3 tmp3 = new Vector3();

    // -----------------------------------------------------------------------
    // Ground check
    // -----------------------------------------------------------------------

    /**
     * Check if there is solid ground directly below the position.
     * Returns true if the surface below is walkable (normal.y > 0.7).
     *
     * @param position current position
     * @param mins     box mins
     * @param maxs     box maxs
     * @param result   reused trace result
     */
    public boolean isOnGround(Vector3 position, Vector3 mins, Vector3 maxs,
                               TraceResult result) {
        tmp1.set(position).sub(0, 0.25f, 0); // check 0.25 units below
        traceBox(position, tmp1, mins, maxs, result);
        return (result.fraction < 1f || result.startSolid) && result.normal.y > 0.7f;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isSolid(BSPFile.Brush brush) {
        if (brush.textureIndex < 0 || brush.textureIndex >= bsp.textures.length) return false;
        BSPFile.Texture tex = bsp.textures[brush.textureIndex];
        
        // Solid if content flag has CONTENTS_SOLID or clip texture
        return (tex.contentFlags & BSPFile.CONTENTS_SOLID) != 0
            || tex.name.startsWith("textures/common/clip");
    }

    
}
