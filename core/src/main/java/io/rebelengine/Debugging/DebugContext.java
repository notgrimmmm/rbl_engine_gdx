package io.rebelengine.Debugging;

import io.rebelengine.NavMeshDebugRenderer;
import io.rebelengine.NavMeshExtractor;
import io.rebelengine.RebelEngineBase;

public class DebugContext {
    private final RebelEngineBase engine;
    private final DebugState debugState;
    private NavMeshDebugRenderer _NavMeshDebugRender;
    
    public final StringBuilder enabledCommandsText = new StringBuilder();

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
    
    public void set_navmesh_debug(NavMeshExtractor.NavMeshGeometry geometry){
        _NavMeshDebugRender = new NavMeshDebugRenderer(geometry);
    }
    
    public NavMeshDebugRenderer get_navmesh_debug_render(){
        return _NavMeshDebugRender;
    }
}
