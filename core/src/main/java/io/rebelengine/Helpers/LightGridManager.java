package io.rebelengine.Helpers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;

public class LightGridManager {

    private static byte[] gridData;
    private static final Vector3 q3MinBounds = new Vector3();
    
    private static int gridW, gridH, gridD;
    private static final int stepX = 64, stepY = 64, stepZ = 128;
    private static final Color tmpColor = new Color();

    // Set to true to see grid cell toggles (Blue, Cyan, Yellow, etc.)
    // Set to false to use actual BSP lightgrid brightness
    public static boolean DEBUG_GRID_CELLS = false;

    public static void init(BSPFile bsp) {
        if (bsp.lightGrid == null || bsp.models == null || bsp.models.length == 0) {
            System.err.println("LightGridManager: No lightGrid data found in BSP!");
            return;
        }

        gridData = bsp.lightGrid;
        
        float minX = bsp.models[0].minX;
        float minY = bsp.models[0].minY;
        float minZ = bsp.models[0].minZ;
        
        float maxX = bsp.models[0].maxX;
        float maxY = bsp.models[0].maxY;
        float maxZ = bsp.models[0].maxZ;

        // Snapping grid bounds to Q3 lightgrid step dimensions
        q3MinBounds.x = stepX * (float) Math.floor(minX / stepX);
        q3MinBounds.y = stepY * (float) Math.floor(minY / stepY);
        q3MinBounds.z = stepZ * (float) Math.floor(minZ / stepZ);

        float q3MaxX = stepX * (float) Math.ceil(maxX / stepX);
        float q3MaxY = stepY * (float) Math.ceil(maxY / stepY);
        float q3MaxZ = stepZ * (float) Math.ceil(maxZ / stepZ);

        gridW = (int) Math.max(1, Math.round((q3MaxX - q3MinBounds.x) / stepX));
        gridH = (int) Math.max(1, Math.round((q3MaxY - q3MinBounds.y) / stepY));
        gridD = (int) Math.max(1, Math.round((q3MaxZ - q3MinBounds.z) / stepZ));
    }

    public static Color getAmbientAt(float px, float py, float pz) {
        if (gridData == null || gridData.length == 0) return Color.WHITE;

        // 1. LibGDX World Space -> Q3 Coordinate Space
        float q3X = px;
        float q3Y = -pz; 
        float q3Z = py;

        // 2. Offsets from grid origin
        float dx = q3X - q3MinBounds.x;
        float dy = q3Y - q3MinBounds.y;
        float dz = q3Z - q3MinBounds.z;

        // 3. Grid coordinates
        int gx = MathUtils.clamp((int) (dx / stepX), 0, gridW - 1);
        int gy = MathUtils.clamp((int) (dy / stepY), 0, gridH - 1);
        int gz = MathUtils.clamp((int) (dz / stepZ), 0, gridD - 1);

        // --- DEBUG MODE: VISUALIZE GRID CELL CROSSINGS ---
        if (DEBUG_GRID_CELLS) {
            float r = (gx % 2 == 0) ? 1.0f : 0.0f;
            float g = (gy % 2 == 0) ? 1.0f : 0.0f;
            float b = (gz % 2 == 0) ? 1.0f : 0.0f;
            tmpColor.set(r, g, b, 1.0f);
            return tmpColor;
        }

        // 4. Compute 1D array index
        int sampleIndex = gx + (gy * gridW) + (gz * gridW * gridH);
        int rawOffset = sampleIndex * 8;

        // HARD SAFETY CLAMP: Guarantees no Pink out-of-bounds flashes ever happen
        int maxOffset = Math.max(0, gridData.length - 8);
        int offset = MathUtils.clamp(rawOffset, 0, maxOffset);

        // 5. Unpack raw ambient & directional bytes (8 bytes per voxel)
        float ambR = Byte.toUnsignedInt(gridData[offset]) / 255f;
        float ambG = Byte.toUnsignedInt(gridData[offset + 1]) / 255f;
        float ambB = Byte.toUnsignedInt(gridData[offset + 2]) / 255f;

        float dirR = Byte.toUnsignedInt(gridData[offset + 3]) / 255f;
        float dirG = Byte.toUnsignedInt(gridData[offset + 4]) / 255f;
        float dirB = Byte.toUnsignedInt(gridData[offset + 5]) / 255f;

        // 6. Ambient Brightness Sum + Overbright Multiplier
        float MULTIPLIER = 2.5f;
        float finalR = MathUtils.clamp((ambR + dirR) * MULTIPLIER, 0.05f, 1.0f);
        float finalG = MathUtils.clamp((ambG + dirG) * MULTIPLIER, 0.05f, 1.0f);
        float finalB = MathUtils.clamp((ambB + dirB) * MULTIPLIER, 0.05f, 1.0f);

        tmpColor.set(finalR, finalG, finalB, 1.0f);
        return tmpColor;
    }
}