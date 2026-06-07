package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class TransformComponent implements Component {
    public final Vector3 previousPosition = new Vector3();
    public final Vector3 currentPosition = new Vector3();
    public final Vector3 renderPosition = new Vector3();

    public float previousYaw;
    public float currentYaw;
    public float renderYaw;

    public float previousPitch;
    public float currentPitch;
    public float renderPitch;
}
