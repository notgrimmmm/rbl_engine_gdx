package io.rebelengine.Helpers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;

public class LightGridManager {

    // Data from the BSP
    private static byte[] gridData;
    private static Vector3 minBounds = new Vector3();
    
    // Grid dimensions
    private static int gridW, gridH, gridD;
    private static int stepX = 64, stepY = 64, stepZ = 128;

    /**
     * Initializes the manager with data from the loaded BSP.
     * Call this right after BSPLoader.load() finishes.
     */
    public static void init(BSPFile bsp) {
        gridData = bsp.lightGrid;
        minBounds.set(bsp.models[0].minX, bsp.models[0].minY, bsp.models[0].minZ);

        // Calculate counts based on map size / steps
        gridW = (int) Math.ceil((bsp.models[0].maxX - bsp.models[0].minX) / stepX) + 1;
        gridH = (int) Math.ceil((bsp.models[0].maxY - bsp.models[0].minY) / stepY) + 1;
        gridD = (int) Math.ceil((bsp.models[0].maxZ - bsp.models[0].minZ) / stepZ) + 1;

        //System.out.println("LightGridManager: Grid dimensions " + gridW + "x" + gridH + "x" + gridD);
    }

    private static final Color tmpColor = new Color();
    /**
     * The main "Question" any entity can ask.
     * @return A Color containing Ambient RGB in (r, g, b)
     */
    public static Color getAmbientAt(float px, float py, float pz) {
        if (gridData == null) return Color.WHITE;

        // 1. Localize position to grid start
        float dx = px - minBounds.x;
        float dy = py - minBounds.y;
        float dz = pz - minBounds.z;

        // 2. Find cell indices
        int gx = MathUtils.clamp((int) (dx / stepX), 0, gridW - 1);
        int gy = MathUtils.clamp((int) (dy / stepY), 0, gridH - 1);
        int gz = MathUtils.clamp((int) (dz / stepZ), 0, gridD - 1);

        // 3. 3D to 1D Index (Standard Q3 logic)
        int sampleIndex = gx + (gy * gridW) + (gz * gridW * gridH);
        int offset = sampleIndex * 8;

        if (offset + 2 >= gridData.length) return Color.BLACK;

        // 4. Convert bytes to 0.0-1.0 float
        // Inside your getAmbientAt method
        // Scale it up by 16x or 32x to move it into a visible range
        float r = (Byte.toUnsignedInt(gridData[offset]) / 255f) * 16.0f;
        float g = (Byte.toUnsignedInt(gridData[offset + 1]) / 255f) * 16.0f;
        float b = (Byte.toUnsignedInt(gridData[offset + 2]) / 255f) * 16.0f;
        
        tmpColor.set(r, g, b, 1f);
        return tmpColor;
    }
}