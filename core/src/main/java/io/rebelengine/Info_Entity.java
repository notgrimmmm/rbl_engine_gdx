/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.math.Vector3;

/**
 *
 */
public class Info_Entity {
    public static class Trigger{
        public boolean sleep = false;
        public int catch_flag = EntityFlags.PLAYER;
    }
    
    public static class Location {
        public Vector3 vec = new Vector3();
    }
    
    public static class Sound {
        
    }
}
