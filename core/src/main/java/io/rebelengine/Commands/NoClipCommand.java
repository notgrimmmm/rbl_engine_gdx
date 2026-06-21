package io.rebelengine.Commands;

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

        return enabling ? "noclip on" : "noclip off";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        // no rendering
    }
}
