package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.PerspectiveCamera;

public class CameraComponent implements Component {
    public PerspectiveCamera camera;
    public boolean active = true;
}
