
package io.rebelengine;

import com.badlogic.gdx.math.Vector3;


public class Info_Entity {
    // Triggers always default to the Player, set another initiator through tb initiator property
    public static class Trigger{
        public String group_tag = ""; // the group this trigger belongs to
        public String target_tag = ""; // not being used ... find purpose for it otherwise just use scripts...
        public boolean sleep = false;
        public int catch_flag = EntityFlags.PLAYER;
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
