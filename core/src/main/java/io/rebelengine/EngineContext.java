package io.rebelengine;

import io.rebelengine.Instances.UI_TextInteraction;
import io.rebelengine.Helpers.Info_Entity;
import io.rebelengine.Debugging.DebugCamera;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import games.rednblack.miniaudio.MiniAudio;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Systems.PhysicsBodySystem;
import java.util.HashMap;

/**
 *
 */
public class EngineContext {
    private static EngineContext instance;
    private final RebelEngineBase instance_engine;
    private DebugCamera camera_debug_noclip;
    private final HashMap<Integer, Info_Entity.Trigger> triggers = new HashMap<>();
    private final HashMap<String, Array<Object>> group_components = new HashMap<>(); // variables and refs for the scripts and triggers
    private final HashMap<String, EntityScript> scripts = new HashMap<>();
    
    private final Array<UI_TextInteraction> text_Interactions = new Array<>();
    private MiniAudio instance_audio;
    
    public EngineContext (RebelEngineBase rblBase){
        instance_engine = rblBase;
    }
    
    public static EngineContext get(){
        return instance;
    }
    
    public void initialize(EngineContext gc){
        instance = gc;
    }
    
    public void set_audioEngine(MiniAudio audio){
        instance_audio = audio;
    }
    
    public MiniAudio AUDIO(){
        return instance_audio;
    }
    
    public BSPCollision COLLISION(){
        return instance_engine.BSP_COLLISION;
    }
    
    public void trace_ray(Entity owner, Vector3 start, Vector3 end, BSPCollision.TraceResult result){
        instance_engine.ASHLEY.getSystem(PhysicsBodySystem.class).traceBox(owner, start, end, Vector3.Zero, Vector3.Zero, result);
    }
    
    
    // camera logic 
    // could be changed to not need this anymore with focus input logic ?
    public void set_activeCamera(Camera cam){
        instance_engine.setCamera(cam);
    }
    public Camera get_activeCamera(){
        return instance_engine.camera;
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
    
    public void add_groupComponent(String key, Object component){
        group_components.computeIfAbsent(key, k -> new Array<>()).add(component);
    }
    
    public Array<Object> get_groupComponentsArray(String key){
        return group_components.get(key);
    }
    
    public void add_script(String key, EntityScript script){
        scripts.put(key, script);
    }
    
    public EntityScript get_script(String key){
        return scripts.get(key);
    }
    
    
    // ui stuff
    public void add_text_UI(UI_TextInteraction txt){
        text_Interactions.add(txt);
    }
    
    public Array<UI_TextInteraction> get_text_interactions(){
        return text_Interactions;
    }
}
