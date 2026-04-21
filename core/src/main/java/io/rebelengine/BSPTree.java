/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

/**
 * BSP 46
 */
public class BSPTree {
    private final BSPFile bsp;
    
    private final Vector3 tmpMin = new Vector3();
    private final Vector3 tmpMax = new Vector3();

    public BSPTree(BSPFile bsp) {
        this.bsp = bsp;
    }

    // -----------------------------------------------------------------------
    // 1. Find which leaf contains a point
    // -----------------------------------------------------------------------

    /**
     * Walk from root node to find the leaf containing (px, py, pz).
     * Returns the leaf index.
     */
    public int findLeaf(float px, float py, float pz) {
        int nodeIndex = 0; // root is always node 0 in Q3 BSP
        while (nodeIndex >= 0) {
            BSPFile.Node  node  = bsp.nodes[nodeIndex];
            BSPFile.Plane plane = bsp.planes[node.planeIndex];
            float dist = plane.nx * px + plane.ny * py + plane.nz * pz - plane.dist;
            nodeIndex = dist >= 0 ? node.children[0] : node.children[1];
        }
        return -(nodeIndex + 1);
    }

    // -----------------------------------------------------------------------
    // 2. Cluster visibility check (no decompression needed — flat bitset)
    // -----------------------------------------------------------------------

    public int getCluster(int leafIndex) {
        if (leafIndex < 0 || leafIndex >= bsp.leaves.length) return -1;
        return bsp.leaves[leafIndex].cluster;
    }

    public boolean isClusterVisible(int fromCluster, int toCluster) {
        if (bsp.visibility == null) return true;
        return bsp.visibility.isVisible(fromCluster, toCluster);
    }

    // -----------------------------------------------------------------------
    // 3. Collect visible faces — cluster PVS + back-to-front tree walk
    // -----------------------------------------------------------------------

    /**
     * Walk the BSP tree and collect face indices visible from the camera leaf.
     *
     * Uses a boolean[] stamp array to avoid submitting the same face twice
     * (faces can appear in multiple leaves).
     *
     * @param cameraLeaf   result of findLeaf()
     * @param outFaces     pre-allocated int[] of size bsp.faces.length
     * @param faceStamp    pre-allocated boolean[] of size bsp.faces.length (zeroed each frame)
     * @return             number of faces written into outFaces
     */
    public int collectVisibleFaces(int cameraLeaf, int[] outFaces, boolean[] faceStamp, Camera camera) {
        int cameraCluster = getCluster(cameraLeaf);
        int[] count = { 0 };

        // Clear stamps
        java.util.Arrays.fill(faceStamp, false);

        walkNode(0, cameraCluster, outFaces, faceStamp, count, camera);
        return count[0];
    }

    private void walkNode(int nodeIndex, int cameraCluster,
                          int[] out, boolean[] stamp, int[] count, Camera camera) {
        if (nodeIndex < 0) {
            // It's a leaf
            int leafIndex = -(nodeIndex + 1);
            BSPFile.Leaf leaf = bsp.leaves[leafIndex];

            // PVS check via cluster
            if (leaf.cluster >= 0 && !isClusterVisible(cameraCluster, leaf.cluster)) return;

            for (int i = 0; i < leaf.numLeafFaces; i++) {
                int faceIdx = bsp.leafFaces[leaf.firstLeafFace + i];
                if (!stamp[faceIdx]) {
                    stamp[faceIdx] = true;
                    out[count[0]++] = faceIdx;
                }
            }
            return;
        }

        // Recurse back-to-front (painter's algorithm)
        BSPFile.Node node = bsp.nodes[nodeIndex];
        
        // frustrum culling 
        if (!isNodeVisible(node, camera)) return;
        
        walkNode(node.children[1], cameraCluster, out, stamp, count, camera);
        walkNode(node.children[0], cameraCluster, out, stamp, count, camera);
    }
    
    private boolean isNodeVisible(BSPFile.Node node, Camera camera) {
        // Check if node AABB intersects camera frustum
        float minX = node.minX, minY = node.minY, minZ = node.minZ;
        float maxX = node.maxX, maxY = node.maxY, maxZ = node.maxZ;

        // Convert Q3 coords to GL coords for frustum check
        // Q3(x,y,z) -> GL(x, z, -y)
        tmpMin.set(minX, minZ, -maxY);
        tmpMax.set(maxX, maxZ, -minY);

        return camera.frustum.boundsInFrustum(
            (tmpMin.x + tmpMax.x) * 0.5f,
            (tmpMin.y + tmpMax.y) * 0.5f,
            (tmpMin.z + tmpMax.z) * 0.5f,
            (tmpMax.x - tmpMin.x) * 0.5f,
            (tmpMax.y - tmpMin.y) * 0.5f,
            (tmpMax.z - tmpMin.z) * 0.5f
        );
    }

    // -----------------------------------------------------------------------
    //  Bezier patch tessellation
    // -----------------------------------------------------------------------

    /**
     * Tessellate a bezier patch face into a triangle list.
     *
     * Q3 patches are bicubic bezier surfaces defined by a patchWidth × patchHeight
     * grid of control points. The grid is subdivided into (patchWidth-1)/2 ×
     * (patchHeight-1)/2 individual 3×3 bezier patches, each tessellated
     * independently.
     *
     * @param face       a FACE_PATCH face
     * @param level      tessellation level (3-5 is typical; 5 = smooth)
     * @param outVerts   output vertex positions, flat [x,y,z, x,y,z, ...]
     * @param outIndices output triangle indices into outVerts
     * @return           number of triangles written
     */
    public int tessellatePatch(BSPFile.Face face, int level,
                               java.util.List<float[]> outVerts,
                               java.util.List<Integer> outIndices) {
        int numPatchesX = (face.patchWidth  - 1) / 2;
        int numPatchesY = (face.patchHeight - 1) / 2;
        int triCount = 0;

        for (int py = 0; py < numPatchesY; py++) {
            for (int px = 0; px < numPatchesX; px++) {
                // Extract the 3x3 control point grid for this sub-patch
                BSPFile.Vertex[] ctrl = new BSPFile.Vertex[9];
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int ci = (py * 2 + row) * face.patchWidth + (px * 2 + col);
                        ctrl[row * 3 + col] = bsp.vertices[face.firstVertex + ci];
                    }
                }
                triCount += tessellateBezier3x3(ctrl, level, outVerts, outIndices);
            }
        }
        return triCount;
    }

    /**
     * Tessellate a single 3×3 bezier patch.
     * Uses de Casteljau evaluation at a uniform grid of (level+1)² sample points.
     */
    private int tessellateBezier3x3(BSPFile.Vertex[] ctrl, int level,
                                     java.util.List<float[]> outVerts,
                                     java.util.List<Integer> outIndices) {
        int steps = level + 1;
        int baseIndex = outVerts.size();

        // Generate (steps+1)² vertices
        for (int j = 0; j <= steps; j++) {
            float t = (float) j / steps;
            for (int i = 0; i <= steps; i++) {
                float s = (float) i / steps;
                outVerts.add(evalBezier(ctrl, s, t));
            }
        }

        // Generate triangles
        int triCount = 0;
        int stride = steps + 1;
        for (int j = 0; j < steps; j++) {
            for (int i = 0; i < steps; i++) {
                int a = baseIndex + j * stride + i;
                int b = a + 1;
                int c = a + stride;
                int d = c + 1;
                outIndices.add(a); outIndices.add(c); outIndices.add(b);
                outIndices.add(b); outIndices.add(c); outIndices.add(d);
                triCount += 2;
            }
        }
        return triCount;
    }

    /**
     * Evaluate a bicubic bezier patch at (s, t) using the 3×3 control grid.
     * Returns [x, y, z, u0, v0, u1, v1].
     */
    private float[] evalBezier(BSPFile.Vertex[] ctrl, float s, float t) {
        float s0 = (1 - s) * (1 - s), s1 = 2 * s * (1 - s), s2 = s * s;
        float t0 = (1 - t) * (1 - t), t1 = 2 * t * (1 - t), t2 = t * t;

        float x  = 0, y  = 0, z  = 0;
        float u0 = 0, v0 = 0, u1 = 0, v1 = 0;

        float[] sw = { s0, s1, s2 };
        float[] tw = { t0, t1, t2 };

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float w = sw[col] * tw[row];
                BSPFile.Vertex v = ctrl[row * 3 + col];
                x  += w * v.x;  y  += w * v.y;  z  += w * v.z;
                u0 += w * v.u0; v0 += w * v.v0;
                u1 += w * v.u1; v1 += w * v.v1;
            }
        }
        // Apply Q3 -> GL swizzle here: Q3(x,y,z) -> GL(x, z, -y)
        return new float[]{ x, z, -y, u0, v0, u1, v1 };
    }
}
