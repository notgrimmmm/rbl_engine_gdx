package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

/**
 *
 */
public class DebugCamera extends PerspectiveCamera{
    private static final float  SPEED        = 400f;
    private static final float  SENS         = 0.2f;
    private float yaw = 0, pitch = 0;
    
    public DebugCamera(Vector3 origin){
        super(90f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.far = 4196;
        this.position.set(origin);
    }
    
    @Override
    public void update(){
        handleInput(Gdx.graphics.getDeltaTime());
        updateCamera();
        super.update();
    }
    
    private void updateCamera() {
        float yR = (float) Math.toRadians(yaw);
        float pR = (float) Math.toRadians(pitch);
        float cp = (float) Math.cos(pR);
        direction.set(
            cp * (float) Math.sin(yR),
            (float) Math.sin(pR),
           -cp * (float) Math.cos(yR)
        );
        up.set(0, 1, 0);
    }
    
    private void handleInput(float dt) {
        // Toggle mouse capture
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
        }

        float speed = SPEED * dt;
        float cos = (float) Math.cos(Math.toRadians(yaw));
        float sin = (float) Math.sin(Math.toRadians(yaw));

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            position.x +=  sin * speed;
            position.z += -cos * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            position.x -=  sin * speed;
            position.z -= -cos * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            position.x += cos * speed;
            position.z += sin * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            position.x -= cos * speed;
            position.z -= sin * speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE))      position.y += speed;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) position.y -= speed;

        // Mouse look
        if (Gdx.input.isCursorCatched()) {
            yaw   += Gdx.input.getDeltaX() * SENS;
            pitch -= Gdx.input.getDeltaY() * SENS;
            pitch  = Math.max(-89f, Math.min(89f, pitch));
        }
    }
}
