/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import Effekseer.swig.EffekseerBackendCore;
import Effekseer.swig.EffekseerEffectCore;
import Effekseer.swig.EffekseerManagerCore;
import io.game.Units.BasePlayer;
import io.rebelengine.Instances.UI_TextInteraction;
import io.rebelengine.Helpers.InputFocus;
import io.rebelengine.Helpers.LightGridManager;
import io.rebelengine.Helpers.EntityFlags;
import io.rebelengine.Helpers.Info_Entity;
import io.rebelengine.Helpers.Utils_Misc;
import io.rebelengine.Debugging.DebuggingConsole;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import games.rednblack.miniaudio.MiniAudio;
import io.game.Interactables.Prop;
import io.game.Interactables.Scripted;
import io.game.Interactables.ScriptedB;
import io.game.Scripts.Enemy_InitiatorTriggeringScript;
import io.game.Scripts.Enemy_NavLocomotionScript;
import io.game.Scripts.Player_InitiatorTriggeringScript;
import io.game.Scripts.Player_PropsInteractionScript;
import io.game.Units.BaseEnemy;
import io.rebelengine.Instances.SFX;
import io.rebelengine.Commands.InfoBodiesCommand;
import io.rebelengine.Commands.InfoNavmeshCommand;

import io.rebelengine.Systems.CameraSyncSystem;
import io.rebelengine.Systems.FrameInputSystem;
import io.rebelengine.Systems.PhysicsBodySystem;
import io.rebelengine.Systems.LocomotionSystem;
import io.rebelengine.Systems.TransformInterpolationSystem;

import io.rebelengine.Commands.StatsCommand;
import io.rebelengine.Commands.NoClipCommand;
import io.rebelengine.Components.CameraComponent;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.ScriptComponent;
import io.rebelengine.Helpers.Utils_Effeks;
import io.rebelengine.Systems.ScriptingSystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

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
    public DynLightsManager LIT_MANAGER;
    
    public BSPRenderer  BSP_RENDERER;
    public BSPTree      BSP_TREE;
    public BSPFile      BSP;

    public BSPCollision BSP_COLLISION;
    public DYNRenderer DYN_RENDERER;
    public UIRenderer UI_RENDERER;
    
    public final Effekseer.swig.EffekseerManagerCore EFFEKS_MANAGER;
    public final Array<EffekseerEffectCore> Effeks_Arr = new Array<>();

    public Camera camera;

    private final InputMultiplexer inputMultiplexer;
    private final DebuggingConsole debuggingConsole;
    private final EngineFrameInputProcessor frameInputProcessor;
    private final FrameInputSystem FrameInputSystem;
    private final LocomotionSystem LocomotionSystem;
    private final PhysicsBodySystem physicsBodySystem;
    private final TransformInterpolationSystem transformInterpolationSystem;
    private final CameraSyncSystem cameraSyncSystem;
    private final ScriptingSystem scriptingSystem;

    private BasePlayer player_base;
    
    public RebelEngineBase(MiniAudio audio_engine) {
        EngineContext gameContext = new EngineContext(this); // might cause issues later moving between scenes ???
        gameContext.initialize(gameContext);
        gameContext.set_audioEngine(audio_engine);

        this.ASHLEY = new RebelAshleyEngine();
        this.SHADER = new EngineShader();
        this.LIT_MANAGER = new DynLightsManager();
        this.UI_RENDERER = new UIRenderer();
        
        this.debuggingConsole = new DebuggingConsole(this);

        this.inputMultiplexer = new InputMultiplexer();
        this.frameInputProcessor = new EngineFrameInputProcessor(ASHLEY);

        this.FrameInputSystem = new FrameInputSystem();
        this.LocomotionSystem = new LocomotionSystem();

        this.physicsBodySystem = new PhysicsBodySystem();
        this.transformInterpolationSystem = new TransformInterpolationSystem();
        this.cameraSyncSystem = new CameraSyncSystem();
        this.scriptingSystem = new ScriptingSystem();
        
        System.loadLibrary("EffekseerNativeForJava");
        EffekseerBackendCore.InitializeWithOpenGL();
        this.EFFEKS_MANAGER = new EffekseerManagerCore();
        if (!EFFEKS_MANAGER.Initialize(8000)){
            Gdx.app.log("EFFEKCORE: ", "Failed to initialize manager"); return;
        }

        ASHLEY.addSystem(FrameInputSystem);
        ASHLEY.addSystem(LocomotionSystem);
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
        debuggingConsole.registerCommand(new InfoNavmeshCommand());

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
        DYN_RENDERER = new DYNRenderer(SHADER, textureRoot);
        BSP_COLLISION = new BSPCollision(BSP);
        
        NavMeshExtractor.NavMeshGeometry NAV_GEOMETRY = NavMeshExtractor.extract(BSP, "textures/common/nav");
        debuggingConsole.set_navmesh_data(NAV_GEOMETRY); // for debugging visuals

        BSPEntities parsedEntities = BSPEntityParser.parse(BSP.entities);

        BSPEntities.Entity debugEnt = parsedEntities.first("debug");
        if (debugEnt != null) {
            Vector3 depos = Utils_Misc.parseVector3(debugEnt.get("origin"));
            EngineContext.get().set_noclip_camera(depos);
        }else{
            inputMultiplexer.removeProcessor(debuggingConsole);
        }
        
        BSPEntities.Entity playerEnt = parsedEntities.first("info_player_start");
        if (playerEnt != null) {
            Vector3 ppos = Utils_Misc.parseVector3(playerEnt.get("origin"));

            player_base = new BasePlayer(ppos);

            ScriptComponent sc = new ScriptComponent()
                    .add(new Player_InitiatorTriggeringScript(player_base.getEntity()))
                    .add(new Player_PropsInteractionScript(player_base.getEntity()));

            player_base.getEntity().add(sc);
            ASHLEY.addEntity(player_base.getEntity());
            setCamera(player_base.getCamera());
            
            player_base.focus();
        }
        
        List<BSPEntities.Entity> cameras_list = parsedEntities.byClass.get("_camera");
        if (cameras_list != null){
            for (BSPEntities.Entity cam : cameras_list){
                if (cam.get("targetname") != null){
                    Vector3 origin = Utils_Misc.parseVector3(cam.get("origin"));
                    PerspectiveCamera _cam = new PerspectiveCamera(90f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                    _cam.far = 2000f;
                    _cam.position.set(origin);
        
                    if (cam.get("initiator") != null){
                        Entity entity = new Entity();
                        entity.flags = EntityFlags.INITIATOR;
                        
                        CameraComponent cameraComponent = new CameraComponent();
                        cameraComponent.camera = _cam;
                        
                        entity.add(cameraComponent);
                        _cam.update();
   
                        EngineContext.get().add_groupComponent(cam.get("targetname"), entity);
                        ASHLEY.addEntity(entity);
                        
                    }else{
                        EngineContext.get().add_groupComponent(cam.get("targetname"), _cam);
                    }
                }
            }
        }
        
        List<BSPEntities.Entity> sound_ent_list = parsedEntities.byClass.get("sound_ent");
        if (sound_ent_list != null){
            for (BSPEntities.Entity sound_ent : sound_ent_list){
                SFX sfx = new SFX(sound_ent.get("wav"));
                Vector3 origin = Utils_Misc.parseVector3(sound_ent.get("origin"));
                if (sound_ent.get("range3D") != null) sfx.setPositionAndRange(origin, Float.parseFloat(sound_ent.get("range3D")));
                if (sound_ent.get("uid") != null) sfx.setUID(sound_ent.get("uid"));
                
                if (sound_ent.get("targetname") != null){
                    EngineContext.get().add_groupComponent(sound_ent.get("targetname"), sfx);
                }
                if (sound_ent.get("set_volume") != null){
                    sfx.setVolume(Float.parseFloat(sound_ent.get("set_volume")));
                }
                if (sound_ent.get("set_loop") != null && sound_ent.get("set_loop").equals("1")){
                    sfx.loop();
                }
                if (sound_ent.get("loaded") != null && sound_ent.get("loaded").equals("1")){
                    sfx.playDefault();
                }
            }
        }
        
        List<BSPEntities.Entity> effeks_list = parsedEntities.byClass.get("info_effek");
        if (effeks_list != null){
            for (BSPEntities.Entity info_effek : effeks_list){
                
                Vector3 origin = Utils_Misc.parseVector3(info_effek.get("origin"));
                String effectPath = info_effek.get("file_path"); 
                
                EffekseerEffectCore effek = Utils_Effeks.loadEffect(effectPath, 1f); 
                if(effek == null) {
                    Gdx.app.log("EFFEKCORE: ", "Failed to load effect"); // Not needed but helpful debug
                }else{
                    Effeks_Arr.add(effek); // Add to array for correct exit 0 dispose later
                    
                    int efkhandle = EFFEKS_MANAGER.Play(effek);
                    EFFEKS_MANAGER.SetEffectPosition(efkhandle, origin.x, origin.y, origin.z);
                    if (info_effek.get("scale") != null){
                        float scale = Float.parseFloat(info_effek.get("scale"));
                        EFFEKS_MANAGER.SetEffectScale(efkhandle, scale, scale, scale);
                    }
                }

            }
        }
        
        List<BSPEntities.Entity> glb_prop_list = parsedEntities.byClass.get("glb_prop");
        if (glb_prop_list != null){
            for (BSPEntities.Entity glbEnt : glb_prop_list){
                String modelString = glbEnt.get("model");
                Vector3 origin = Utils_Misc.parseVector3(glbEnt.get("origin"));
                SceneAsset asset = new GLBLoader().load(Gdx.files.internal("main/baseq3/" + modelString));
                Model glbModel = asset.scene.model;
                
                DYNRenderer.RenderInstance instance = Utils_Misc.createGlbRenderInstance(modelString, glbModel, origin, Vector3.Zero, DYN_RENDERER);
                
                Prop prop = new Prop(instance, glbModel, origin);
                prop.add(new ScriptComponent());
                
                ASHLEY.addEntity(prop);
                DYN_RENDERER.instances.add(instance);
                
                if (glbEnt.get("uid") != null) prop.uid = glbEnt.get("uid");
                if (glbEnt.get("targetname") != null) EngineContext.get().add_groupComponent(glbEnt.get("targetname"), prop);
            }
        }
        
        List<BSPEntities.Entity> brush_scriptable_list = parsedEntities.byClass.get("brush_scriptable");
        if (brush_scriptable_list != null){
            for (BSPEntities.Entity brushEnt : brush_scriptable_list){
                DYNRenderer.RenderInstance instance = Utils_Misc.createRenderInstance(brushEnt.get("model"), DYN_RENDERER, BSP);
                BSPFile.Model bModel = BSP.models[instance.bmIndex];
                
                ScriptedB entity = new ScriptedB(instance, bModel, instance.position);
                
                ASHLEY.addEntity(entity);
                DYN_RENDERER.instances.add(instance);
                
                if (brushEnt.get("uid") != null) entity.uid = brushEnt.get("uid");
                
                if (brushEnt.get("absolute_targetname") != null){
                    EngineContext.get().add_groupComponent(brushEnt.get("absolute_targetname"), entity);
                }
                if (brushEnt.get("targetname") != null){
                    EngineContext.get().add_groupComponent(brushEnt.get("targetname"), entity);
                }
            }
        }
        
        //TODO: change this to phyisics debris
        List<BSPEntities.Entity> physics_prop_list = parsedEntities.byClass.get("physics_prop");
        if (physics_prop_list != null){
            for (BSPEntities.Entity physics_prop : physics_prop_list){
                DYNRenderer.RenderInstance instance = Utils_Misc.createRenderInstance(physics_prop.get("model"), DYN_RENDERER, BSP);
                BSPFile.Model bModel = BSP.models[instance.bmIndex];
                Prop prop = new Prop(instance, bModel, instance.position);
                prop.add(new ScriptComponent());
                prop.body.velocity_impact_threshold.set(Utils_Misc.parseVec3TB(physics_prop.get("impact_threshold")));
                
                ASHLEY.addEntity(prop);
                DYN_RENDERER.instances.add(instance);
            }
        }
        // scriptables
        List<BSPEntities.Entity> physics_scriptable_list = parsedEntities.byClass.get("physics_scriptable");
        if (physics_scriptable_list != null){
            for (BSPEntities.Entity physics_scriptable : physics_scriptable_list){
                DYNRenderer.RenderInstance instance = Utils_Misc.createRenderInstance(physics_scriptable.get("model"), DYN_RENDERER, BSP);
                BSPFile.Model bModel = BSP.models[instance.bmIndex];
                Scripted scripted = new Scripted(instance, bModel, instance.position);
                scripted.add(new ScriptComponent());
                scripted.body.gravity = 0f;
                
                if (physics_scriptable.get("absolute_targetname") != null){
                    EngineContext.get().add_groupComponent(physics_scriptable.get("absolute_targetname"), scripted);
                }
                if (physics_scriptable.get("targetname") != null){
                    EngineContext.get().add_groupComponent(physics_scriptable.get("targetname"), scripted);
                }
                
                ASHLEY.addEntity(scripted);
                DYN_RENDERER.instances.add(instance);
            }
        }

        List<BSPEntities.Entity> trigger_list = parsedEntities.byClass.get("trigger_ent"); // brush entity
        if (trigger_list != null){
            for (BSPEntities.Entity trigger_ent : trigger_list){
                String brushModelIndex = trigger_ent.get("model");
                int bmIndex = Integer.parseInt(brushModelIndex.substring(1));
                
                Vector3 min = new Vector3(BSP.models[bmIndex].minX,BSP.models[bmIndex].minY,BSP.models[bmIndex].minZ);
                Vector3 max = new Vector3(BSP.models[bmIndex].maxX,BSP.models[bmIndex].maxY,BSP.models[bmIndex].maxZ);
                Vector3 center = new Vector3(
                    (min.x + max.x) / 2.0f, 
                    -(min.z + max.z) / 2.0f,
                    -((min.y + max.y) / 2.0f)
                );
                
                Info_Entity.Trigger trigger = new Info_Entity.Trigger();
                trigger.center = center;
                EngineContext.get().add_trigger(bmIndex, trigger);
                
                if (trigger_ent.get("sleep") != null && !trigger_ent.get("sleep").isBlank())
                {
                    trigger.sleep = true;
                }

                if (trigger_ent.get("targetname") != null){
                    String group_tag = trigger_ent.get("targetname");
                    EngineContext.get().add_groupComponent(group_tag, trigger);
                    trigger.group_tag = group_tag;
                }

                if (trigger_ent.get("target") != null){
                    String target_tag = trigger_ent.get("target");
                    trigger.target_tag = target_tag;
                }
                
                if (trigger_ent.get("T_target") != null){
                    String T_target = trigger_ent.get("T_target");
                    trigger.T_target = T_target;
                }
                
                if (trigger_ent.get("initiator") != null && !trigger_ent.get("initiator").isBlank()){
                    //set catch flag that is not player default
                    String catchT = trigger_ent.get("initiator"); // the wanted initiator entity flag
                    trigger.catch_flag = switch (catchT){
                        case "projectile" -> EntityFlags.PROJECTILE;
                        case "enemy"      -> EntityFlags.ENEMY;
                        default -> trigger.catch_flag;
                    };
                }
            }
        }

        List<BSPEntities.Entity> locations_list = parsedEntities.byClass.get("info_location"); // point entity
        if (locations_list != null){
            for (BSPEntities.Entity info_location : locations_list){
                Info_Entity.Location location = new Info_Entity.Location();
                location.vec = Utils_Misc.parseVector3(info_location.get("origin"));
                if (info_location.get("indexed") != null ) location.indexed = Integer.parseInt(info_location.get("indexed"));
                
                String group_tag = info_location.get("targetname");
                EngineContext.get().add_groupComponent(group_tag, location);
            }
        }

        List<BSPEntities.Entity> facing_points_list = parsedEntities.byClass.get("facing_point"); // point entity
        if (facing_points_list != null){
            for (BSPEntities.Entity facing_point : facing_points_list){
                Info_Entity.FacingPoint facingPoint = new Info_Entity.FacingPoint();
                facingPoint.point = Utils_Misc.parseVector3(facing_point.get("origin"));

                String group_tag = facing_point.get("targetname");
                EngineContext.get().add_groupComponent(group_tag, facingPoint);
            }
        }

        List<BSPEntities.Entity> txt_interaction_list = parsedEntities.byClass.get("text_interaction"); // point entity
        if (txt_interaction_list != null){
            for (BSPEntities.Entity txt_interaction : txt_interaction_list){
                UI_TextInteraction txt = new UI_TextInteraction();
                txt.text = txt_interaction.get("text");
                txt.position = Utils_Misc.parseVector3(txt_interaction.get("origin"));
                if (txt_interaction.get("visible") != null){
                    txt.visible = txt_interaction.get("visible").equalsIgnoreCase("true");
                }

                EngineContext.get().add_text_UI(txt); // for rendering

                if (txt_interaction.get("targetname") !=  null){
                    EngineContext.get().add_groupComponent(txt_interaction.get("targetname"), txt); // for lookup
                }
            }
        }
        
        List<BSPEntities.Entity> dynamic_lights = parsedEntities.byClass.get("lit_point");
        if (dynamic_lights != null){
            for (BSPEntities.Entity litEnt : dynamic_lights){
                Vector3 origin = Utils_Misc.parseVector3(litEnt.get("origin"));
                Color color = Utils_Misc.parseColor255(litEnt.get("_color"));
                float radius = Float.parseFloat(litEnt.get("radius"));
                float flickerRange = litEnt.get("flicker_range") == null ? 0 : Float.parseFloat(litEnt.get("flicker_range"));
                float intensity = litEnt.get("light") == null ? 1.0f : Float.parseFloat(litEnt.get("light"));
                
                //add other shit
                DynLightsManager.LitPoint light_point = new DynLightsManager.LitPoint(origin, color, radius, flickerRange, intensity, true);
                if (litEnt.get("targetname") != null) EngineContext.get().add_groupComponent(litEnt.get("targetname"), light_point);
                if (litEnt.get("target") != null){
                    for (Object obj : EngineContext.get().get_groupComponentsArray(litEnt.get("target"))){
                        if (obj instanceof Info_Entity.FacingPoint fp){
                            Vector3 direction = new Vector3(fp.point).sub(origin).nor();
                            light_point.setDirection(direction);
                            // set cone for spotlight
                        }
                    }
                }
                
                LIT_MANAGER.addLight(light_point);
            }
        }
        
        List<BSPEntities.Entity> enemy_list = parsedEntities.byClass.get("info_enemy_start");
        if (enemy_list != null){
            NavMeshGraph NAV_GRAPH = new NavMeshGraph(NAV_GEOMETRY);
            for (BSPEntities.Entity enemy_entity : enemy_list){
                String modelString = enemy_entity.get("model");
                Vector3 origin = Utils_Misc.parseVector3(enemy_entity.get("origin"));
                SceneAsset asset = new GLBLoader().load(Gdx.files.internal("main/baseq3/" + modelString));
                Model glbModel = asset.scene.model;
                
                DYNRenderer.RenderInstance instance = Utils_Misc.createGlbRenderInstance(modelString, glbModel, origin, Vector3.Zero, DYN_RENDERER);
                
                BaseEnemy enemy = new BaseEnemy(instance, glbModel, origin, NAV_GRAPH);
                enemy.scriptComponent
                        .add(new Enemy_InitiatorTriggeringScript(enemy))
                        .add(new Enemy_NavLocomotionScript(enemy));
                
                if (enemy_entity.get("targetname") != null) EngineContext.get().add_groupComponent(enemy_entity.get("targetname"), enemy);
                ASHLEY.addEntity(enemy);
                DYN_RENDERER.instances.add(instance);
            }
        }

        List<BSPEntities.Entity> scriptor_list = parsedEntities.byClass.get("scriptor");
        if (scriptor_list != null){
            for (BSPEntities.Entity scriptor : scriptor_list){

                Array<Object> group_components = new Array<>();
                String group = "";
                if (scriptor.get("target") != null){
                    group = scriptor.get("target");
                    group_components = EngineContext.get().get_groupComponentsArray(group);
                }

                if (scriptor.get("class") == null){ // no class no script
                    System.err.println(scriptor + " No class property in scriptor, can't create script.");
                    continue;
                }

                String name = scriptor.get("class");
                try {
                    String fullPath = "io.game.Scripts." + name;
                    Class<? extends EntityScript> clazz = Class.forName(fullPath).asSubclass(EntityScript.class);
                    EntityScript script = clazz.getDeclaredConstructor().newInstance();
                    script.set_components(group_components);

                    // finally add to ecs ONLY if there is an Ashley Entity in the group components
                    // only reason we would probably want this is for initiators like enemies etc.
                    // otherwise script is not added to the ecs run meaning its not constanly executing on update_fixed, and only called
                    for (Object component : group_components){
                        if (component instanceof Entity compEntity){
                            if (compEntity.getComponent(ScriptComponent.class) == null){

                                ScriptComponent sc = new ScriptComponent();
                                sc.add(script);
                                compEntity.add(sc);

                            }else{

                                compEntity.getComponent(ScriptComponent.class).add(script);
                            }

                            break; // return out of the component loop safely
                        }
                    }

                    EngineContext.get().add_script(group, script); // add to game context for look up
                    System.out.println("Instantiated script file : " + script);

                } catch (ClassNotFoundException e) {
                    System.err.println("NOT FOUND " + e);
                } catch (Exception e){
                    System.err.println("ERROR " + e);
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
        LIT_MANAGER.refreshLighting(SHADER, delta);
        //SHADER.addSpotLight(camera.position, 500f, camera.direction, Color.WHITE, 1f, 55);
        SHADER.bind();

        EngineContext.get().AUDIO().setListenerPosition(camera.position.x, camera.position.y, camera.position.z);
        EngineContext.get().AUDIO().setListenerDirection(camera.direction.x, camera.direction.y, camera.direction.z);
        // set listener worl up ? also should i set the listener index 0 ?
        
        BSP_RENDERER.render(camera, q3x, q3y, q3z);
        DYN_RENDERER.render(camera); //already contains instances added
        UI_RENDERER.render(camera);
        
        Utils_Effeks.renderEffekseerStack(EFFEKS_MANAGER, camera, delta);
        
        debuggingConsole.render(); // do this last
    }

    /**
     * @param delta - use for lights and animations
     *        alpha - use for interpolating physics
     *   fixed_step - use for any physics movement
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
        if (cam != null) {
            this.camera = cam;
            return;
        }

        if (player_base != null) this.camera = player_base.getCamera();
    }

    /**
     * Hand input focus to a different entity (e.g. a vehicle, a spectator
     * camera, a remote-controlled drone). The new entity must carry a
     * {@link io.rebelengine.Components.InputControlComponent}.
     * @param entity - entity we want to take input focus
     */
    public void setInputFocus(Entity entity) {
        InputFocus.set(entity);
    }

    /** @return the entity currently receiving input, or {@code null}. */
    public Entity getInputFocus() {
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
        System.out.println("[ ENGINE DISPOSE ]");
        
        for (EffekseerEffectCore efk : Effeks_Arr){
            efk.delete();
        }
        Effeks_Arr.clear();
        
        if (BSP_RENDERER != null)   BSP_RENDERER.dispose();
        if (SHADER != null)         SHADER.dispose();
        if (EFFEKS_MANAGER != null) EFFEKS_MANAGER.delete();
        debuggingConsole.dispose();
    }
}