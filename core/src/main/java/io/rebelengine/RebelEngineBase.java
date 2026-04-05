/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

/**
 * The base engine plus utilities
 */
public class RebelEngineBase implements Disposable{
    
    //entitiy
    public final Engine ASHLEY;
    
    //managers
    
    //bsp
    public BSPRenderer BSP_RENDERER;
    public BSPTree     BSP_TREE;
    public BSPFile     BSP_FILE;
    
    //camera - the active render camera, can be replaced 
    public PerspectiveCamera camera;
    
    //inputs
    public InputMultiplexer inputMultiplexer;
    
    public RebelEngineBase() {
        this.ASHLEY = new Engine();
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
            BSP_FILE = BSPLoader.load(buf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BSP: " + mapPath + " — " + e);
        }
 
        BSP_TREE     = new BSPTree(BSP_FILE);
        BSP_RENDERER = new BSPRenderer(BSP_FILE);
        BSP_RENDERER.buildGeometry(textureRoot);
 
        // Parse the entities[] from the bsp file
        BSPEntities parsedEntities = BSPEntityParser.parse(BSP_FILE.entities);
        // debug print System.out.println(BSP_FILE.entities);
        
        // debugging ent
        BSPEntities.Entity debugEnt = parsedEntities.byClass.get("debug").getFirst();
        if (debugEnt != null) {
            Vector3 depos = Utilities.parseVector3(debugEnt.properties.get("origin"));
            
            DebugCamera dcam = new DebugCamera(depos);
            setCamera(dcam);
        }

        
        // player ent TODO: set it to one only
        List<BSPEntities.Entity> pEnts = parsedEntities.byClass.get("info_player_start");
        if (pEnts != null) {
            BSPEntities.Entity playerEnt = pEnts.getFirst();
        }
        
        // function brush entities
        List<BSPEntities.Entity> funcEnts = parsedEntities.byClass.get("func_system");
        if (funcEnts != null) {
            for (BSPEntities.Entity funcEnt : funcEnts){
                String brushModelKey = funcEnt.get("model");
                int bmIndex = Integer.parseInt(brushModelKey.substring(1));
                
                BSPFile.Model bModel = BSP_FILE.models[bmIndex];
                System.out.println(bModel);
            }
        }
    }
    
    public void render(float delta){
        //separate into delta and alpha for use in physics interpolation and fixedtime

        //render everything and cull where necessary
        if (BSP_RENDERER == null || camera == null) return;
        
        //update main camera render here
        camera.update();

        // Convert GL camera position to Q3 coords for PVS lookup
        // GL(x, y, z) -> Q3(x, -z, y)
        float q3x =  camera.position.x;
        float q3y = -camera.position.z;
        float q3z =  camera.position.y;

        BSP_RENDERER.render(camera, q3x, q3y, q3z);
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
    
    public void setCamera(PerspectiveCamera cam){
        this.camera = cam;
    }
    
    @Override
    public void dispose() {
        if (BSP_RENDERER != null) BSP_RENDERER.dispose();
    }
}
