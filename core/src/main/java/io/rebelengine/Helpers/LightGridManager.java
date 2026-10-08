package io.rebelengine.Helpers;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;

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
        public final Vector3 ambient = new Vector3(0.2f, 0.2f, 0.2f);
        public final Vector3 directed = new Vector3(0.8f, 0.8f, 0.8f);
        public final Vector3 lightDir = new Vector3(0f, 1f, 0f);
    }

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

    public static void sampleAt(float px, float py, float pz, GridSample outSample) {
        if (gridData == null || gridData.length == 0) return;

        // LibGDX -> Q3 Axis Mapping
        float q3X = px;
        float q3Y = -pz;
        float q3Z = py + 16.0f;

        float dx = q3X - q3MinBounds.x;
        float dy = q3Y - q3MinBounds.y;
        float dz = q3Z - q3MinBounds.z;

        int gx = MathUtils.clamp((int) Math.floor(dx / stepX), 0, gridW - 1);
        int gy = MathUtils.clamp((int) Math.floor(dy / stepY), 0, gridH - 1);
        int gz = MathUtils.clamp((int) Math.floor(dz / stepZ), 0, gridD - 1);

        // Q3Map2 memory layout stride: X -> Z -> Y
        int sampleIndex = gx + (gy * gridW) + (gz * gridW * gridH);
        int offset = sampleIndex * bytesPerCell;


        if (offset < 0 || offset + 8 > gridData.length) {
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

        outSample.ambient.set(ambR, ambG, ambB);
        outSample.directed.set(dirR, dirG, dirB);

        // Unpack Spherical Light Direction
        int phiByte   = Byte.toUnsignedInt(gridData[offset + 6]);
        int thetaByte = Byte.toUnsignedInt(gridData[offset + 7]);

        float phi   = phiByte   * (MathUtils.PI / 255.0f);
        float theta = thetaByte * (MathUtils.PI2 / 255.0f);

        float q3Lx = MathUtils.sin(phi) * MathUtils.cos(theta);
        float q3Ly = MathUtils.sin(phi) * MathUtils.sin(theta);
        float q3Lz = MathUtils.cos(phi);

        outSample.lightDir.set(q3Lx, q3Lz, -q3Ly).nor();
    }
}