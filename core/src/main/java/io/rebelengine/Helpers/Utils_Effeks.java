/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine.Helpers;

import Effekseer.swig.EffekseerEffectCore;
import Effekseer.swig.EffekseerManagerCore;
import Effekseer.swig.EffekseerTextureType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import java.io.File;
import java.io.Serializable;

/**
 * Loading and rendering utilities for effeks in 3d
 */
public class Utils_Effeks implements Serializable{
    private static final float[] TMP = new float[16];
    public static EffekseerEffectCore loadEffect(String effectPath, float magnification) {
        com.badlogic.gdx.files.FileHandle handle = Gdx.files.internal(effectPath);
        EffekseerEffectCore effectCore = new EffekseerEffectCore();

        // load an effect
        {
            byte[] bytes = handle.readBytes();
            if (!effectCore.Load(bytes, bytes.length, magnification)) {
                System.out.print("Failed to load.");
                return null;
            }
        }

        // load textures
        EffekseerTextureType[] textureTypes = new EffekseerTextureType[] {
                EffekseerTextureType.Color,
                EffekseerTextureType.Normal,
                EffekseerTextureType.Distortion,
        };

	    for(int t = 0; t < 3; t++)
        {
            for (int i = 0; i < effectCore.GetTextureCount(textureTypes[t]); i++) {
                String path = (new File(effectPath)).getParent();
                if (path != null) {
                    path += "/" + effectCore.GetTexturePath(i, textureTypes[t]);
                } else {
                    path = effectCore.GetTexturePath(i, textureTypes[t]);
                }

                handle = Gdx.files.internal(path);
                byte[] bytes = handle.readBytes();
                effectCore.LoadTexture(bytes, bytes.length, i, textureTypes[t]);
            }
        }

        for (int i = 0; i < effectCore.GetModelCount(); i++) {
            String path = (new File(effectPath)).getParent();
            if (path != null) {
                path += "/" + effectCore.GetModelPath(i);
            } else {
                path = effectCore.GetModelPath(i);
            }

            handle = Gdx.files.internal(path);
            byte[] bytes = handle.readBytes();
            effectCore.LoadModel(bytes, bytes.length, i);
        }

		for (int i = 0; i < effectCore.GetMaterialCount(); i++) {
			String path = (new File(effectPath)).getParent();
			if (path != null) {
				path += "/" + effectCore.GetMaterialPath(i);
			} else {
				path = effectCore.GetMaterialPath(i);
			}

			handle = Gdx.files.internal(path);
			byte[] bytes = handle.readBytes();
			effectCore.LoadMaterial(bytes, bytes.length, i);
		}

		for (int i = 0; i < effectCore.GetCurveCount(); i++) {
			String path = (new File(effectPath)).getParent();
			if (path != null) {
				path += "/" + effectCore.GetCurvePath(i);
			} else {
				path = effectCore.GetCurvePath(i);
			}

			handle = Gdx.files.internal(path);
			byte[] bytes = handle.readBytes();
			effectCore.LoadCurve(bytes, bytes.length, i);
		}
        return effectCore;
    }
    public static float[] toEffekseerFloats(com.badlogic.gdx.math.Matrix4 m) {
        float[] a = m.val;
        TMP[0]  = a[0];  TMP[1]  = a[4];  TMP[2]  = a[8];  TMP[3]  = a[12];
        TMP[4]  = a[1];  TMP[5]  = a[5];  TMP[6]  = a[9];  TMP[7]  = a[13];
        TMP[8]  = a[2];  TMP[9]  = a[6];  TMP[10] = a[10]; TMP[11] = a[14];
        TMP[12] = a[3];  TMP[13] = a[7];  TMP[14] = a[11]; TMP[15] = a[15];
        return TMP;
    }
    public static void renderEffekseerStack(EffekseerManagerCore effeksManager, Camera camera, float delta){
        float[] projRM = Utils_Effeks.toEffekseerFloats(camera.projection);
        effeksManager.SetProjectionMatrix(
            projRM[0], projRM[1], projRM[2], projRM[3],
            projRM[4], projRM[5], projRM[6], projRM[7],
            projRM[8], projRM[9], projRM[10], projRM[11],
            projRM[12], projRM[13], projRM[14], projRM[15]
        );

        float[] viewRM = Utils_Effeks.toEffekseerFloats(camera.view);
        effeksManager.SetCameraMatrix(
            viewRM[0], viewRM[1], viewRM[2], viewRM[3],
            viewRM[4], viewRM[5], viewRM[6], viewRM[7],
            viewRM[8], viewRM[9], viewRM[10], viewRM[11],
            viewRM[12], viewRM[13], viewRM[14], viewRM[15]
        );

        effeksManager.Update(delta / (1.0f / 60.0f));
        effeksManager.DrawBack();
        effeksManager.DrawFront();
    }
}