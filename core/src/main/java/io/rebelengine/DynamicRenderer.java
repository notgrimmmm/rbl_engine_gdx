package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.Map;

public class DynamicRenderer implements Disposable {
    private final EngineShader SHADER;
    private final String textureBase;
    
    private final Map<String, Mesh> meshLibrary = new HashMap<>();
    private final Map<String, Texture> textureLibrary = new HashMap<>();
    public final Array<RenderInstance> instances = new Array<>();

    // Reusable objects to prevent garbage collection spikes
    private final Matrix4 modelTransform = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();

    public DynamicRenderer(EngineShader shader, String texturebase){
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

    public void addBrush(String key, BSPFile.Model bModel, BSPFile bsp) {
        if (meshLibrary.containsKey(key)) return; 

        Mesh mesh = buildMeshFromModel(bModel, bsp);
        if (mesh != null) {
            meshLibrary.put(key, mesh);
            int texIdx = bsp.faces[bModel.firstFace].textureIndex;
            String texName = bsp.textures[texIdx].name;
            loadTextureForBrush(key, texName);
        }
    }

    public static Vector3 getModelCenter(BSPFile.Model bModel) {
        return new Vector3(
            (bModel.minX + bModel.maxX) * 0.5f,
            (bModel.minZ + bModel.maxZ) * 0.5f,
            -(bModel.minY + bModel.maxY) * 0.5f
        );
    }

    private Mesh buildMeshFromModel(BSPFile.Model bModel, BSPFile bsp) {
        Array<Float> vList = new Array<>();
        Array<Short> iList = new Array<>();
        Vector3 modelCenter = getModelCenter(bModel);

        // BSP brush-model vertices are stored in world space. Dynamic instances
        // need local vertices so their transform can be their world position.
        for (int i = 0; i < bModel.numFaces; i++) {
            BSPFile.Face face = bsp.faces[bModel.firstFace + i];
            int base = vList.size / 7;

            if (face.type == BSPFile.FACE_POLYGON || face.type == BSPFile.FACE_MESH) {
                for (int v = 0; v < face.numVertices; v++) {
                    BSPFile.Vertex bv = bsp.vertices[face.firstVertex + v];
                    // Pos (X, Z, -Y) | UV0 | UV1 (placeholder 0)
                    vList.addAll(
                        bv.x - modelCenter.x,
                        bv.z - modelCenter.y,
                        -bv.y - modelCenter.z,
                        bv.u0, bv.v0, 0f, 0f
                    );
                }
                for (int m = 0; m < face.numMeshVerts; m++) {
                    iList.add((short) (base + bsp.meshVerts[face.firstMeshVert + m]));
                }
            }
        }

        if (vList.size == 0) return null;

        Mesh mesh = new Mesh(true, vList.size / 7, iList.size,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv0"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv1"));

        float[] vArr = new float[vList.size];
        for (int i = 0; i < vList.size; i++) vArr[i] = vList.get(i);
        short[] iArr = new short[iList.size];
        for (int i = 0; i < iList.size; i++) iArr[i] = iList.get(i);

        mesh.setVertices(vArr);
        mesh.setIndices(iArr);
        return mesh;
    }

    private void loadTextureForBrush(String meshKey, String textureName) {
        for (String ext : new String[]{".tga", ".png", ".jpg"}) {
            FileHandle fh = Gdx.files.internal(textureBase + "/" + textureName + ext);
            if (fh.exists()) {
                Texture t = new Texture(fh, true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                textureLibrary.put(meshKey, t);
                return;
            }
        }
    }

    public void render(Camera camera) {
        SHADER.bind();
        
        for (RenderInstance inst : instances) {
            Color lightC = LightGridManager.getAmbientAt(inst.position.x, -inst.position.z, inst.position.y);
            inst.lightGridColor.set(lightC.r, lightC.g, lightC.b);
            
            Mesh mesh = meshLibrary.get(inst.meshKey);
            Texture tex = textureLibrary.get(inst.meshKey);
            if (mesh == null) continue;

            if (tex != null) {
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                tex.bind();
            }

            // reuse modelTransform matrix
            modelTransform.setToTranslation(inst.position);
            // modelTransform.rotate(...)

            // reuse combinedMatrix
            combinedMatrix.set(camera.combined).mul(modelTransform);

            SHADER.setMVP(combinedMatrix);
            SHADER.setLightGridColor(inst.lightGridColor.x, inst.lightGridColor.y, inst.lightGridColor.z);
            SHADER.setHasLightmap(0.0f); // using lightmap data from bsp .no

            mesh.render(SHADER.getProgram(), GL20.GL_TRIANGLES);
        }
    }
    
    @Override
    public void dispose() {
        for (Mesh m : meshLibrary.values()) m.dispose();
        for (Texture t : textureLibrary.values()) t.dispose();
        meshLibrary.clear();
        textureLibrary.clear();
    }
}
