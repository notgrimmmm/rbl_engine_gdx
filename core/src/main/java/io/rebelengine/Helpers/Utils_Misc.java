package io.rebelengine.Helpers;

import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;
import io.rebelengine.DYNRenderer;

/**
 * engine helper utilities
 */
public class Utils_Misc {
    public static Vector3 parseVector3(String s) {
        String[] p = s.split(" ");
        return new Vector3(Float.parseFloat(p[0]), Float.parseFloat(p[2]), -Float.parseFloat(p[1]));
    }
    
    public static DYNRenderer.RenderInstance createRenderInstance(String model, DYNRenderer renderer, BSPFile BSP){
        String brushModelKey = model;
        int bmIndex = Integer.parseInt(brushModelKey.substring(1));

        BSPFile.Model bModel = BSP.models[bmIndex];
        renderer.addBrush(brushModelKey, bModel, BSP);
        DYNRenderer.RenderInstance instance = new DYNRenderer.RenderInstance();
        instance.meshKey = brushModelKey;
        instance.bmIndex = bmIndex;
        instance.position.set(DYNRenderer.getModelCenter(bModel));
        
        return instance;
    }
}
