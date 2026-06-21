/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.game.Scripts;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.game.Behaviors.Input_LadderMoveBehavior;
import io.game.Behaviors.Locomotion_LadderMove;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.InputControlComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.Helpers.Info_Entity;
import io.rebelengine.Instances.UI_TextInteraction;

/**
 * 
 */
public class TwoWayLadderScript implements EntityScript{

    private UI_TextInteraction text_interaction;
    private Info_Entity.Trigger trigger;
    private final Vector3 facePoint = new Vector3();
    
    private boolean initiator_IN = false;
    
    private final InputControlComponent ladderInput;
    private InputControlComponent initatorDefaultInput;
    private Entity initiatorReference;
    
    float peakY;
    
    boolean climbing = false;
    private boolean inputLatched = false;
    
    public TwoWayLadderScript(){
        ladderInput = new InputControlComponent();
        ladderInput.inputBehavior = new Input_LadderMoveBehavior();
        ladderInput.locomotionBehavior = new Locomotion_LadderMove();
    }
    
    @Override
    public void set_components(Array<Object> components) {

        for (Object component : components){
            if (component instanceof UI_TextInteraction text){
                this.text_interaction = text;
                this.peakY = text_interaction.position.y;
            }
            if (component instanceof Info_Entity.Trigger trig){
                this.trigger = trig;
            }
            if (component instanceof Info_Entity.FacingPoint fp){
                facePoint.set(fp.point);
            }
        }
    }

    // trigger in
    @Override
    public void execute(Object parameter) {
        if (parameter instanceof Entity entity){
            if ((entity.flags & trigger.catch_flag) != 0){ // if the flags have the wanted flag we good
                initiator_IN = true;
                initiatorReference = entity;
            }else{
                return;
            }
        }else{
            return;
        }
        
        TransformComponent transform = initiatorReference.getComponent(TransformComponent.class);
        float desiredTextY = transform.renderPosition.y + 48f; // hard coded eye height
        text_interaction.position.y = Math.min(desiredTextY, peakY);
        
        text_interaction.visible = true;
        
    }

    // trigger out
    @Override
    public void execute_off() {
        text_interaction.visible = false;
        initiator_IN = false;
        
        if (climbing) stopClimbing();
    }


    // this is called by an initiator since this does not have a parent entity
    // frame update should not be needed for most scripts. input control should be used or passed
    // in this case since we arent trying to control a body in the ladder, we pass the input to the player
    @Override
    public void update_fixed() {
        if (!initiator_IN) {
            if (climbing) {
                stopClimbing();
            }
            return;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.F)) {
            // Only trigger the toggle if this is the initial frame the key was pressed
            if (!inputLatched) {
                if (!climbing) {
                    startClimbing();
                } else {
                    stopClimbing();
                }
                inputLatched = true; // Lock the latch so it won't fire again next fixed frame
            }
        } else {
            inputLatched = false; // Release the latch once the player lets go of the physical key
        }
    }

    private void startClimbing() {
        climbing = true;
        text_interaction.visible = false;
        
        initatorDefaultInput = initiatorReference.getComponent(InputControlComponent.class);
        initiatorReference.remove(InputControlComponent.class);
        initiatorReference.add(ladderInput);

        TransformComponent initiator_transform = initiatorReference.getComponent(TransformComponent.class);
        
        // grab direction vector from facing point
        float dx = facePoint.x - initiator_transform.currentPosition.x;
        float dz = facePoint.z - initiator_transform.currentPosition.z;

        // this is engine specific, leave as x, -z for engine axis
        float angleInRadians = (float) Math.atan2(dx, -dz);
        float angleInDegrees = angleInRadians * 180.0f / (float) Math.PI;
        
        Vector3 dir = new Vector3(
            facePoint.x - trigger.center.x,
            0, // Keep it flat on the horizontal plane
            facePoint.z - trigger.center.z
        );
        dir.nor();

        // we want the opposite of the face direction plus an offset
        float distanceOffset = 16.0f; // make it modular ?
        Vector3 wantedPosition = new Vector3(
            trigger.center.x - (dir.x * distanceOffset),
            initiator_transform.currentPosition.y, // Keep player's current height
            trigger.center.z - (dir.z * distanceOffset)
        );
        initiator_transform.currentPosition.set(wantedPosition);
        
        initiator_transform.currentYaw = angleInDegrees;
        
        // do this otherwise yaw in inputladder will be clamped to the world axis - and + ... idk
        if (ladderInput.inputBehavior instanceof  Input_LadderMoveBehavior behavior){
            behavior.baseYaw = angleInDegrees;
            behavior.lookYaw = 0f;
        }
    }

    private void stopClimbing() {
        climbing = false;
        if (initiator_IN) text_interaction.visible = true;
        
        initiatorReference.remove(InputControlComponent.class);
        initiatorReference.add(initatorDefaultInput);
        
        // Reset physics state that ladder locomotion might have changed
        io.rebelengine.Components.PhysicsBodyComponent body = initiatorReference.getComponent(io.rebelengine.Components.PhysicsBodyComponent.class);
        if (body != null) {
            body.noclip = false;
            body.grounded = false;
            body.velocity.setZero();
        }
    }
}
