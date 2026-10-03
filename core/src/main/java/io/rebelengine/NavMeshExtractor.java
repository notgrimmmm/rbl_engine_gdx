package io.rebelengine;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.HashSet;
import java.util.Set;

public class NavMeshExtractor {

    public static class NavPolygon {
        public Vector3[] vertices;
        public Vector3 normal = new Vector3();
    }

    public static class NavMeshGeometry {
        public Array<NavPolygon> polygons = new Array<>();
        public Array<Vector3> allVertices = new Array<>();
        public Array<Short> indices = new Array<>();
    }

    /**
     * Minimum dot product with (0, 1, 0) to consider a face a "walkable top surface".
     * 0.707f allows up to ~45-degree slopes. Set higher (e.g. 0.9f) if you only want flat tops.
     */
    private static final float WALKABLE_NORMAL_THRESHOLD = 0.707f;

    public static NavMeshGeometry extract(BSPFile bsp, String navTextureName) {
        NavMeshGeometry result = new NavMeshGeometry();

        Set<Integer> targetTextureIndices = new HashSet<>();
        if (bsp.textures != null) {
            for (int i = 0; i < bsp.textures.length; i++) {
                if (bsp.textures[i].name != null && 
                   (bsp.textures[i].name.equalsIgnoreCase(navTextureName) || 
                    bsp.textures[i].name.endsWith("/nav") ||
                    bsp.textures[i].name.contains("nav"))) {
                    targetTextureIndices.add(i);
                }
            }
        }

        if (targetTextureIndices.isEmpty()) {
            System.out.println("[NavMeshExtractor] No matching nav textures found for: " + navTextureName);
            return result;
        }

        if (bsp.brushes == null || bsp.brushSides == null || bsp.planes == null) return result;

        for (int b = 0; b < bsp.brushes.length; b++) {
            BSPFile.Brush brush = bsp.brushes[b];
            boolean isNavBrush = false;

            if (targetTextureIndices.contains(brush.textureIndex)) {
                isNavBrush = true;
            } else {
                for (int s = 0; s < brush.numSides; s++) {
                    BSPFile.BrushSide side = bsp.brushSides[brush.firstSide + s];
                    if (targetTextureIndices.contains(side.textureIndex)) {
                        isNavBrush = true;
                        break;
                    }
                }
            }

            if (isNavBrush) {
                buildBrushTopPolygons(bsp, brush, result);
            }
        }

        System.out.println("[NavMeshExtractor] Extracted " + result.polygons.size + " top face polygons.");
        return result;
    }

    private static void buildBrushTopPolygons(BSPFile bsp, BSPFile.Brush brush, NavMeshGeometry outNav) {
        for (int i = 0; i < brush.numSides; i++) {
            BSPFile.BrushSide side = bsp.brushSides[brush.firstSide + i];
            BSPFile.Plane plane = bsp.planes[side.planeIndex];

            // 1. Convert Q3 plane normal (X, Y, Z) -> LibGDX Engine (X, Z, -Y)
            Vector3 normal = new Vector3(plane.nx, plane.nz, -plane.ny).nor();

            // FIX 1: FILTER ONLY TOP FACES (upward facing normals)
            if (normal.y < WALKABLE_NORMAL_THRESHOLD) {
                continue; // Skip walls, under-faces, and steep slopes
            }

            // Calculate point on plane in engine space
            Vector3 pointOnPlane = new Vector3(plane.nx, plane.nz, -plane.ny).scl(plane.dist);

            // Generate large initial polygon quad on this plane
            Vector3[] poly = createInitialLargeQuad(normal, pointOnPlane);

            // 2. Clip against ALL OTHER side planes of this brush
            for (int j = 0; j < brush.numSides; j++) {
                if (i == j) continue;

                BSPFile.BrushSide clipSide = bsp.brushSides[brush.firstSide + j];
                BSPFile.Plane clipPlane = bsp.planes[clipSide.planeIndex];

                Vector3 clipNormal = new Vector3(clipPlane.nx, clipPlane.nz, -clipPlane.ny).nor();
                Vector3 clipPoint = new Vector3(clipPlane.nx, clipPlane.nz, -clipPlane.ny).scl(clipPlane.dist);

                poly = clipPolygonWithPlane(poly, clipNormal, clipPoint);
                if (poly == null || poly.length < 3) break;
            }

            // 3. Store valid top-face polygon
            if (poly != null && poly.length >= 3) {
                NavPolygon navPoly = new NavPolygon();
                navPoly.vertices = poly;
                navPoly.normal.set(normal);
                outNav.polygons.add(navPoly);

                // Triangulate face (Fan Method)
                short baseIdx = (short) outNav.allVertices.size;
                for (Vector3 v : poly) {
                    outNav.allVertices.add(new Vector3(v));
                }
                for (int t = 1; t < poly.length - 1; t++) {
                    outNav.indices.add(baseIdx);
                    outNav.indices.add((short) (baseIdx + t));
                    outNav.indices.add((short) (baseIdx + t + 1));
                }
            }
        }
    }

    private static Vector3[] createInitialLargeQuad(Vector3 normal, Vector3 pointOnPlane) {
        Vector3 u = new Vector3();
        if (Math.abs(normal.y) < 0.99f) {
            u.set(0, 1, 0).crs(normal).nor();
        } else {
            u.set(1, 0, 0).crs(normal).nor();
        }
        Vector3 v = new Vector3(normal).crs(u).nor();

        float size = 8192.0f; // Large initial bounds

        return new Vector3[] {
            new Vector3(pointOnPlane).add( u.x * size + v.x * size,  u.y * size + v.y * size,  u.z * size + v.z * size),
            new Vector3(pointOnPlane).add(-u.x * size + v.x * size, -u.y * size + v.y * size, -u.z * size + v.z * size),
            new Vector3(pointOnPlane).add(-u.x * size - v.x * size, -u.y * size - v.y * size, -u.z * size - v.z * size),
            new Vector3(pointOnPlane).add( u.x * size - v.x * size,  u.y * size - v.y * size,  u.z * size - v.z * size)
        };
    }

    private static Vector3[] clipPolygonWithPlane(Vector3[] poly, Vector3 clipNormal, Vector3 clipPoint) {
        Array<Vector3> outList = new Array<>();
        float planeDist = clipNormal.dot(clipPoint);

        for (int i = 0; i < poly.length; i++) {
            Vector3 curr = poly[i];
            Vector3 next = poly[(i + 1) % poly.length];

            float dCurr = curr.dot(clipNormal) - planeDist;
            float dNext = next.dot(clipNormal) - planeDist;

            // Inside brush volume (behind or on plane)
            if (dCurr <= 0.001f) {
                outList.add(curr);
            }

            // Edge crosses clipping plane
            if ((dCurr < -0.001f && dNext > 0.001f) || (dCurr > 0.001f && dNext < -0.001f)) {
                float t = dCurr / (dCurr - dNext);
                Vector3 intersect = new Vector3(curr).lerp(next, t);
                outList.add(intersect);
            }
        }

        if (outList.size < 3) return null;
        return outList.toArray(); // Might give warnings or crash when expecting vec3[]
    }
}