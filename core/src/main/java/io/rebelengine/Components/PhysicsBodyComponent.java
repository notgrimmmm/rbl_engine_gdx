package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPCollision;
import io.rebelengine.Helpers.PhysicsType;
import io.rebelengine.Instances.SFX;

public class PhysicsBodyComponent implements Component {
    public final Vector3 velocity = new Vector3();
    public final Vector3 minsStand = new Vector3(-16f, -16f, -16f);
    public final Vector3 maxsStand = new Vector3(16f, 16f, 16f);
    public final Vector3 minsCrouch = new Vector3(-16f, -14f, -16f);
    public final Vector3 maxsCrouch = new Vector3(16f, 14f, 16f);

    public float moveSpeed = 120f;
    public float jumpSpeed = 170f;
    public float gravity = 800f;
    public float terminalVelocity = -1500f;
    public float frictionCoeff = 0f;
    public float stepHeight = 18f;
    public float eyeStand = 48f;
    public float eyeCrouch = 20f;
    public float slopeCoeff = 0.7f;

    public boolean grounded;
    public boolean crouching;
    public boolean wantsCrouch;
    public boolean jumpRequested;
    public boolean noclip;
    
    public Vector3 savedPosition = new Vector3();
    public Vector3 savedVelocity = new Vector3();
    public Vector3 stepPosition = new Vector3();
    public Vector3 downPosition = new Vector3();
    public BSPCollision.TraceResult traceResult = new BSPCollision.TraceResult();
    
    public PhysicsType Physics = PhysicsType.Dynamic; // dynamic body is the default
    public int bspModelIndexReference = -1; // refer to the instance component if there is one
    public float roll_sign = 1f;
    public Vector3 velocity_impact_threshold = new Vector3(30, 220, 310);
    
    public Entity ignoreTarget = null; // when being held or inside another body it should ignore if we want
    
    public float sfxImpactRange = 200f;
    public SFX shortImpact;
    public SFX mediumImpact;
    public SFX highImpact;
}
