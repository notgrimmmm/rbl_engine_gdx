/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.gdx.utils.Array;

/**
 *
 */
public interface EntityScript {
    final Array<Object> group_components = new Array<>();
    void set_components(Array<Object> components);
    void update_fixed(float fixedDt);
    void execute(Object parameter);
    void execute_off();
}
