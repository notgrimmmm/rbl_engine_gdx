/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.game.PlayerScriptTest;
import io.rebelengine.Commands.InfoBodiesCommand;

import io.rebelengine.Systems.CameraSyncSystem;
import io.rebelengine.Systems.PlayerFrameInputSystem;
import io.rebelengine.Systems.PhysicsBodySystem;
import io.rebelengine.Systems.PlayerLocomotionSystem;
import io.rebelengine.Systems.TransformInterpolationSystem;

import io.rebelengine.Commands.StatsCommand;
import io.rebelengine.Commands.NoClipCommand;
import io.rebelengine.Components.ScriptComponent;
import io.rebelengine.Systems.ScriptingSystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * The base engine plus utilities
 */
public class RebelEngineBase implements Disposable{
    private static final float FIXED_STEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f;
    private static final float ALPHA_EPSILON = 0.0001f;

    private float accumulator = 0f;

    public final RebelAshleyEngine ASHLEY;

    public EngineShader SHADER;
    public BSPRenderer  BSP_RENDERER;
    public BSPTree      BSP_TREE;
    public BSPFile      BSP;

    public BSPCollision BSP_COLLISION;
    public DynamicRenderer DYN_RENDERER;

    public Camera camera;

    private final InputMultiplexer inputMultiplexer;
    private final DebuggingConsole debuggingConsole;
    private final EngineFrameInputProcessor frameInputProcessor;
    private final PlayerFrameInputSystem playerFrameInputSystem;
    private final PlayerLocomotionSystem playerLocomotionSystem;
    private final PhysicsBodySystem physicsBodySystem;
    private final TransformInterpolationSystem transformInterpolationSystem;
    private final CameraSyncSystem cameraSyncSystem;
    private final ScriptingSystem scriptingSystem;
    
    private BasePlayer player_base;
    
    public RebelEngineBase() {
        GameContext gameContext = new GameContext(this);
        gameContext.initialize(gameContext);
        
        this.ASHLEY = new RebelAshleyEngine();
        this.SHADER = new EngineShader();
        this.debuggingConsole = new DebuggingConsole(this);
        
        this.inputMultiplexer = new InputMultiplexer();
        this.frameInputProcessor = new EngineFrameInputProcessor(ASHLEY);
        
        this.playerFrameInputSystem = new PlayerFrameInputSystem();
        this.playerLocomotionSystem = new PlayerLocomotionSystem();
        
        this.physicsBodySystem = new PhysicsBodySystem();
        this.transformInterpolationSystem = new TransformInterpolationSystem();
        this.cameraSyncSystem = new CameraSyncSystem();
        this.scriptingSystem = new ScriptingSystem();
        
        ASHLEY.addSystem(playerFrameInputSystem);
        ASHLEY.addSystem(playerLocomotionSystem);
        ASHLEY.addSystem(physicsBodySystem);
        ASHLEY.addSystem(transformInterpolationSystem);
        ASHLEY.addSystem(cameraSyncSystem);
        ASHLEY.addSystem(scriptingSystem);
        
        
        
        /**
         * Register all debugging commands
         */
        debuggingConsole.registerCommand(new StatsCommand());
        debuggingConsole.registerCommand(new NoClipCommand());
        debuggingConsole.registerCommand(new InfoBodiesCommand());
        
        inputMultiplexer.addProcessor(debuggingConsole);
        inputMultiplexer.addProcessor(frameInputProcessor);

        camera = new PerspectiveCamera(90,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight());
        camera.near = 16f;
        camera.far  = 4096f;
        camera.update();
    }
    
    /**
     * Load a BSP map and initialize all BSP systems
     * Call this from show() in game screen
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
        DYN_RENDERER = new DynamicRenderer(SHADER, textureRoot);
        BSP_COLLISION = new BSPCollision(BSP);
        
 
        BSPEntities parsedEntities = BSPEntityParser.parse(BSP.entities);

        BSPEntities.Entity debugEnt = parsedEntities.first("debug");
        if (debugEnt != null) {
            Vector3 depos = Utilities.parseVector3(debugEnt.get("origin"));
            GameContext.get().set_noclip_camera(depos);
        }

        BSPEntities.Entity playerEnt = parsedEntities.first("info_player_start");
        if (playerEnt != null) {
            Vector3 ppos = Utilities.parseVector3(playerEnt.get("origin"));

            player_base = new BasePlayer(ppos);
            
            ScriptComponent sc = new ScriptComponent()
                    .add(new PlayerScriptTest(player_base.getEntity()));
            
            player_base.getEntity().add(sc);
            
            ASHLEY.addEntity(player_base.getEntity());
            setCamera(player_base.getCamera());

            // Hand input focus to the player. The player is the default dand
            // highest-priority input target. Other systems (vehicles, spec
            // cameras) can call InputFocus.set(...) to take it later.
            player_base.focus();
        }
        
        // function brush items, things like doors ? and rocks etc
        // they should NOT have any brushes for interaction, physics component and system will do the work
        List<BSPEntities.Entity> func_system_list = parsedEntities.byClass.get("func_item");
        if (func_system_list != null) {
            for (BSPEntities.Entity funcEnt : func_system_list){
                String brushModelKey = funcEnt.get("model");
                int bmIndex = Integer.parseInt(brushModelKey.substring(1));
                
                BSPFile.Model bModel = BSP.models[bmIndex];
                //System.out.println(bModel + " - " + bModel.firstFace);
                DYN_RENDERER.addBrush(brushModelKey, bModel, BSP);
                // 2. Create the "Instance" (The actual object in the world)
                DynamicRenderer.RenderInstance inst = new DynamicRenderer.RenderInstance();
                inst.meshKey = brushModelKey;
                inst.bmIndex = bmIndex;
                inst.position.set(DynamicRenderer.getModelCenter(bModel));
                
                Func_Item fi = new Func_Item(inst, bModel, inst.position);
                ASHLEY.addEntity(fi);
                DYN_RENDERER.instances.add(inst);
            }
        }
        
        List<BSPEntities.Entity> trigger_list = parsedEntities.byClass.get("trigger_ent");
        if (trigger_list != null){
            for (BSPEntities.Entity trigger_ent : trigger_list){
                // code here
                String brushModelIndex = trigger_ent.get("model");
                int bmIndex = Integer.parseInt(brushModelIndex.substring(1));

                Info_Entity.Trigger t = new Info_Entity.Trigger();
                GameContext.get().add_trigger(bmIndex, t);
            }
        }
        
        List<BSPEntities.Entity> scriptor_list = parsedEntities.byClass.get("scriptor");
        if (trigger_list != null){
            for (BSPEntities.Entity scriptor : scriptor_list){
                // code here
                System.out.println(scriptor.properties);
                if (scriptor.properties.get("target") != null){
                    // do thing
                }
            }
        }
    }
    
    public void render(float delta){
        if (BSP_RENDERER == null || camera == null) return;

        debuggingConsole.update(delta);

        // Input focus is the single source of truth. Locking it globally
        // silences the player, the mouse-look, and any future system that
        // consults InputFocus.is(...). Console-open and noclip both lock.
        InputFocus.setLocked(debuggingConsole.isVisible() || debuggingConsole.get_noclipState()); // maybe we dont need this ? 

        ASHLEY.updateFrame();
        float alpha = stepSimulation(delta);
        ASHLEY.updateRender(alpha);
        camera.update();
        
        // Convert GL camera position to Q3 coords for PVS lookup
        // GL(x, y, z) -> Q3(x, -z, y)
        float q3x =  camera.position.x;
        float q3y = -camera.position.z;
        float q3z =  camera.position.y;
        
        SHADER.clearLights();
        //SHADER.addSpotLight(camera.position, 500f, camera.direction, Color.WHITE, 30f);
        SHADER.bind();
        
        BSP_RENDERER.render(camera, q3x, q3y, q3z);
        DYN_RENDERER.render(camera); //already contains instances added
        debuggingConsole.render();
        
        
    }
    
    /**
     * 
     * @param delta - use for lights and animations
     *        alpha - use for interpolating physics
     *   fixed_step - use for any physcis movement
     */
    public float stepSimulation(float delta){
        boolean stepped = false;
        accumulator += Math.min(delta, MAX_FRAME_TIME);
        while (accumulator >= FIXED_STEP){
            accumulator -= FIXED_STEP;
            ASHLEY.updateFixed(FIXED_STEP);
            stepped = true;
        }

        float alpha = accumulator / FIXED_STEP;
        if (stepped && alpha <= ALPHA_EPSILON) {
            return 1f;
        }
        return alpha;
    }
    
    /**
    * Call when the viewport changes (window resize).
    */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.viewportWidth  = width;
        camera.viewportHeight = height;
        camera.update();
        debuggingConsole.resize(width, height);
    }
    
    public void setCamera(Camera cam){
        if (cam == null || cam == player_base.getCamera()){
            this.camera = player_base.getCamera();
            return;
        }

        this.camera = cam;
    }

    /**
     * Hand input focus to a different entity (e.g. a vehicle, a spectator
     * camera, a remote-controlled drone). The new entity must carry an
     * {@link io.rebelengine.Components.InputControlComponent}.
     * @param entity - entity we want to take input focus
     */
    public void setInputFocus(com.badlogic.ashley.core.Entity entity) {
        InputFocus.set(entity);
    }

    /** @return the entity currently receiving input, or {@code null}. */
    public com.badlogic.ashley.core.Entity getInputFocus() {
        return InputFocus.get();
    }

    /** @return true if input is globally locked (console, noclip, pause, ...). */
    public boolean isInputLocked() {
        return InputFocus.isLocked();
    }

    public InputProcessor getInputProcessor() {
        return inputMultiplexer;
    }
    
    @Override
    public void dispose() {
        if (BSP_RENDERER != null)   BSP_RENDERER.dispose();
        if (SHADER != null)         SHADER.dispose();
        debuggingConsole.dispose();
    }
}
