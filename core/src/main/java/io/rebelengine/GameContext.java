package io.rebelengine;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import java.util.HashMap;

/**
 *
 */
public class GameContext {
    private static GameContext instance;
    private final RebelEngineBase instance_engine;
    private DebugCamera camera_debug_noclip;
    private HashMap<Integer, Info_Entity.Trigger> triggers = new HashMap<>();
    
    public GameContext (RebelEngineBase rblBase){
        instance_engine = rblBase;
    }
    
    public static GameContext get(){
        return instance;
    }
    
    public void initialize(GameContext gc){
        instance = gc;
    }
    
    public BSPCollision COLLISION(){
        return instance_engine.BSP_COLLISION;
    }
    
    
    // camera logic 
    // could be changed to not need this anymore with focus input logic ?
    public void set_activeCamera(Camera cam){
        instance_engine.setCamera(cam);
    }
    
    public void set_noclip_camera(Vector3 origin){
        camera_debug_noclip = new DebugCamera(origin);
    }
    
    public Camera get_noclip_camera(){
        return camera_debug_noclip;
    }
    
    /**
     * Logic for info entities that only contain information to use
     */
    public void add_trigger(int key, Info_Entity.Trigger value){
        triggers.put(key, value);
    }
    
    public Info_Entity.Trigger get_trigger(int key){
        return triggers.get(key);
    }
}
