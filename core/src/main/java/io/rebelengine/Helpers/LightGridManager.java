package io.rebelengine.Helpers;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;

/**
 * TODO: Fix this whole thing and also on DYNRenderer for lightgrid ligthing on dyn objs
 * This is basically a work around to make sure no light is completely black. by setting py+100 and also floor max color threshold
 * The lightgrid needs to be refactored correctly, do a grid, sample from around to make sure the color is correct. maybe lower the grid or make grids even smaller 
 * @author me
 */
public class LightGridManager {

    private static byte[] gridData;
    private static final Vector3 q3MinBounds = new Vector3();
    
    private static int gridW, gridH, gridD;
    private static final int bytesPerCell = 8;
    
    // Standard Q3 Lightgrid cell sizes
    private static final float stepX = 64.0f;
    private static final float stepY = 64.0f;
    private static final float stepZ = 128.0f;
    
    public static Vector3 ambient_offset = new Vector3(0.07f, 0.07f, 0.07f);

    public static class GridSample {
        public final Vector3 ambient = new Vector3(0.1f, 0.1f, 0.1f);
        public final Vector3 directed = new Vector3(0.1f, 0.1f, 0.1f);
        public final Vector3 lightDir = new Vector3(0f, 1f, 0f);
    }

    // Reusable scratch objects for trilinear interpolation blending
    private static final GridSample sample000 = new GridSample();
    private static final GridSample sample100 = new GridSample();
    private static final GridSample sample010 = new GridSample();
    private static final GridSample sample110 = new GridSample();
    private static final GridSample sample001 = new GridSample();
    private static final GridSample sample101 = new GridSample();
    private static final GridSample sample011 = new GridSample();
    private static final GridSample sample111 = new GridSample();

    public static void init(BSPFile bsp) {
        if (bsp.lightGrid == null || bsp.models == null || bsp.models.length == 0) {
            System.err.println("LightGridManager ERROR: No lightGrid data found in BSP!");
            return;
        }

        gridData = bsp.lightGrid;

        float minX = bsp.models[0].minX;
        float minY = bsp.models[0].minY;
        float minZ = bsp.models[0].minZ;
        
        float maxX = bsp.models[0].maxX;
        float maxY = bsp.models[0].maxY;
        float maxZ = bsp.models[0].maxZ;

        q3MinBounds.x = stepX * (float) Math.ceil(minX / stepX);
        q3MinBounds.y = stepY * (float) Math.ceil(minY / stepY);
        q3MinBounds.z = stepZ * (float) Math.ceil(minZ / stepZ);

        float q3MaxX = stepX * (float) Math.floor(maxX / stepX);
        float q3MaxY = stepY * (float) Math.floor(maxY / stepY);
        float q3MaxZ = stepZ * (float) Math.floor(maxZ / stepZ);

        // Q3Map2 grid cell dimensions (+1 is required by Q3Map2)
        gridW = (int) ((q3MaxX - q3MinBounds.x) / stepX + 1);
        gridH = (int) ((q3MaxY - q3MinBounds.y) / stepY + 1);
        gridD = (int) ((q3MaxZ - q3MinBounds.z) / stepZ + 1);

        int totalExpectedCells = gridW * gridH * gridD;
        int totalActualCells = gridData.length / bytesPerCell;

        System.out.println("--- LIGHTGRID BOUNDS ---");
        System.out.println("BSP Model 0 Bounds: Min(" + minX + ", " + minY + ", " + minZ + ") Max(" + maxX + ", " + maxY + ", " + maxZ + ")");
        System.out.println("Grid Dimensions: W=" + gridW + " (X), H=" + gridH + " (Y), D=" + gridD + " (Z)");
        System.out.println("Total Expected Bytes: " + (totalExpectedCells * bytesPerCell) + " | Actual Grid Bytes: " + gridData.length);
        System.out.println("Expected Cells: " + totalExpectedCells + " | Actual Cells in Byte Buffer: " + totalActualCells);
        System.out.println("q3MinBounds: " + q3MinBounds);
    }

    private static void fetchCellSample(int gx, int gy, int gz, GridSample out) {
        gx = MathUtils.clamp(gx, 0, gridW - 1);
        gy = MathUtils.clamp(gy, 0, gridH - 1);
        gz = MathUtils.clamp(gz, 0, gridD - 1);

        // Q3Map2 memory layout stride: X -> Z -> Y (W * H * D layout: gx + gy*gridW + gz*gridW*gridH)
        int sampleIndex = gx + (gy * gridW) + (gz * gridW * gridH);
        int offset = sampleIndex * bytesPerCell;

        if (offset < 0 || offset + bytesPerCell > gridData.length) {
            out.ambient.set(0.1f, 0.1f, 0.1f);
            out.directed.set(0.1f, 0.1f, 0.1f);
            out.lightDir.set(0f, 1f, 0f);
            return;
        }

        // Unpack Ambient RGB
        float ambR = Byte.toUnsignedInt(gridData[offset])     / 255.0f;
        float ambG = Byte.toUnsignedInt(gridData[offset + 1]) / 255.0f;
        float ambB = Byte.toUnsignedInt(gridData[offset + 2]) / 255.0f;

        // Unpack Directed RGB
        float dirR = Byte.toUnsignedInt(gridData[offset + 3]) / 255.0f;
        float dirG = Byte.toUnsignedInt(gridData[offset + 4]) / 255.0f;
        float dirB = Byte.toUnsignedInt(gridData[offset + 5]) / 255.0f;

        out.ambient.set(ambR, ambG, ambB);
        out.directed.set(dirR, dirG, dirB);

        // Unpack Spherical Light Direction
        int phiByte   = Byte.toUnsignedInt(gridData[offset + 6]);
        int thetaByte = Byte.toUnsignedInt(gridData[offset + 7]);

        float phi   = phiByte   * (MathUtils.PI / 255.0f);
        float theta = thetaByte * (MathUtils.PI2 / 255.0f);

        float q3Lx = MathUtils.sin(phi) * MathUtils.cos(theta);
        float q3Ly = MathUtils.sin(phi) * MathUtils.sin(theta);
        float q3Lz = MathUtils.cos(phi);

        out.lightDir.set(q3Lx, q3Lz, -q3Ly).nor();
    }

    public static void sampleAt(float px, float py, float pz, GridSample outSample) {
        if (gridData == null || gridData.length == 0) return;

        // LibGDX -> Q3 Axis Mapping
        float q3X = px;
        float q3Y = -pz;
        float q3Z = py + 100f;

        float dx = (q3X - q3MinBounds.x) / stepX;
        float dy = (q3Y - q3MinBounds.y) / stepY;
        float dz = (q3Z - q3MinBounds.z) / stepZ;

        int gx = (int) Math.floor(dx);
        int gy = (int) Math.floor(dy);
        int gz = (int) Math.floor(dz);

        float fx = dx - gx;
        float fy = dy - gy;
        float fz = dz - gz;

        // Fetch 8 surrounding corner cells for trilinear interpolation (Quake 3 style)
        fetchCellSample(gx,     gy,     gz,     sample000);
        fetchCellSample(gx + 1, gy,     gz,     sample100);
        fetchCellSample(gx,     gy + 1, gz,     sample010);
        fetchCellSample(gx + 1, gy + 1, gz,     sample110);
        fetchCellSample(gx,     gy,     gz + 1, sample001);
        fetchCellSample(gx + 1, gy,     gz + 1, sample101);
        fetchCellSample(gx,     gy + 1, gz + 1, sample011);
        fetchCellSample(gx + 1, gy + 1, gz + 1, sample111);

        // Trilinear interpolation across X (fx)
        // Bottom-Z plane
        Vector3 amb00 = lerpVec(sample000.ambient, sample100.ambient, fx);
        Vector3 dir00 = lerpVec(sample000.directed, sample100.directed, fx);
        Vector3 lDir00 = lerpVec(sample000.lightDir, sample100.lightDir, fx);

        Vector3 amb10 = lerpVec(sample010.ambient, sample110.ambient, fx);
        Vector3 dir10 = lerpVec(sample010.directed, sample110.directed, fx);
        Vector3 lDir10 = lerpVec(sample010.lightDir, sample110.lightDir, fx);

        // Top-Z plane
        Vector3 amb01 = lerpVec(sample001.ambient, sample101.ambient, fx);
        Vector3 dir01 = lerpVec(sample001.directed, sample101.directed, fx);
        Vector3 lDir01 = lerpVec(sample001.lightDir, sample101.lightDir, fx);

        Vector3 amb11 = lerpVec(sample011.ambient, sample111.ambient, fx);
        Vector3 dir11 = lerpVec(sample011.directed, sample111.directed, fx);
        Vector3 lDir11 = lerpVec(sample011.lightDir, sample111.lightDir, fx);

        // Interpolate across Y (fy)
        Vector3 amb0 = lerpVec(amb00, amb10, fy);
        Vector3 dir0 = lerpVec(dir00, dir10, fy);
        Vector3 lDir0 = lerpVec(lDir00, lDir10, fy);

        Vector3 amb1 = lerpVec(amb01, amb11, fy);
        Vector3 dir1 = lerpVec(dir01, dir11, fy);
        Vector3 lDir1 = lerpVec(lDir01, lDir11, fy);

        // Interpolate across Z (fz)
        outSample.ambient.set(lerpVec(amb0, amb1, fz));
        outSample.directed.set(lerpVec(dir0, dir1, fz));
        outSample.lightDir.set(lerpVec(lDir0, lDir1, fz)).nor();

        // Quake 3 Minimum Ambient Floor (prevents models from pitching to absolute black inside geometry/shadows)
        float minAmbientFloor = 0.07f;
        float minDirectedFloor = 0.02f;
        outSample.ambient.x = Math.max(outSample.ambient.x, minAmbientFloor); // R
        outSample.ambient.y = Math.max(outSample.ambient.y, minAmbientFloor); // G
        outSample.ambient.z = Math.max(outSample.ambient.z, minAmbientFloor); // B
        
        outSample.directed.x = Math.max(outSample.directed.x, minDirectedFloor);
        outSample.directed.y = Math.max(outSample.directed.y, minDirectedFloor);
        outSample.directed.z = Math.max(outSample.directed.z, minDirectedFloor);
    }

    private static Vector3 lerpVec(Vector3 v1, Vector3 v2, float alpha) {
        return new Vector3(
            v1.x + (v2.x - v1.x) * alpha,
            v1.y + (v2.y - v1.y) * alpha,
            v1.z + (v2.z - v1.z) * alpha
        );
    }
    
    public static void debugRender(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer) {
        if (gridData == null || gridData.length == 0) return;

        shapeRenderer.setColor(0.0f, 1.0f, 0.0f, 1.0f);

        for (int gz = 0; gz < gridD; gz++) {
            for (int gy = 0; gy < gridH; gy++) {
                for (int gx = 0; gx < gridW; gx++) {
                    int sampleIndex = gx + (gy * gridW) + (gz * gridW * gridH);
                    int offset = sampleIndex * bytesPerCell;

                    if (offset < 0 || offset + bytesPerCell > gridData.length) continue;

                    int ambR = Byte.toUnsignedInt(gridData[offset]);
                    int ambG = Byte.toUnsignedInt(gridData[offset + 1]);
                    int ambB = Byte.toUnsignedInt(gridData[offset + 2]);
                    if (ambR == 0 && ambG == 0 && ambB == 0) continue;

                    float q3X = q3MinBounds.x + gx * stepX;
                    float q3Y = q3MinBounds.y + gy * stepY;
                    float q3Z = q3MinBounds.z + gz * stepZ;

                    float xMin = q3X;
                    float xMax = q3X + stepX;

                    float yMin = q3Z - 128.0f;
                    float yMax = q3Z + stepZ - 128.0f;

                    float zMax = -q3Y;
                    float zMin = -(q3Y + stepY);

                    // Bottom rectangle
                    shapeRenderer.line(xMin, yMin, zMin, xMax, yMin, zMin);
                    shapeRenderer.line(xMax, yMin, zMin, xMax, yMin, zMax);
                    shapeRenderer.line(xMax, yMin, zMax, xMin, yMin, zMax);
                    shapeRenderer.line(xMin, yMin, zMax, xMin, yMin, zMin);

                    // Top rectangle
                    shapeRenderer.line(xMin, yMax, zMin, xMax, yMax, zMin);
                    shapeRenderer.line(xMax, yMax, zMin, xMax, yMax, zMax);
                    shapeRenderer.line(xMax, yMax, zMax, xMin, yMax, zMax);
                    shapeRenderer.line(xMax, yMax, zMax, xMin, yMax, zMin);

                    // Connecting vertical pillars
                    shapeRenderer.line(xMin, yMin, zMin, xMin, yMax, zMin);
                    shapeRenderer.line(xMax, yMin, zMin, xMax, yMax, zMin);
                    shapeRenderer.line(xMax, yMin, zMax, xMax, yMax, zMax);
                    shapeRenderer.line(xMin, yMin, zMax, xMin, yMax, zMax);
                }
            }
        }
    }
}