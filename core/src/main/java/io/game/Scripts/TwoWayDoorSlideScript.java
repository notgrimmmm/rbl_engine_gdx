/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Scripts;

import com.badlogic.gdx.utils.Array;
import io.game.Interactables.ScriptedB;
import io.rebelengine.Components.EntityScript;

/**
 *
 */
public class TwoWayDoorSlideScript implements EntityScript{

    ScriptedB targetR;
    ScriptedB targetL;
    @Override
    public void set_components(Array<Object> components) {
        for (Object obj : components){
            if (obj instanceof ScriptedB ent){
                if (targetR == null && ent.uid.equalsIgnoreCase("R")) targetR = ent;
                else if (targetL == null && ent.uid.equalsIgnoreCase("L")) targetL = ent;
            }
        }
    }

    private final float maxSlideDistance = 62.0f;
    private float currentSlideAmount = 0.0f;
    float moveStep = 0.7f;

    @Override
    public void update_fixed(float fixedDt) {
        if (targetR == null || targetL == null) return;
        if (currentSlideAmount >= maxSlideDistance) return;

        // clmp threshold movement
        if (currentSlideAmount + moveStep > maxSlideDistance) {
            moveStep = maxSlideDistance - currentSlideAmount;
        }

        targetR.transform.currentPosition.x += moveStep;
        targetL.transform.currentPosition.x -= moveStep;

        currentSlideAmount += moveStep;
    }

    @Override
    public void execute(Object parameter) {
    }

    @Override
    public void execute_off() {
    }
    
}
