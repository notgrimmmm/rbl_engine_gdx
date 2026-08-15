
package io.rebelengine.Helpers;

public enum PhysicsType {
    Dynamic,        //  most use cases, props and the like that need gravity and roll
    Kinematic,      //  use cases where there should be NO overlap fixing, trystep, or gravity
    Mutable         //  use cases like scripted objs or player but with dynamic states, for control
}
