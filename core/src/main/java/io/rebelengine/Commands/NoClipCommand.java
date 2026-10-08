package io.rebelengine.Commands;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.Debugging.DebugCommand;
import io.rebelengine.Debugging.DebugContext;
import io.rebelengine.Debugging.DebugRenderContext;
import io.rebelengine.EngineContext;
import io.rebelengine.Helpers.InputFocus;

/**
 * Toggles free-fly "noclip" mode.
 *
 * <p>On enter:  swap to the debug camera and lock input (the player should
 * not move while flying around).
 *
 * <p>On exit:   restore the player camera and unlock input. If anything had
 * stolen focus before noclip, we leave it alone — callers that hand off
 * focus are responsible for restoring it.
 */
public class NoClipCommand implements DebugCommand {

    private String playertoposition_texthelper = "[ p ] player to position";
    
    @Override
    public String key() {
        return "noclip";
    }

    @Override
    public String run(String[] args, DebugContext context) {
        boolean enabling = !context.debugState().noclip;
        context.debugState().noclip = enabling;

        if (enabling) {
            EngineContext.get().set_activeCamera(EngineContext.get().get_noclip_camera());
            InputFocus.setLocked(true);
        } else {
            EngineContext.get().set_activeCamera(null); // null routes to player camera
            InputFocus.setLocked(false);
        }
        
        String displayKey = "[ " + key() + " ]";
        if (context.debugState().noclip){
            context.enabledCommandsText.append(displayKey);
        }else{
            int index = context.enabledCommandsText.indexOf(displayKey);
            if (index != -1) {
                context.enabledCommandsText.delete(index, index + displayKey.length());
            }
        }

        return enabling ? "noclip on" : "noclip off";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        if (!context.debugState().noclip) {
            return;
        }
        
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)){
            if (context.engine().getInputFocus() != null){
                context.engine().getInputFocus().getComponent(TransformComponent.class).currentPosition.set(EngineContext.get().get_activeCamera().position);
            }
        }
        
        renderContext.uiBatch().begin();
        renderContext.uiFont().setColor(Color.WHITE);
        renderContext.uiFont().draw(renderContext.uiBatch(), playertoposition_texthelper, 100f, 100f);
        renderContext.uiBatch().end();
        
    }
}