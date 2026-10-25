package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.rebelengine.Helpers.LightGridManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DYNRenderer implements Disposable {
    private final EngineShader SHADER;
    private final String textureBase;

    // BSP brush models (generated meshes)
    private final Map<String, Array<BrushBatch>> brushLibrary = new HashMap<>();
    // GLB models (source models; each RenderInstance gets its own ModelInstance)
    private final Map<String, Model> glbModels = new HashMap<>();
    private final Map<String, Texture> glbTextureOverrides = new HashMap<>();

    private final Map<Integer, Texture> textureLibrary = new HashMap<>();
    private final Array<Model> glbModelsToDispose = new Array<>();

    private Texture[] lightmapTextures = new Texture[0];
    private Texture whiteLightmap;
    private Texture checkerTexture;
    public final Array<RenderInstance> instances = new Array<>();

    // Reusable objects to prevent garbage collection spikes
    private final Matrix4 modelTransform = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private final Matrix3 normalMatrix = new Matrix3();

    private final Matrix4 partTransform = new Matrix4();
    private final Matrix4 partMVP = new Matrix4();
    private final Matrix3 partNormal = new Matrix3();

    private final LightGridManager.GridSample gridSample = new LightGridManager.GridSample();

    public DYNRenderer(EngineShader shader, String texturebase){
        this.SHADER = shader;
        this.textureBase = texturebase;
    }

    public static class RenderInstance {
        public String meshKey;
        public int bmIndex;
        public Vector3 position = new Vector3();
        public Vector3 rotation = new Vector3();
        public Vector3 lightGridColor = new Vector3(1, 1, 1);

        // --- GLB animation state (null for BSP brushes) ---
        public ModelInstance modelInstance;
        public AnimationController animator;
        boolean resolved = false; // internal: have we looked for a GLB model yet?

        /** Play an animation. loop=true loops forever. blend = crossfade time in seconds. */
        public void play(String anim, boolean loop, float blend) {
            if (animator == null) {
                Gdx.app.error("DYNRenderer", "play(\"" + anim + "\") ignored: instance '" + meshKey
                        + "' has no animator (not resolved yet via createGlbInstance()/resolve(), "
                        + "or the model has no animations)");
                return;
            }
            if (modelInstance.getAnimation(anim) == null) {
                Gdx.app.error("DYNRenderer", "play(): animation \"" + anim + "\" not found on '" + meshKey + "'");
                return;
            }
            // don't restart a looping animation that's already playing
            if (loop && animator.current != null && animator.current.animation != null
                    && animator.current.animation.id.equals(anim)) return;
            animator.animate(anim, loop ? -1 : 1, 1f, null, blend);
        }

        /** Plays the first animation in the model */
        public void playFirst(boolean loop, float blend) {
            if (modelInstance == null || modelInstance.animations.size == 0) return;
            play(modelInstance.animations.get(0).id, loop, blend);
        }

        /** Play an animation once and get a callback when it ends. */
        public void playOnce(String anim, float blend, AnimationController.AnimationListener listener) {
            if (animator == null) return;
            animator.animate(anim, 1, 1f, listener, blend);
        }

        public void setAnimationSpeed(String anim, float speed, boolean loop, float blend) {
            if (animator == null) return;
            animator.animate(anim, loop ? -1 : 1, speed, null, blend);
        }

        public boolean hasAnimation(String anim) {
            return modelInstance != null && modelInstance.getAnimation(anim) != null;
        }

        public void setPaused(boolean paused) {
            if (animator != null) animator.paused = paused;
        }
    }

    private static class BrushBatch {
        Mesh mesh;
        Texture texture;
        Texture lightmap;
        boolean hasLightmap;
    }

    public void addBrush(String key, BSPFile.Model bModel, BSPFile bsp) {
        if (brushLibrary.containsKey(key)) return;

        ensureLightmaps(bsp);
        Array<BrushBatch> batches = buildBatchesFromModel(bModel, bsp);
        if (batches.size > 0) {
            brushLibrary.put(key, batches);
        }
    }

    public void addGlbModel(String key, Model glbModel) {
        if (glbModels.containsKey(key)) return;
        glbModels.put(key, glbModel);
        glbModelsToDispose.add(glbModel);
        printAnimations(key);
    }

    /** Debug: prints every animation name available on a registered GLB model. */
    public void printAnimations(String key) {
        Model m = glbModels.get(key);
        if (m == null) {
            Gdx.app.log("DYNRenderer", "printAnimations: no GLB model with key '" + key + "'");
            return;
        }
        if (m.animations.size == 0) {
            Gdx.app.log("DYNRenderer", "GLB '" + key + "' has NO animations");
            return;
        }
        Gdx.app.log("DYNRenderer", "GLB '" + key + "' animations (" + m.animations.size + "):");
        for (int i = 0; i < m.animations.size; i++) {
            com.badlogic.gdx.graphics.g3d.model.Animation a = m.animations.get(i);
            Gdx.app.log("DYNRenderer", "  [" + i + "] \"" + a.id + "\"  (" + a.duration + "s)");
        }
    }

    /**
     * Creates a render instance with its own animation state and adds it to the scene.
     * (Optional: instances you add to `instances` manually with just a meshKey
     * are auto-resolved to a GLB ModelInstance on the next update/render.)
     */
    public RenderInstance createGlbInstance(String key) {
        Model m = glbModels.get(key);
        if (m == null) return null;
        RenderInstance inst = new RenderInstance();
        inst.meshKey = key;
        attachGlb(inst, m);
        instances.add(inst);
        return inst;
    }

    public void removeInstance(RenderInstance inst) {
        instances.removeValue(inst, true);
    }

    private void attachGlb(RenderInstance inst, Model m) {
        inst.modelInstance = new ModelInstance(m);
        if (m.animations.size > 0) {
            inst.animator = new AnimationController(inst.modelInstance);
        }
        inst.resolved = true;
    }

    /** Lazily binds a GLB ModelInstance to instances that were added manually. */
    public void resolve(RenderInstance inst) {
        if (inst.resolved) return;
        inst.resolved = true;
        Model m = glbModels.get(inst.meshKey);
        if (m != null) attachGlb(inst, m);
    }

    public static Vector3 getModelCenter(BSPFile.Model bModel) {
        return new Vector3(
            (bModel.minX + bModel.maxX) * 0.5f,
            (bModel.minZ + bModel.maxZ) * 0.5f,
            -(bModel.minY + bModel.maxY) * 0.5f
        );
    }

    private Array<BrushBatch> buildBatchesFromModel(BSPFile.Model bModel, BSPFile bsp) {
        Map<Long, List<Float>> vertMap = new HashMap<>();
        Map<Long, List<Short>> idxMap = new HashMap<>();
        Vector3 modelCenter = getModelCenter(bModel);

        for (int i = 0; i < bModel.numFaces; i++) {
            BSPFile.Face face = bsp.faces[bModel.firstFace + i];
            int texIdx = face.textureIndex;
            if (texIdx < 0 || texIdx >= bsp.textures.length) continue;
            if ((bsp.textures[texIdx].surfaceFlags & 0x0004) != 0) continue;
            if (bsp.textures[texIdx].name.equals("missing")) continue;
            if (bsp.textures[texIdx].name.isEmpty()) continue;

            int lmIdx = face.lightmapIndex;
            long key = batchKey(texIdx, lmIdx);
            List<Float> vList = vertMap.computeIfAbsent(key, k -> new ArrayList<>());
            List<Short> iList = idxMap.computeIfAbsent(key, k -> new ArrayList<>());
            int base = vList.size() / 10;

            if (face.type == BSPFile.FACE_POLYGON || face.type == BSPFile.FACE_MESH) {
                for (int v = 0; v < face.numVertices; v++) {
                    BSPFile.Vertex bv = bsp.vertices[face.firstVertex + v];
                    addVertex(vList, bv, modelCenter);
                }
                for (int m = 0; m < face.numMeshVerts; m++) {
                    iList.add((short) (base + bsp.meshVerts[face.firstMeshVert + m]));
                }
            }
        }

        Array<BrushBatch> batches = new Array<>();
        for (Map.Entry<Long, List<Float>> entry : vertMap.entrySet()) {
            List<Float> vList = entry.getValue();
            List<Short> iList = idxMap.get(entry.getKey());
            if (vList.isEmpty() || iList == null || iList.isEmpty()) continue;

            Mesh mesh = new Mesh(true, vList.size() / 10, iList.size(),
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.Normal, 3, "a_normal"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv0"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv1"));

            float[] vArr = new float[vList.size()];
            for (int i = 0; i < vList.size(); i++) vArr[i] = vList.get(i);
            short[] iArr = new short[iList.size()];
            for (int i = 0; i < iList.size(); i++) iArr[i] = iList.get(i);

            mesh.setVertices(vArr);
            mesh.setIndices(iArr);

            int texIdx = (int)(entry.getKey() >>> 32);
            int lmIdx = (int)(entry.getKey() & 0xFFFFFFFFL);

            BrushBatch batch = new BrushBatch();
            batch.mesh = mesh;
            batch.texture = loadTexture(texIdx, bsp);
            batch.lightmap = (lmIdx >= 0 && lmIdx < lightmapTextures.length)
                    ? lightmapTextures[lmIdx] : getOrCreateWhiteLightmap();
            batch.hasLightmap = batch.lightmap != whiteLightmap;
            batches.add(batch);
        }
        return batches;
    }

    private void addVertex(List<Float> vList, BSPFile.Vertex bv, Vector3 modelCenter) {
        float inset = 0.5f / BSPFile.LIGHTMAP_SIZE;
        float u1 = Math.max(inset, Math.min(1f - inset, bv.u1));
        float v1 = Math.max(inset, Math.min(1f - inset, bv.v1));
        vList.add(bv.x - modelCenter.x);
        vList.add(bv.z - modelCenter.y);
        vList.add(-bv.y - modelCenter.z);
        vList.add(bv.nx);
        vList.add(bv.nz);
        vList.add(-bv.ny);
        vList.add(bv.u0);
        vList.add(bv.v0);
        vList.add(u1);
        vList.add(v1);
    }

    public void overrideTextureForMesh(String meshKey, Texture newTexture, boolean continueUseLightmap) {
        Array<BrushBatch> batches = brushLibrary.get(meshKey);
        if (batches != null) {
            for (BrushBatch batch : batches) {
                batch.texture = newTexture;
                batch.hasLightmap = continueUseLightmap;
            }
        }
        if (glbModels.containsKey(meshKey)) {
            glbTextureOverrides.put(meshKey, newTexture);
        }
    }

    private Texture loadTexture(int texIdx, BSPFile bsp) {
        Texture cached = textureLibrary.get(texIdx);
        if (cached != null) return cached;

        String textureName = bsp.textures[texIdx].name;
        for (String ext : new String[]{".tga", ".png", ".jpg"}) {
            FileHandle fh = Gdx.files.internal(textureBase + "/" + textureName + ext);
            if (fh.exists()) {
                Texture t = new Texture(fh, true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                textureLibrary.put(texIdx, t);
                return t;
            }
        }
        return getOrCreateCheckerTexture();
    }

    private void ensureLightmaps(BSPFile bsp) {
        if (lightmapTextures.length > 0 || bsp.lightmaps == null) return;

        lightmapTextures = new Texture[bsp.lightmaps.length];
        for (int i = 0; i < bsp.lightmaps.length; i++) {
            lightmapTextures[i] = buildLightmapTexture(bsp.lightmaps[i]);
        }
    }

    private Texture buildLightmapTexture(BSPFile.Lightmap lm) {
        int s = BSPFile.LIGHTMAP_SIZE;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGB888);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int idx = (y * s + x) * 3;
                int r = Byte.toUnsignedInt(lm.rgb[idx]);
                int g = Byte.toUnsignedInt(lm.rgb[idx + 1]);
                int b = Byte.toUnsignedInt(lm.rgb[idx + 2]);
                pm.drawPixel(x, y, (r << 24) | (g << 16) | (b << 8) | 0xFF);
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return t;
    }

    private Texture getOrCreateWhiteLightmap() {
        if (whiteLightmap == null) {
            Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGB888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fill();
            whiteLightmap = new Texture(pm);
            pm.dispose();
        }
        return whiteLightmap;
    }

    private Texture getOrCreateCheckerTexture() {
        if (checkerTexture == null) {
            Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    pm.drawPixel(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xFF00FFFF : 0x000000FF);
                }
            }
            checkerTexture = new Texture(pm);
            pm.dispose();
        }
        return checkerTexture;
    }

    private static long batchKey(int texIdx, int lmIdx) {
        return ((long)texIdx << 32) | (lmIdx & 0xFFFFFFFFL);
    }

    public void update(float delta) {
        for (int i = 0; i < instances.size; i++) {
            RenderInstance inst = instances.get(i);
            resolve(inst);
            if (inst.modelInstance == null) continue;

            if (inst.animator != null) inst.animator.update(delta);
            // updates node.globalTransform AND skinning bone matrices
            inst.modelInstance.calculateTransforms();
        }
    }

    public void render(Camera camera) {
        SHADER.bind();

        for (int i = 0; i < instances.size; i++) {
            RenderInstance inst = instances.get(i);
            resolve(inst);

            boolean isGlb = inst.modelInstance != null;
            Array<BrushBatch> batches = isGlb ? null : brushLibrary.get(inst.meshKey);
            if (!isGlb && batches == null) continue;

            LightGridManager.sampleAt(inst.position.x, inst.position.y, inst.position.z, gridSample);
            gridSample.ambient.add(LightGridManager.ambient_offset); // make it a bit brighter on dark sections

            SHADER.setLightGridData(gridSample.ambient, gridSample.directed, gridSample.lightDir);

            inst.lightGridColor.set(gridSample.ambient);
            SHADER.setLightGridColor(inst.lightGridColor.x, inst.lightGridColor.y, inst.lightGridColor.z);

            // instance transform
            modelTransform.setToTranslation(inst.position);
            modelTransform.rotate(Vector3.Y, inst.rotation.y);
            modelTransform.rotate(Vector3.X, inst.rotation.x);
            modelTransform.rotate(Vector3.Z, inst.rotation.z);

            if (isGlb) {
                Texture override = glbTextureOverrides.get(inst.meshKey);
                Array<Node> nodes = inst.modelInstance.nodes;
                for (int n = 0; n < nodes.size; n++) {
                    renderNode(nodes.get(n), camera, override);
                }
                continue;
            }

            // ---------------- BSP brush path ----------------
            SHADER.setSkinned(false);

            normalMatrix.set(modelTransform).inv().transpose();
            combinedMatrix.set(camera.combined).mul(modelTransform);

            SHADER.setMVP(combinedMatrix);
            SHADER.setModel(modelTransform);
            SHADER.setNormalMatrix(normalMatrix);

            ShaderProgram prog = SHADER.getProgram();
            for (int b = 0; b < batches.size; b++) {
                BrushBatch batch = batches.get(b);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                batch.texture.bind();

                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
                batch.lightmap.bind();
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

                SHADER.setHasLightmap(batch.hasLightmap ? 1f : 0f);
                SHADER.setUseNormalShading(true);

                batch.mesh.render(prog, GL20.GL_TRIANGLES);
            }
        }
        SHADER.setSkinned(false);
        SHADER.setUseNormalShading(false);
    }

    private void renderNode(Node node, Camera camera, Texture textureOverride) {
        ShaderProgram prog = SHADER.getProgram();

        for (int p = 0; p < node.parts.size; p++) {
            NodePart part = node.parts.get(p);
            if (!part.enabled) continue;

            boolean skinned = part.bones != null && part.bones.length > 0;

            if (skinned) {
                // Bone matrices already contain the node/bone hierarchy transforms
                // (model space), so only the instance transform is applied on top.
                partTransform.set(modelTransform);
                SHADER.setBones(part.bones);
            } else {
                // Rigid part: apply its animated node transform
                partTransform.set(modelTransform).mul(node.globalTransform);
            }
            SHADER.setSkinned(skinned);

            partNormal.set(partTransform).inv().transpose();
            partMVP.set(camera.combined).mul(partTransform);

            SHADER.setMVP(partMVP);
            SHADER.setModel(partTransform);
            SHADER.setNormalMatrix(partNormal);

            Texture tex = textureOverride;
            if (tex == null) {
                if (part.material.has(TextureAttribute.Diffuse)) {
                    TextureAttribute ta = (TextureAttribute) part.material.get(TextureAttribute.Diffuse);
                    tex = ta.textureDescription.texture;
                } else {
                    tex = getOrCreateCheckerTexture();
                }
            }

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            tex.bind();
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
            getOrCreateWhiteLightmap().bind();
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

            SHADER.setHasLightmap(0f);
            SHADER.setUseNormalShading(true);

            // Use the part's offset/size: GLB meshes often pack several parts in one buffer
            part.meshPart.mesh.render(prog, part.meshPart.primitiveType,
                    part.meshPart.offset, part.meshPart.size);
        }

        for (int c = 0; c < node.getChildCount(); c++) {
            renderNode(node.getChild(c), camera, textureOverride);
        }
    }

    @Override
    public void dispose() {
        for (Array<BrushBatch> batches : brushLibrary.values()) {
            for (BrushBatch batch : batches) {
                if (batch.mesh != null) batch.mesh.dispose();
            }
        }
        for (Texture t : textureLibrary.values()) t.dispose();
        if (lightmapTextures != null) {
            for (Texture t : lightmapTextures) if (t != null) t.dispose();
        }
        if (whiteLightmap != null) whiteLightmap.dispose();
        if (checkerTexture != null) checkerTexture.dispose();

        for (Model m : glbModelsToDispose) {
            m.dispose();
        }
        glbModelsToDispose.clear();

        brushLibrary.clear();
        glbModels.clear();
        glbTextureOverrides.clear();
        textureLibrary.clear();
        instances.clear();
    }
}