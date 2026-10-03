/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DynLightsManager {
    
    public final List<LitPoint> LIGHTS = new ArrayList<>();
    
    public static class LitPoint {        
        private final Vector3 position = new Vector3();
        private Color color = new Color();
        private float base_radius;
        private float current_radius; // use this 
        private float target_radius; // this doesnt change
        
        private float flickerRange = 0;
        private boolean active = true;
        
        private float flickerInterval = 0.1f;
        private float lerpSpeed = 15f;
        private float timer = 0f;
        
        private final Vector3 direction = new Vector3();
        public float intensity;
        
        /**
         * @param pos
         * @param col
         * @param radius
         * @param flicker
         * @param intense
         * @param on 
         */
        public LitPoint(Vector3 pos, Color col, float radius, float flicker, float intense, boolean on){
            this.position.set(pos);
            this.flickerRange = flicker;
            this.active = on;
            this.base_radius = radius;
            this.current_radius = this.base_radius;
            this.color = col;
            this.intensity = intense;
        }

        public void update(float delta) {
            if (flickerRange != 0){
                timer += delta;
                if (timer >= flickerInterval) {
                    target_radius = MathUtils.random(flickerRange, base_radius);
                    timer = 0f;
                }
                current_radius = MathUtils.lerp(current_radius, target_radius, delta * lerpSpeed);
            }
        }
        
        public Vector3 getPosition() {return position;}
        public void setActive(boolean on){ active = on; }
        public float getRadius() {return current_radius;}
        public Color getColor() {return color;}
        public Vector3 getDirection() {return direction;}
        
        public void setPosition(Vector3 pos){ position.set(pos); }
        public boolean isActive() {return active;}
        public void setFlickerRange(float fr){flickerRange = fr;}
        public void setDirection(Vector3 dir) {direction.set(dir);}
    }
    
    public void addLight(LitPoint light){
        LIGHTS.add(light);
    }
    
    public void refreshLighting(EngineShader shader, float delta){
        LIGHTS.forEach(l -> l.update(delta));
        
        for (LitPoint light : LIGHTS){
            if (light.isActive()) shader.addSpotLight(light.getPosition(), light.getRadius(), light.getDirection(), light.getColor(), light.intensity, 95f);
        }
    }
}