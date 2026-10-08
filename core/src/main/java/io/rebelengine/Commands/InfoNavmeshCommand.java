/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Commands;

import io.rebelengine.Debugging.DebugCommand;
import io.rebelengine.Debugging.DebugContext;
import io.rebelengine.Debugging.DebugRenderContext;
import io.rebelengine.EngineContext;

/**
 *
 */
public class InfoNavmeshCommand implements DebugCommand{

    @Override
    public String key() {
        return "info_navmesh";
    }

    @Override
    public String run(String[] args, DebugContext context) {
        context.debugState().infoNavmesh = !context.debugState().infoNavmesh;
        
        String displayKey = "[ " + key() + " ]";
        if (context.debugState().infoNavmesh){
            context.enabledCommandsText.append(displayKey);
        }else{
            int index = context.enabledCommandsText.indexOf(displayKey);
            if (index != -1) {
                context.enabledCommandsText.delete(index, index + displayKey.length());
            }
        }
        return context.debugState().infoBodies ? "info_navmesh enabled" : "info_navmesh disabled";
    }

    @Override
    public void render(DebugRenderContext renderContext, DebugContext context) {
        if (!context.debugState().infoNavmesh) return;
        if (context.get_navmesh_debug_render() == null) return;
        
        context.get_navmesh_debug_render().render(EngineContext.get().get_activeCamera());
    }
    
}
