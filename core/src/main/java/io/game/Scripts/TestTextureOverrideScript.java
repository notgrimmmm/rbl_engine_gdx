package io.game.Scripts;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Array;
import io.game.Interactables.ScriptedB;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.EngineContext;

/**
 * Fully self-contained test script creating its own FBO and text.
 */
public class TestTextureOverrideScript implements EntityScript {

    private ScriptedB scriptable_screen;
    private String meshkey = "";
    
    // Self-contained static resources for the test
    private static FrameBuffer selfFbo = null;
    private static SpriteBatch selfBatch = null;
    private static BitmapFont selfFont = null;
    private static boolean initialized = false;

    private void initSelfResources() {
        if (initialized) return;
        selfFbo = new FrameBuffer(Pixmap.Format.RGBA8888, 512, 512, false);
        selfBatch = new SpriteBatch();
        selfFont = new BitmapFont(); // Default LibGDX font
        initialized = true;
    }

    @Override
    public void set_components(Array<Object> array) {
        for (Object obj : array){
            if (obj instanceof ScriptedB brushScripted){
                scriptable_screen = brushScripted;
                if (scriptable_screen.instanceComponent != null && 
                    scriptable_screen.instanceComponent.instance != null) {
                    meshkey = scriptable_screen.instanceComponent.instance.meshKey;
                }
            }
        }
    }

    @Override
    public void update_fixed(float f) {
    }

    @Override
    public void execute(Object o) {
        if (meshkey == null || meshkey.isEmpty()) {
            System.out.println("[TEST] MeshKey is empty or null!");
            return;
        }

        // Initialize self-contained FBO and font tools
        initSelfResources();

        // 1. Render test text adventure content into our own FBO
        selfFbo.begin();
        Gdx.gl.glClearColor(0.0f, 0.15f, 0.0f, 1f); // Dark retro green screen
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);
        
        selfFont.getData().setScale(1.0f);
        
        selfBatch.getProjectionMatrix().setToOrtho2D(512, 512, -512, -512);

        selfBatch.begin();
        
        float offsetX = 200; // Shift right to get away from the clipped left edge
        float startY = 380;  // Adjust vertical starting position
        
        selfFont.draw(selfBatch, "=== TEXT ADVENTURE ===", offsetX, startY);
        selfFont.draw(selfBatch, "> SYSTEM ONLINE...", offsetX, startY - 40);
        selfFont.draw(selfBatch, "> ENTER COMMAND: LOOK ROOM", offsetX, startY - 80);
        
        selfBatch.end();
        selfFbo.end();

        // 2. Push the FBO's color texture directly to the renderer
        if (EngineContext.get().getDYNRenderer() != null) {
            EngineContext.get().getDYNRenderer().overrideTextureForMesh(meshkey, selfFbo.getColorBufferTexture(), false);
            //System.out.println("[TEST] Self-contained FBO texture successfully applied to meshKey: " + meshkey);
        } else {
            //System.out.println("[TEST] DYNRenderer reference is null!");
        }
    }

    @Override
    public void execute_off() {
    }
}