/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Scripts;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.EngineContext;
import io.rebelengine.Helpers.Info_Entity;

/**
 *
 */
public class Enemy_InitiatorTriggeringScript implements EntityScript{
        private final Entity owner;
    private PhysicsBodyComponent body;
    
    // Key: Trigger BSP Index (e.g., 3, 5) | Value: Running script instance
    public final IntMap<EntityScript> activeScripts = new IntMap<>();
    
    // A temporary scratch array used to safely queue keys for removal while iterating
    private final Array<Integer> removalScratch = new Array<>();

    public Enemy_InitiatorTriggeringScript(Entity owner) {
        this.owner = owner;
    }

    @Override
    public void update_fixed(float fixedDt) {
        body = owner.getComponent(PhysicsBodyComponent.class);
        Array<Integer> currentOverlaps = body.traceResult.nonSolidOverlapIndices;

        removalScratch.clear();
        for (IntMap.Entry<EntityScript> entry : activeScripts.entries()) {
            int activeTriggerId = entry.key;
            
            // If we are running a script for a trigger we are no longer touching...
            if (!currentOverlaps.contains(activeTriggerId, false)) {
                entry.value.execute_off();       // Turn it off
                removalScratch.add(activeTriggerId); // Mark it for removal
            }
        }
        
        // Physically remove them from the map now that iteration is finished
        for (int id : removalScratch) {
            activeScripts.remove(id);
        }

        for (int i = 0; i < currentOverlaps.size; i++) {
            int triggerId = currentOverlaps.get(i);
            Info_Entity.Trigger trigger = EngineContext.get().get_trigger(triggerId);

            if (trigger == null) continue;
            if ((owner.flags & trigger.catch_flag) == 0) continue;

            // Direct map lookup — order independent!
            EntityScript runningScript = activeScripts.get(triggerId);

            // CASE A: Trigger fell asleep while we were inside it
            if (trigger.sleep) {
                if (runningScript != null) {
                    runningScript.execute_off();
                    activeScripts.remove(triggerId);
                }
                continue;
            } else {
                if (trigger.group_tag.isBlank()) trigger.trigger_targets(); //only call this if there is no targetname group (there is no script calling it)
            }

            EntityScript targetScript = EngineContext.get().get_script(trigger.group_tag);

            // CASE B: Direct swap to a different script for the same trigger zone
            if (runningScript != null && runningScript != targetScript) {
                runningScript.execute_off();
                activeScripts.remove(triggerId);
                runningScript = null; // Forces CASE C to evaluate next
            }

            // CASE C: Fresh entry into this trigger zone
            if (runningScript == null && targetScript != null) {
                targetScript.execute(owner);
                activeScripts.put(triggerId, targetScript);
            }
        }

        for (EntityScript script : activeScripts.values()) {
            script.update_fixed(fixedDt);
        }
    }

    @Override public void set_components(Array<Object> components) {}
    @Override public void execute_off() {}
    @Override public void execute(Object parameter) {}
}