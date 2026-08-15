package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DYNRenderer implements Disposable {
    private final EngineShader SHADER;
    private final String textureBase;
    
    private final Map<String, Array<BrushBatch>> brushLibrary = new HashMap<>();
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
    }

    private static class BrushBatch {
        Mesh mesh;
        Texture texture;
        Texture lightmap;
        boolean hasLightmap;
        boolean isGlbMesh; // Tracks if mesh was provided by GLB model vs generated BSP mesh
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
        if (brushLibrary.containsKey(key)) return;

        Array<BrushBatch> batches = new Array<>();
        glbModelsToDispose.add(glbModel);

        for (Node node : glbModel.nodes) {
            extractGlbBatches(node, batches);
        }

        if (batches.size > 0) {
            brushLibrary.put(key, batches);
        }
    }

    private void extractGlbBatches(Node node, Array<BrushBatch> batches) {
        for (NodePart part : node.parts) {
            BrushBatch batch = new BrushBatch();
            batch.mesh = part.meshPart.mesh;
            batch.isGlbMesh = true;

            if (part.material.has(TextureAttribute.Diffuse)) {
                TextureAttribute texAttr = (TextureAttribute) part.material.get(TextureAttribute.Diffuse);
                batch.texture = texAttr.textureDescription.texture;
            } else {
                batch.texture = getOrCreateCheckerTexture();
            }

            batch.lightmap = getOrCreateWhiteLightmap();
            batch.hasLightmap = false;

            batches.add(batch);
        }

        for (Node child : node.getChildren()) {
            extractGlbBatches(child, batches);
        }
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
            int base = vList.size() / 7;

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

            Mesh mesh = new Mesh(true, vList.size() / 7, iList.size(),
                new VertexAttribute(Usage.Position, 3, "a_position"),
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
            batch.isGlbMesh = false;
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
        vList.add(bv.u0);
        vList.add(bv.v0);
        vList.add(u1);
        vList.add(v1);
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

    public void render(Camera camera) {
        SHADER.bind();
        
        for (RenderInstance inst : instances) {
            Array<BrushBatch> batches = brushLibrary.get(inst.meshKey);
            if (batches == null) continue;
            
            //Color lightC = LightGridManager.getAmbientAt(inst.position.x, inst.position.y, inst.position.z);
            inst.lightGridColor.set(0.5f, 0.5f, 0.5f);

            // reuse modelTransform matrix
            modelTransform.setToTranslation(inst.position);
            modelTransform.rotate(Vector3.Y, inst.rotation.y);
            modelTransform.rotate(Vector3.X, inst.rotation.x);
            modelTransform.rotate(Vector3.Z, inst.rotation.z);

            // compute normal matrix for GLB model orientation
            normalMatrix.set(modelTransform).inv().transpose();

            // reuse combinedMatrix
            combinedMatrix.set(camera.combined).mul(modelTransform);

            SHADER.setMVP(combinedMatrix);
            SHADER.setModel(modelTransform);
            SHADER.setNormalMatrix(normalMatrix);
            SHADER.setLightGridColor(inst.lightGridColor.x, inst.lightGridColor.y, inst.lightGridColor.z); // ambient

            for (BrushBatch batch : batches) {
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                batch.texture.bind();

                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
                batch.lightmap.bind();
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

                SHADER.setHasLightmap(batch.hasLightmap ? 1f : 0f);
                SHADER.setUseNormalShading(batch.isGlbMesh); // Active Lambertian ONLY on GLB meshes

                batch.mesh.render(SHADER.getProgram(), GL20.GL_TRIANGLES);
            }
        }
    }
    
    @Override
    public void dispose() {
        for (Array<BrushBatch> batches : brushLibrary.values()) {
            for (BrushBatch batch : batches) {
                if (batch.mesh != null && !batch.isGlbMesh) {
                    batch.mesh.dispose();
                }
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
        textureLibrary.clear();
    }
}