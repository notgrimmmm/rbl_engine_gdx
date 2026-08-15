package io.rebelengine.Helpers;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPFile;
import io.rebelengine.DYNRenderer;

/**
 * engine helper utilities
 */
public class Utils_Misc {
    /**
     * @param s origin property
     * @return - vector3 with opengl world axis coordindates xyz
     */
    public static Vector3 parseVector3(String s) {
        String[] p = s.split(" ");
        return new Vector3(Float.parseFloat(p[0]), Float.parseFloat(p[2]), -Float.parseFloat(p[1]));
    }
    
    /**
     * @param s origin property
     * @return - vector3 from with trenchbroom world axis coordinates xzy
     */
    public static Vector3 parseVec3TB(String s) {
        String[] p = s.split(" ");
        return new Vector3(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]));
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
    
    public static DYNRenderer.RenderInstance createGlbRenderInstance(String key, Model glbModel, Vector3 position, Vector3 rotation, DYNRenderer renderer) {
        // 1. Pass GLB Model meshes into DYNRenderer to build BrushBatch entries
        renderer.addGlbModel(key, glbModel);

        // 2. Create the RenderInstance
        DYNRenderer.RenderInstance instance = new DYNRenderer.RenderInstance();
        instance.meshKey = key;
        instance.bmIndex = -10; // Negative index identifies it as external/GLB model
        instance.position.set(position);
        instance.rotation.set(rotation);

        return instance;
    }
}
