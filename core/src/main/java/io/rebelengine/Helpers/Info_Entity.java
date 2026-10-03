
package io.rebelengine.Helpers;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.game.Interactables.Scripted;
import io.rebelengine.Instances.SFX;
import io.rebelengine.EngineContext;


public class Info_Entity {
    // Triggers always default to the Player, set another initiator through tb initiator property
    public static class Trigger{
        public String group_tag = ""; // the group this trigger belongs to
        public String target_tag = ""; // not being used ... find purpose for it otherwise just use scripts...
        public String T_target = ""; // more targets usually for other targetnames and specific to triggers
        public boolean sleep = false;
        public int catch_flag = EntityFlags.PLAYER;
        public Vector3 center = new Vector3();
        
        public int trigger_target_maxCount = 1; // given by property
        private int trigger_targeting_count = 0;
        
        public void trigger_targets(){
            if (target_tag.isBlank() || target_tag.isBlank()) return;
            if (trigger_targeting_count == trigger_target_maxCount) return;
            
            Array<Object> targets = EngineContext.get().get_groupComponentsArray(target_tag);
            if (!T_target.isBlank()) targets.addAll(EngineContext.get().get_groupComponentsArray(T_target));
            
            if (targets == null || targets.isEmpty()) return;
            
            for (Object object : targets){ // switch check
                if (object instanceof SFX targetSFX){
                    targetSFX.play();
                }
                if (object instanceof Trigger trigger){
                    trigger.sleep = false;
                }
                if (object instanceof Scripted scripted){
                    scripted.body.gravity = scripted.defaultGravity;
                }
            }
            trigger_targeting_count++;
        }
    }
    
    public static class Location {
        public int indexed = 0;
        public Vector3 vec = new Vector3();
    }
    
    public static class FacingPoint {
        public Vector3 point = new Vector3();
    }
    
    public static class Sound {
        
    }
}
