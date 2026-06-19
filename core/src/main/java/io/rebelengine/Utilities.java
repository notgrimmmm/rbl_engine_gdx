package io.rebelengine;

import com.badlogic.gdx.math.Vector3;

/**
 * engine helper utilities
 */
public class Utilities {
    public static Vector3 parseVector3(String s) {
        String[] p = s.split(" ");
        return new Vector3(Float.parseFloat(p[0]), Float.parseFloat(p[2]), -Float.parseFloat(p[1]));
    }
    
    public static DynamicRenderer.RenderInstance createRenderInstance(String model, DynamicRenderer renderer, BSPFile BSP){
        String brushModelKey = model;
        int bmIndex = Integer.parseInt(brushModelKey.substring(1));

        BSPFile.Model bModel = BSP.models[bmIndex];
        renderer.addBrush(brushModelKey, bModel, BSP);
        DynamicRenderer.RenderInstance instance = new DynamicRenderer.RenderInstance();
        instance.meshKey = brushModelKey;
        instance.bmIndex = bmIndex;
        instance.position.set(DynamicRenderer.getModelCenter(bModel));
        
        return instance;
    }
}
