package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

/**
 * First person player controller with BSP collision.
 *
 * Owns a PerspectiveCamera — set it on the engine via engine.setCamera(player.getCamera()).
 *
 * Usage:
 *   PlayerController player = new PlayerController(collision);
 *   player.setSpawnPosition(x, y, z);
 *   engine.setCamera(player.getCamera());
 *   // each frame:
 *   player.update(delta);
 */
public class BasePlayerControl {

    // -----------------------------------------------------------------------
    // Player dimensions (Quake 3 units)
    // -----------------------------------------------------------------------
    public static final float RADIUS          = 16f;
    public static final float HEIGHT_STAND    = 32f;
    public static final float HEIGHT_CROUCH   = 28f;
    public static final float EYE_STAND       = 48f;
    public static final float EYE_CROUCH      = 20f;

    // Movement
    public static final float MOVE_SPEED      = 120f;
    public static final float JUMP_VELOCITY   = 170f;
    public static final float GRAVITY         = 800f;
    public static final float FRICTION        = 8f;
    public static final float STOP_SPEED      = 100f;
    public static final float STEP_HEIGHT     = 18f;   // max step up
    public static final float MOUSE_SENS      = 0.2f;

    // Total Height is 56. We put the "origin" in the middle of the vertical stick.
    private static final Vector3 MINS_STAND  = new Vector3(-RADIUS, -HEIGHT_STAND / 2f, -RADIUS);
    private static final Vector3 MAXS_STAND  = new Vector3( RADIUS,  HEIGHT_STAND / 2f,  RADIUS);

    private static final Vector3 MINS_CROUCH = new Vector3(-RADIUS, -HEIGHT_CROUCH / 2f, -RADIUS);
    private static final Vector3 MAXS_CROUCH = new Vector3( RADIUS,  HEIGHT_CROUCH / 2f,  RADIUS);

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private final BSPCollision collision;
    private final PerspectiveCamera camera;

    // World position — feet position in GL coords (Y-up)
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();

    private float yaw   = 0f;
    private float pitch = 0f;

    private boolean onGround  = false;
    private boolean crouching = false;
    private boolean noclip    = false; // debug fly mode

    // Scratch
    private final Vector3 wishDir  = new Vector3();
    private final Vector3 tmp      = new Vector3();
    private final BSPCollision.TraceResult tr = new BSPCollision.TraceResult();
    
    Vector3 prevPos = new Vector3();
    Vector3 currentPos = new Vector3();
    Vector3 eyeScale = new Vector3(0,EYE_STAND,0);

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public BasePlayerControl(BSPCollision collision, int viewportWidth, int viewportHeight) {
        this.collision = collision;
        this.camera    = new PerspectiveCamera(90, viewportWidth, viewportHeight);
        camera.near = 3f; // changed it to 3 to stop depth buffer issue with a blue purple band on distance lightmap .... change back to 1 to see it
        camera.far  = 4096f;
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    /**
     * Set player spawn position in GL coordinates (Y-up).
     * Pass the Q3 origin converted: Q3(x,y,z) -> GL(x, z, -y)
     */
    public void setSpawnPosition(float x, float y, float z) {
        position.set(x, y, z);
        //updateCamera();
        currentPos.set(position);
    }

    public PerspectiveCamera getCamera() { return camera; }

    // -----------------------------------------------------------------------
    // Per-frame update
    // -----------------------------------------------------------------------

    public void update(float alpha) {
        handleMouseLook();
        updateCamera(alpha);
    }
    
    public void updatePhysics(float fixedstep){
        prevPos.set(currentPos);
        applyGravity(fixedstep);  
        handleMovement(fixedstep);
        handleJump();
        moveAndCollide(fixedstep);
        currentPos.set(getPosition());
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    private void handleMouseLook() {
        if (!Gdx.input.isCursorCatched()) return;
        yaw   += Gdx.input.getDeltaX() * MOUSE_SENS;
        pitch -= Gdx.input.getDeltaY() * MOUSE_SENS;
        pitch  = Math.max(-89f, Math.min(89f, pitch));
    }

    private void handleMovement(float delta) {
        float cos = (float) Math.cos(Math.toRadians(yaw));
        float sin = (float) Math.sin(Math.toRadians(yaw));

        wishDir.set(0, 0, 0);

        // 1. Get raw input direction
        if (Gdx.input.isKeyPressed(Input.Keys.W)) { wishDir.x +=  sin; wishDir.z += -cos; }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { wishDir.x -=  sin; wishDir.z -= -cos; }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { wishDir.x +=  cos; wishDir.z +=  sin; }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { wishDir.x -=  cos; wishDir.z -=  sin; }

        //crouching = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        boolean wasCrouching = crouching;
        crouching = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);

        // If we JUST started crouching or JUST stopped
        if (crouching != wasCrouching) {
            float heightDiff = (HEIGHT_STAND - HEIGHT_CROUCH) / 2f;
            if (crouching) {
                // Box shrank: Lower the center so the feet stay on the floor
                position.y -= heightDiff;
            } else {
                // Box grew: Raise the center so the feet stay on the floor
                position.y += heightDiff;

                // OPTIONAL: Check if there is room to stand up
                // If you stand up under a low ceiling, you might get stuck in the geometry.
                // You should ideally run a traceBox here to see if you're blocked.
            }
        }
        
        
        
        
        
        float speed = crouching ? MOVE_SPEED * 0.5f : MOVE_SPEED;

        if (wishDir.len2() > 0) {
            wishDir.nor().scl(speed);

            // 2. INSTANT SPEED: Override X and Z velocity immediately
            // We do NOT touch velocity.y here because that's handled by gravity/jumping
            velocity.x = wishDir.x;
            velocity.z = wishDir.z;
        } else {
            // 3. INSTANT STOP: If no keys are pressed, kill horizontal momentum
            if (onGround) {
                velocity.x = 0;
                velocity.z = 0;
            }
        }
    }

    private void handleJump() {
        if (onGround && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            velocity.y = JUMP_VELOCITY;
            onGround   = false;
        }
    }

    private void applyGravity(float delta) {
        if (!onGround) {
            velocity.y -= GRAVITY * delta;
            // Terminal velocity
            if (velocity.y < -1500f) velocity.y = -1500f;
        }
    }

    // -----------------------------------------------------------------------
    // Collision movement
    // -----------------------------------------------------------------------

    private void moveAndCollide(float delta) {
        Vector3 mins = crouching ? MINS_CROUCH : MINS_STAND;
        Vector3 maxs = crouching ? MAXS_CROUCH : MAXS_STAND;

        // Try step-up before sliding move
        if (onGround && (velocity.x != 0 || velocity.z != 0)) {
            tryStepUp(mins, maxs, delta);
        } else {
            collision.slideMove(position, velocity, mins, maxs, delta);
        }

        // Ground check
        onGround = collision.isOnGround(position, mins, maxs, tr);
        if (onGround && velocity.y < 0) velocity.y = 0;
    }

    private void tryStepUp(Vector3 mins, Vector3 maxs, float delta) {
        // 1. Try normal move first
        Vector3 savedPos = new Vector3(position);
        Vector3 savedVel = new Vector3(velocity);

        collision.slideMove(position, velocity, mins, maxs, delta);

        // 2. If we moved far enough, no need to step
        if (position.dst2(savedPos) > 0.01f) return;

        // 3. Try stepping up
        Vector3 stepPos = new Vector3(savedPos).add(0, STEP_HEIGHT, 0);
        tr.reset();
        collision.traceBox(savedPos, stepPos, mins, maxs, tr);
        if (tr.fraction > 0) {
            position.set(savedPos).add(0, STEP_HEIGHT * tr.fraction, 0);

            // Try moving horizontally from stepped position
            velocity.set(savedVel);
            collision.slideMove(position, velocity, mins, maxs, delta);

            // Step down to land on surface
            Vector3 downPos = new Vector3(position).sub(0, STEP_HEIGHT, 0);
            tr.reset();
            collision.traceBox(position, downPos, mins, maxs, tr);
            if (tr.fraction < 1f) {
                position.lerp(downPos, tr.fraction);
            }
        }
    }



    // -----------------------------------------------------------------------
    // Camera sync
    // -----------------------------------------------------------------------
    private void updateCamera(float alpha) {
        float eyeHeight = crouching ? EYE_CROUCH : EYE_STAND;
        
        Vector3 crr = new Vector3(currentPos.x, currentPos.y + eyeHeight, currentPos.z);
        
        // Camera sits at eye height above feet
        camera.position.set(
            prevPos.x,
            prevPos.y + eyeHeight,
            prevPos.z
        ).lerp(crr, alpha);

        float yR = (float) Math.toRadians(yaw);
        float pR = (float) Math.toRadians(pitch);
        float cp = (float) Math.cos(pR);
        camera.direction.set(
            cp * (float) Math.sin(yR),
            (float) Math.sin(pR),
           -cp * (float) Math.cos(yR)
        );
        camera.up.set(0, 1, 0);
        camera.update();
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public Vector3 getPosition()  { return position; }
    public Vector3 getVelocity()  { return velocity; }
    public boolean isOnGround()   { return onGround; }
    public boolean isNoclip()     { return noclip; }
    public float   getYaw()       { return yaw; }
    public float   getPitch()     { return pitch; }

    // Q3 coordinates for PVS lookup
    public float q3x() { return position.x; }
    public float q3y() { return -position.z; }
    public float q3z() { return position.y + (crouching ? EYE_CROUCH : EYE_STAND); }
}