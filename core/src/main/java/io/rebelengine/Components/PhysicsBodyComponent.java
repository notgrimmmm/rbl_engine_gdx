package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import io.rebelengine.BSPCollision;

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
    public float stepHeight = 18f;
    public float eyeStand = 48f;
    public float eyeCrouch = 20f;

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
    
    public int bspModelIndexReference = -1; // refer to the instance component if there is one
    
    
}
