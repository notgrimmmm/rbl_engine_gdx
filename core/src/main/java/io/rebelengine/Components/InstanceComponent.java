/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Components;

import com.badlogic.ashley.core.Component;
import io.rebelengine.DynamicRenderer;

/**
 *
 */
public class InstanceComponent implements Component{
    public DynamicRenderer.RenderInstance instance = null;
    public int bspModelIndex = -1;
}
