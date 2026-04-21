/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/**
 * Holds all components used in physics
 */
public class ComponentBag {
    public static class TransformComponent implements Component {
        public final Vector3 pos = new Vector3(), prevPos = new Vector3(), renderPos = new Vector3();
    }

    public static class PhysicsComponent implements Component {
        public final Vector3 vel = new Vector3();
        public final Vector3 mins = new Vector3(); // Defined at spawn
        public final Vector3 maxs = new Vector3(); // Defined at spawn
        public boolean onGround = false;
    }

    public static class CameraComponent implements Component {
        public float yaw = 0, pitch = 0;
        public float offset = 0; // Vertical offset from Transform.pos
        public boolean isActive = false;
    }
}
