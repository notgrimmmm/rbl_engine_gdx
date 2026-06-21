
package io.rebelengine.Helpers;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.rebelengine.Instances.SFX;
import io.rebelengine.EngineContext;


public class Info_Entity {
    // Triggers always default to the Player, set another initiator through tb initiator property
    public static class Trigger{
        public String group_tag = ""; // the group this trigger belongs to
        public String target_tag = ""; // not being used ... find purpose for it otherwise just use scripts...
        public boolean sleep = false;
        public int catch_flag = EntityFlags.PLAYER;
        public Vector3 center = new Vector3();
        
        private int trigger_targeting_count = 0;
        
        public void trigger_targets(){
            if (target_tag.isBlank() || target_tag.isBlank()) return;
            if (trigger_targeting_count > 0) return;
            Array<Object> targets = EngineContext.get().get_groupComponentsArray(target_tag);
            if (targets == null || targets.isEmpty()) return;
            
            for (Object object : targets){ // switch check
                if (object instanceof SFX targetSFX){
                    targetSFX.play();
                }
            }
            trigger_targeting_count++;
        }
    }
    
    public static class Location {
        public Vector3 vec = new Vector3();
    }
    
    public static class FacingPoint {
        public Vector3 point = new Vector3();
    }
    
    public static class Sound {
        
    }
}
