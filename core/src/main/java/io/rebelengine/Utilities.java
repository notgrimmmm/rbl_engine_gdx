package io.rebelengine;

import com.badlogic.gdx.math.Vector3;

/**
 * engine helper utilities
 */
public class Utilities {
    public static Vector3 parseVector3(String s) {
        String[] p = s.split(" ");
        return new Vector3(Float.parseFloat(p[0]), Float.parseFloat(p[2]), -Float.parseFloat(p[1]));
    }
}
