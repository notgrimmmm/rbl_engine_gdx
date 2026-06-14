package io.rebelengine.Commands;

import io.rebelengine.DebugCommand;
import io.rebelengine.DebugContext;
import io.rebelengine.DebugRenderContext;
import io.rebelengine.GameContext;
import io.rebelengine.InputFocus;

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
            GameContext.get().set_activeCamera(GameContext.get().get_noclip_camera());
            InputFocus.setLocked(true);
        } else {
            GameContext.get().set_activeCamera(null); // null routes to player camera
            InputFocus.setLocked(false);
        }

        return enabling ? "noclip on" : "noclip off";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        // no rendering
    }
}
