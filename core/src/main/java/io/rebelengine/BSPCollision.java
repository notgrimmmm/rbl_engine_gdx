package io.rebelengine;

import com.badlogic.gdx.math.Vector3;

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
    private final Vector3 tmp2 = new Vector3();

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

        public void reset() {
            fraction   = 1.0f;
            normal.set(0, 0, 0);
            startSolid = false;
            allSolid   = false;
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
        BSPFile.Model world = bsp.models[0];
        for (int bi = 0; bi < bsp.brushes.length; bi++) {
            BSPFile.Brush brush = bsp.brushes[bi];
            // Only test solid brushes
            if (!isSolid(brush)) continue;
            traceThroughBrush(brush, start, end, mins, maxs, result);
            if (result.allSolid) return;
        }
    }

    /**
     * Simplified point trace — no box, just a ray.
     * Useful for line-of-sight checks.
     */
    public void traceRay(Vector3 start, Vector3 end, TraceResult result) {
        Vector3 zero = new Vector3(0, 0, 0);
        traceBox(start, end, zero, zero, result);
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

        for (int si = 0; si < brush.numSides; si++) {
            BSPFile.BrushSide side  = bsp.brushSides[brush.firstSide + si];
            BSPFile.Plane     plane = bsp.planes[side.planeIndex];

            // Convert plane normal from Q3 to GL coords: Q3(nx,ny,nz) -> GL(nx,nz,-ny)
            float pnx = plane.nx;
            float pny = plane.nz;
            float pnz = -plane.ny;
            float pd   = plane.dist;

            // Expand plane by box extents (Minkowski sum)
            float offset = 0f;
            if (pnx > 0) offset += maxs.x * pnx; else offset += mins.x * pnx;
            if (pny > 0) offset += maxs.y * pny; else offset += mins.y * pny;
            if (pnz > 0) offset += maxs.z * pnz; else offset += mins.z * pnz;

            float dist1 = start.x * pnx + start.y * pny + start.z * pnz - (pd + offset);
            float dist2 = end.x   * pnx + end.y   * pny + end.z   * pnz - (pd + offset);

            if (dist1 > 0) startOut = true;

            // Both in front — no collision with this plane
            if (dist1 > 0 && dist2 >= 0) return;
            // Both behind — skip this plane
            if (dist1 <= 0 && dist2 <= 0) continue;

            float denom = dist1 - dist2;
            if (dist1 > dist2) {
                // Entering
                float f = (dist1 - EPSILON) / denom;
                if (f > enterFrac) {
                    enterFrac = f;
                    clipNormal.set(pnx, pny, pnz);
                }
            } else {
                // Leaving
                float f = (dist1 + EPSILON) / denom;
                if (f < leaveFrac) leaveFrac = f;
            }
        }

        if (!startOut) {
            result.startSolid = true;
            result.allSolid   = (leaveFrac <= 0);
            return;
        }

        if (enterFrac < leaveFrac) {
            if (enterFrac > -1f && enterFrac < result.fraction) {
                result.fraction = Math.max(0f, enterFrac);
                result.normal.set(clipNormal);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sliding move — resolves velocity against collisions (up to 4 planes)
    // -----------------------------------------------------------------------

    private static final int   MAX_CLIP_PLANES = 4;
    private static final float EPSILON         = 0.01125f; // 1/32 unit
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

            if (tr.allSolid) {
                velocity.set(0, 0, 0);
                return;
            }

            if (tr.fraction > 0) {
                position.add(tmp1.set(velocity).scl(timeLeft * tr.fraction));
                numPlanes = 0;
            }

            if (tr.fraction == 1f) break; // moved all the way

            timeLeft -= timeLeft * tr.fraction;

            // Store hit plane
            if (numPlanes < MAX_CLIP_PLANES) {
                planes[numPlanes].set(tr.normal);
                numPlanes++;
            }

            // Clip velocity against all hit planes
            for (int i = 0; i < numPlanes; i++) {
                clipVelocity(velocity, planes[i], velocity, 1.01f);
            }

            // Check if we're going back into a previous plane
            boolean ok = true;
            for (int i = 0; i < numPlanes; i++) {
                if (velocity.dot(planes[i]) < 0) { ok = false; break; }
            }
            if (!ok) {
                // Zero out velocity
                velocity.set(0, 0, 0);
                break;
            }
        }
    }

    /**
     * Clip a velocity vector against a plane normal.
     * overbounce > 1.0 adds bounce, 1.0 = perfect slide.
     */
    public static void clipVelocity(Vector3 in, Vector3 normal,
                                     Vector3 out, float overbounce) {
        float backoff = in.dot(normal) * overbounce;
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
        return result.fraction < 1f && result.normal.y > 0.7f;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isSolid(BSPFile.Brush brush) {
        if (brush.textureIndex < 0 || brush.textureIndex >= bsp.textures.length) return false;
        BSPFile.Texture tex = bsp.textures[brush.textureIndex];
        // Solid if content flag has CONTENTS_SOLID
        return (tex.contentFlags & BSPFile.CONTENTS_SOLID) != 0
            || tex.name.startsWith("textures/common/clip");
    }
}