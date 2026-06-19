package io.game.Scripts;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.game.Interactables.Prop;
import io.rebelengine.BSPCollision;
import io.rebelengine.Components.EntityScript;
import io.rebelengine.Components.PhysicsBodyComponent;
import io.rebelengine.Components.TransformComponent;
import io.rebelengine.GameContext;
import io.rebelengine.UI_TextInteraction;

/**
 *
 */
public class Player_PropsInteractionScript implements EntityScript{
    
    private final Entity owner;
    UI_TextInteraction txt;
    
    private Entity hoveredEntity;
    private Entity grabbedEntity;
    private final Vector2 centerProjection = new Vector2();
    private boolean inputLatched = false;
    
    private float builtUpForce = 1f;

    public Player_PropsInteractionScript(Entity entity){
        this.owner = entity;
        this.txt = new UI_TextInteraction();
        GameContext.get().add_text_UI(txt);
    }
    

    Vector3 offset = new Vector3(-10, 30, 20);
    private final Vector3 grabPosition = new Vector3();
    private final Vector3 grabForward = new Vector3();
    private final Vector3 grabRight = new Vector3();

    @Override
    public void update_fixed() {
        BSPCollision.TraceResult rayResult = new BSPCollision.TraceResult();
        Vector3 start = GameContext.get().get_activeCamera().position;
        Vector3 dir = GameContext.get().get_activeCamera().direction;
        Vector3 end = new Vector3(start).mulAdd(dir, 70f);
        GameContext.get().trace_ray(owner, start, end, rayResult);
        
        txt.twoDProjectionFromCenter = centerProjection;
        txt.visible = true;
        txt.text = rayResult.hitEntity == null ? "" : "HIT";
        hoveredEntity = rayResult.hitEntity;
        
        if (!(hoveredEntity instanceof Prop)) hoveredEntity = null; // dont bother with it if its not a Prop
        
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)){ // TODO: some hand animations cocking back to show force build up and realease for 3 states low, mid, high force
            builtUpForce += 0.1f;
            builtUpForce = builtUpForce > 15.0f ? 15.0f : builtUpForce;
        }else builtUpForce = 1f;
        
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            // Only trigger the toggle if this is the initial frame the key was pressed
            if (!inputLatched) {
                if (grabbedEntity != null) {
                    releaseGrabbedEntity();
                } else if (hoveredEntity != null) {
                    grabEntity(hoveredEntity);
                }
                inputLatched = true; // Lock the latch so it won't fire again next fixed frame
            }
        } else {
            inputLatched = false; // Release the latch once the player lets go of the physical key
        }
        
        updateGrabbedEntity();
    }

    private void grabEntity(Entity entity) {
        PhysicsBodyComponent body = entity.getComponent(PhysicsBodyComponent.class);
        TransformComponent hitTransform = entity.getComponent(TransformComponent.class);
        TransformComponent ownerTransform = owner.getComponent(TransformComponent.class);

        if (body == null || hitTransform == null || ownerTransform == null) {
            return;
        }

        body.noclip = true;
        body.velocity.setZero();
        grabbedEntity = entity;
        updateGrabbedEntity();
    }

    private void releaseGrabbedEntity() {
        PhysicsBodyComponent body = grabbedEntity.getComponent(PhysicsBodyComponent.class);
        if (body != null) {
            body.noclip = false;
            // Recalculate full 3D direction vector including pitch right before release
            float yawRad = owner.getComponent(TransformComponent.class).currentYaw * MathUtils.degreesToRadians;
            float pitchRad = owner.getComponent(TransformComponent.class).currentPitch * MathUtils.degreesToRadians;

            float cosPitch = MathUtils.cos(pitchRad);
            grabForward.set(
                MathUtils.sin(yawRad) * cosPitch,
                MathUtils.sin(pitchRad),
                -MathUtils.cos(yawRad) * cosPitch
            );

            Vector3 finalThrowVelocity = new Vector3(grabForward).scl(30f * builtUpForce); // forward plus direction xz (pitch)
            body.velocity.set(finalThrowVelocity);
            body.grounded = false;
        }
        grabbedEntity = null;
        builtUpForce = 1f; // reset back to default just in case
    }

    private void updateGrabbedEntity() {
        if (grabbedEntity == null) {
            return;
        }

        PhysicsBodyComponent body = grabbedEntity.getComponent(PhysicsBodyComponent.class);
        TransformComponent hitTransform = grabbedEntity.getComponent(TransformComponent.class);
        TransformComponent ownerTransform = owner.getComponent(TransformComponent.class);

        if (body == null || hitTransform == null || ownerTransform == null) {
            grabbedEntity = null;
            return;
        }

        float yawRad = ownerTransform.currentYaw * MathUtils.degreesToRadians;
        grabForward.set(MathUtils.sin(yawRad), 0f, -MathUtils.cos(yawRad));
        grabRight.set(grabForward.z, 0f, -grabForward.x);

        grabPosition.set(ownerTransform.currentPosition)
                .mulAdd(grabRight, offset.x)
                .add(0f, offset.y, 0f)
                .mulAdd(grabForward, offset.z);

        body.noclip = true;
        body.velocity.setZero();
        
        
        hitTransform.currentPosition.set(grabPosition); // only need current pos since interpolation system will do the rest for smoothing
        hitTransform.currentYaw = -ownerTransform.currentYaw; // set it to the facing direction a little stutter maybe to lerp ? 
    }

    @Override
    public void execute(Object parameter) {

    }

    @Override
    public void execute_off() {
      
    }
    
    @Override
    public void set_components(Array<Object> components) {
    }
}
