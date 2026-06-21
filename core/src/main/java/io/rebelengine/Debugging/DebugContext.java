package io.rebelengine.Debugging;

import io.rebelengine.RebelEngineBase;

public class DebugContext {
    private final RebelEngineBase engine;
    private final DebugState debugState;

    public DebugContext(RebelEngineBase engine, DebugState debugState) {
        this.engine = engine;
        this.debugState = debugState;
    }

    public RebelEngineBase engine() {
        return engine;
    }

    public DebugState debugState() {
        return debugState;
    }
}
