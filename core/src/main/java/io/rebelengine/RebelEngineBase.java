/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.rebelengine.Components.EngineInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * The base engine plus utilities
 */
public class RebelEngineBase implements Disposable{
    private static final float FIXED_STEP = 1f / 60f;
    private float accumulator = 0f;
    
    //entitiy
    public final Engine ASHLEY;
    
    //managers
    
    //unified shader
    public EngineShader SHADER;
    //bsp
    public BSPRenderer  BSP_RENDERER;
    public BSPTree      BSP_TREE;
    public BSPFile      BSP;
    
    //bsp physics
    public BSPCollision BSP_COLLISION;
    
    //dyanmic render
    public DynamicRenderer DYN_RENDERER;
    
    //camera - the active render camera, can be replaced 
    public Camera camera;
    
    //inputs
    public InputMultiplexer inputMultiplexer;
    
    BasePlayerControl player;
    
    EngineInput input;
    
    public RebelEngineBase() {
        this.ASHLEY = new Engine();
        this.SHADER = new EngineShader();
        this.inputMultiplexer = new InputMultiplexer();
        //create game context when initializing the engine to add, remove, edit, anything during runtime
        
        //make a debug camera class and init the camera class
        
        
        //initialize and add all managers to ASHLEY
            // make sure you can pass a settings 'file' from game to the engine to edit sound, brightness, graphics, controls, etc

        //make a camera here as main (it can bet set to any camera for rendering)
        camera = new PerspectiveCamera(90,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight());
        camera.near = 16f;
        camera.far  = 4096f;
        camera.update();
    }
    
    /**
     * Load a BSP map and initialize all BSP systems.
     * Call this from show() in your game screen.
     *
     * @param mapPath     internal asset path to the .bsp file
     * @param textureRoot internal asset path to the texture root directory
     */
    public void loadMap(String mapPath, String textureRoot) {
        try {
            FileHandle fh  = Gdx.files.internal(mapPath);
            byte[]     raw = fh.readBytes();
            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            BSP = BSPLoader.load(buf);
            LightGridManager.init(BSP); // control light grid ambient
            
            System.out.println("Lightmaps: " + BSP.lightmaps.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BSP: " + mapPath + " — " + e);
        }
 
        BSP_TREE     = new BSPTree(BSP);
        BSP_RENDERER = new BSPRenderer(BSP, SHADER);
        BSP_RENDERER.buildGeometry(textureRoot);
        
        BSP_COLLISION = new BSPCollision(BSP);
        
        DYN_RENDERER = new DynamicRenderer(SHADER, textureRoot);
        
        
        //ashley setup
//        input = new EngineInput();
//        ASHLEY.addSystem(new SystemsBag.ControlSystem(input));
//        ASHLEY.addSystem(new SystemsBag.bspPhysicsSystem(BSP_COLLISION));
//        ASHLEY.addSystem(new SystemsBag.RenderSyncSystem(camera));
        
 
        // Parse the entities[] from the bsp file
        BSPEntities parsedEntities = BSPEntityParser.parse(BSP.entities);
        
        // debugging ent
        BSPEntities.Entity debugEnt = parsedEntities.first("debug");
        if (debugEnt != null) {
            Vector3 depos = Utilities.parseVector3(debugEnt.get("origin"));
            
            DebugCamera dcam = new DebugCamera(depos);
            //setCamera(dcam);
        }

        
        // player ent TODO: set it to one only
        BSPEntities.Entity playerEnt = parsedEntities.first("info_player_start");
        if (playerEnt != null) {
            Vector3 ppos = Utilities.parseVector3(playerEnt.get("origin"));
            
            player = new BasePlayerControl(BSP_COLLISION, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            player.setSpawnPosition(ppos.x, ppos.y, ppos.z);
            setCamera(player.getCamera());
        }
        
        // function brush entities
        List<BSPEntities.Entity> funcEnts = parsedEntities.byClass.get("func_system");
        if (funcEnts != null) {
            for (BSPEntities.Entity funcEnt : funcEnts){
                String brushModelKey = funcEnt.get("model");
                int bmIndex = Integer.parseInt(brushModelKey.substring(1));
                
                BSPFile.Model bModel = BSP.models[bmIndex];
                //System.out.println(bModel + " - " + bModel.firstFace);
                DYN_RENDERER.addBrush(brushModelKey, bModel, BSP);
                // 2. Create the "Instance" (The actual object in the world)
                DynamicRenderer.RenderInstance inst = new DynamicRenderer.RenderInstance();
                inst.meshKey = brushModelKey;
                DYN_RENDERER.instances.add(inst);
                
            }
        }
    }
    
    public void render(float delta){
        //separate into delta and alpha for use in physics interpolation and fixedtime

        //render everything and cull where necessary
        if (BSP_RENDERER == null || camera == null) return;
        
        //update main camera render here
        camera.update();
        steps(delta);
        
        // Convert GL camera position to Q3 coords for PVS lookup
        // GL(x, y, z) -> Q3(x, -z, y)
        float q3x =  camera.position.x;
        float q3y = -camera.position.z;
        float q3z =  camera.position.y;
        
        SHADER.clearLights();
        //SHADER.addSpotLight(player.getCamera().position, 500f, camera.direction, Color.WHITE, 30f);
        SHADER.bind(); 

        BSP_RENDERER.render(camera, q3x, q3y, q3z);
        DYN_RENDERER.render(camera); //already contains instances added
    }
    
    /**
     * 
     * @param delta - use for lights and animations
     *        alpha - use for interpolating physics
     *   fixed_step - use for any physcis movement
     */
    public void steps(float delta){
        accumulator += delta;
        while (accumulator >= FIXED_STEP){
            accumulator -= FIXED_STEP;
            if (player != null) player.updatePhysics(FIXED_STEP);
        }
        
        float alpha = accumulator / FIXED_STEP;
        player.update(alpha);
    }
    
    /**
    * Call when the viewport changes (window resize).
    */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.viewportWidth  = width;
        camera.viewportHeight = height;
        camera.update();
    }
    
    public void setCamera(Camera cam){
        this.camera = cam;
    }
    
    @Override
    public void dispose() {
        if (BSP_RENDERER != null)   BSP_RENDERER.dispose();
        if (SHADER != null)         SHADER.dispose();
    }
}
