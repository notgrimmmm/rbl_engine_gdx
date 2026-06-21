package io.rebelengine.Debugging;

public interface DebugCommand {
    String key();
    String run(String[] args, DebugContext context);

    default void render(DebugRenderContext renderContext, DebugContext context) {
    }
}
