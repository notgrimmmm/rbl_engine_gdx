/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Scripts;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.GameContext;
import io.rebelengine.Info_Entity;

/**
 *
 */
public class Player_InitiatorTriggeringScript implements EntityScript{
    private final Entity owner;
    private PhysicsBodyComponent body;
    
    public Player_InitiatorTriggeringScript(Entity owner){
        this.owner = owner;
    }
    

    // Parallel tracking array initialized with the same capacity (ordered=false, capacity=4)
    // This should live at the class level of this script/component so it persists across frames.
    public final Array<EntityScript> activeScripts = new Array<>(false, 4);
    @Override
    public void update_fixed() {
        body = owner.getComponent(PhysicsBodyComponent.class);
        Array<Integer> overlaps = body.traceResult.nonSolidOverlapIndices;

        int maxIterations = Math.max(overlaps.size, activeScripts.size);

        for (int i = 0; i < maxIterations; i++) {
            Integer triggerInt = (i < overlaps.size) ? overlaps.get(i) : null;
            EntityScript activeScript = (i < activeScripts.size) ? activeScripts.get(i) : null;

            // CASE 1: Trigger exited
            if (triggerInt == null) {
                if (activeScript != null) {
                    activeScript.execute_off();
                    activeScripts.set(i, null);
                }
                continue; 
            }

            int index = triggerInt; 
            Info_Entity.Trigger trigger = GameContext.get().get_trigger(index);

            if (trigger == null) continue;

            // CASE 2: Trigger fell asleep
            if (trigger.sleep) {
                if (activeScript != null) {
                    activeScript.execute_off();
                    activeScripts.set(i, null);
                }
                continue; 
            }

            EntityScript tScript = GameContext.get().get_script(trigger.group_tag);

            while (activeScripts.size <= i) {
                activeScripts.add(null);
            }

            // CASE 3: Direct swap to a different script
            if (activeScript != null && activeScript != tScript) {
                activeScript.execute_off();
                activeScripts.set(i, null);
                activeScript = null; 
            }

            // CASE 4: New trigger entry 
            // this will only be called once because once this hits, all cases will return false
            if (activeScript == null && tScript != null) {
                tScript.execute(owner); 
                activeScripts.set(i, tScript); 
            }
        }
        
        for (EntityScript active_script : activeScripts){
            if (active_script != null) active_script.update_fixed(); // keeps running the update_fixed loop on any active scripts
        }
        // Trim tracking array to match current overlap bounds
        while (activeScripts.size > overlaps.size && activeScripts.peek() == null) {
            activeScripts.pop();
        }
    }

    @Override
    public void set_components(Array<Object> components) {
    }

    @Override
    public void execute_off() {
    }

    @Override
    public void execute(Object parameter) {
    }
}
