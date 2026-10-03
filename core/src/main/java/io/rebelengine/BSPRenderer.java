package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BSPRenderer implements Disposable {

    private static final int PATCH_LEVEL = 4;
    private static final int MAX_FACES   = 65536;

    private final BSPFile      bsp;
    private final BSPTree      tree;
    private final EngineShader shader;

    private final Map<Long, FaceBatch> batches = new HashMap<>();
    private Texture[] lightmapTextures;

    private final int[]     visibleFaces = new int[MAX_FACES];
    private final boolean[] faceStamp    = new boolean[MAX_FACES];
    
    private final Matrix4 identityMatrix = new Matrix4();
    private final Matrix3 identityNormalMatrix = new Matrix3();

    private static class FaceBatch implements Disposable {
        Texture texture;
        Texture lightmap;
        int     texIdx;
        float[] verts;
        int[]   indices;
        int     vertexCount;
        int     indexCount;
        Mesh    mesh;

        @Override public void dispose() {
            if (mesh    != null) mesh.dispose();
            if (texture != null) texture.dispose();
        }
    }

    public BSPRenderer(BSPFile bsp, EngineShader shader) {
        this.bsp    = bsp;
        this.tree   = new BSPTree(bsp);
        this.shader = shader;
    }

    public void buildGeometry(String textureBasePath) {
        buildLightmaps();
        buildFaceBatches(textureBasePath);
    }

    private void buildLightmaps() {
        if (bsp.lightmaps == null) { lightmapTextures = new Texture[0]; return; }
        lightmapTextures = new Texture[bsp.lightmaps.length];
        for (int i = 0; i < bsp.lightmaps.length; i++) {
            lightmapTextures[i] = buildLightmapTexture(bsp.lightmaps[i]);
        }
    }

    private Texture buildLightmapTexture(BSPFile.Lightmap lm) {
        int s = BSPFile.LIGHTMAP_SIZE;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
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

    private void buildFaceBatches(String textureBasePath) {
        Map<Long, List<float[]>> vertMap = new HashMap<>();
        Map<Long, List<Integer>> idxMap  = new HashMap<>();

        BSPFile.Model world = bsp.models[0];
        int end = world.firstFace + world.numFaces;

        for (int fi = 0; fi < end; fi++) {
            BSPFile.Face face   = bsp.faces[fi];
            int          texIdx = face.textureIndex;

            if (texIdx < 0 || texIdx >= bsp.textures.length) continue;
            if ((bsp.textures[texIdx].surfaceFlags & 0x0004) != 0) continue;
            if (bsp.textures[texIdx].name.equals("missing")) continue;
            if (bsp.textures[texIdx].name.isEmpty()) continue;

            int  lmIdx = face.lightmapIndex;
            long key   = batchKey(texIdx, lmIdx);

            if (!vertMap.containsKey(key)) {
                vertMap.put(key, new ArrayList<>());
                idxMap .put(key, new ArrayList<>());
            }

            List<float[]> verts   = vertMap.get(key);
            List<Integer> indices = idxMap .get(key);

            if (face.type == BSPFile.FACE_POLYGON || face.type == BSPFile.FACE_MESH) {
                buildMeshFace(face, verts, indices);
            } else if (face.type == BSPFile.FACE_PATCH) {
                buildPatchFace(face, verts, indices);
            }
        }

        VertexAttribute[] attrs = {
            new VertexAttribute(Usage.Position,           3, "a_position"),
            new VertexAttribute(Usage.Normal,             3, "a_normal"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv0"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv1"),
        };

        Texture whiteLM = getOrCreateWhiteLightmap();

        for (Map.Entry<Long, List<float[]>> entry : vertMap.entrySet()) {
            long          key   = entry.getKey();
            List<float[]> vList = entry.getValue();
            List<Integer> iList = idxMap.get(key);
            if (vList.isEmpty()) continue;

            int texIdx = (int)(key >>> 32);
            int lmIdx  = (int)(key & 0xFFFFFFFFL);

            FaceBatch batch   = new FaceBatch();
            batch.texIdx      = texIdx;
            batch.vertexCount = vList.size();
            batch.verts       = new float[batch.vertexCount * 10];
            for (int i = 0; i < batch.vertexCount; i++) {
                System.arraycopy(vList.get(i), 0, batch.verts, i * 10, 10);
            }
            batch.indexCount = iList.size();
            batch.indices    = new int[batch.indexCount];
            for (int i = 0; i < batch.indexCount; i++) {
                batch.indices[i] = iList.get(i);
                if (batch.indices[i] > 65535) {
                    System.out.println("WARNING: Index overflow: " + bsp.textures[texIdx].name);
                    break;
                }
            }

            Mesh mesh = new Mesh(true, batch.vertexCount, batch.indexCount, attrs);
            mesh.setVertices(batch.verts);
            mesh.setIndices(toShortArray(batch.indices));
            batch.mesh     = mesh;
            batch.texture  = loadTexture(textureBasePath, texIdx);
            batch.lightmap = (lmIdx >= 0 && lmIdx < lightmapTextures.length)
                             ? lightmapTextures[lmIdx] : whiteLM;

            batches.put(key, batch);
        }
    }

    private void buildMeshFace(BSPFile.Face face, List<float[]> verts, List<Integer> indices) {
        int base = verts.size();
        for (int i = 0; i < face.numVertices; i++)
            verts.add(toGLVertex(bsp.vertices[face.firstVertex + i]));
        for (int i = 0; i < face.numMeshVerts; i++)
            indices.add(base + bsp.meshVerts[face.firstMeshVert + i]);
    }

    private void buildPatchFace(BSPFile.Face face, List<float[]> verts, List<Integer> indices) {
        int base = verts.size();
        List<float[]> pv = new ArrayList<>();
        List<Integer> pi = new ArrayList<>();
        tree.tessellatePatch(face, PATCH_LEVEL, pv, pi);
        verts.addAll(pv);
        for (int idx : pi) indices.add(base + idx);
    }

    public void render(Camera camera, float camX, float camY, float camZ) {
        int cameraLeaf = tree.findLeaf(camX, camY, camZ);

        java.util.Arrays.fill(faceStamp, 0, Math.min(faceStamp.length, bsp.faces.length), false);
        int faceCount = tree.collectVisibleFaces(cameraLeaf, visibleFaces, faceStamp, camera);

        boolean[] batchUsed = new boolean[bsp.textures != null ? bsp.textures.length : 0];
        for (int i = 0; i < faceCount; i++) {
            int tex = bsp.faces[visibleFaces[i]].textureIndex;
            if (tex >= 0 && tex < batchUsed.length) batchUsed[tex] = true;
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glFrontFace(GL20.GL_CW); // set geometry to use clockwise winding front faces
        Gdx.gl.glCullFace(GL20.GL_BACK); // disable back faces on geometry

        shader.bind();
        shader.setMVP(camera.combined);
        shader.setModel(identityMatrix); // this is needed to set matrix world 000 correctly for lighting
        shader.setNormalMatrix(identityNormalMatrix);
        shader.setLightGridColor(1f, 1f, 1f);
        shader.setCameraPosition(camera.position);
        shader.setUseNormalShading(true);

        Texture whiteLM = getOrCreateWhiteLightmap();

        for (Map.Entry<Long, FaceBatch> entry : batches.entrySet()) {
            FaceBatch batch = entry.getValue();
            if (batch.texIdx >= batchUsed.length || !batchUsed[batch.texIdx]) continue;
            if (batch.mesh == null) continue;

            if (batch.texture != null) {
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                batch.texture.bind();
            }

            Texture lm = batch.lightmap != null ? batch.lightmap : whiteLM;
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
            lm.bind();
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

            shader.setHasLightmap(lm == whiteLM ? 0f : 1f);
            batch.mesh.render(shader.getProgram(), GL20.GL_TRIANGLES); // this is the render model/world
        }
        
        Gdx.gl.glDisable(GL20.GL_CULL_FACE); // Unpolute cull face leaks for the next render
    }
    
    private static float[] toGLVertex(BSPFile.Vertex v) {
        float inset = 0.5f / BSPFile.LIGHTMAP_SIZE;
        float u1 = Math.max(inset, Math.min(1f - inset, v.u1));
        float v1 = Math.max(inset, Math.min(1f - inset, v.v1));
        return new float[]{ v.x, v.z, -v.y, v.nx, v.nz, -v.ny, v.u0, v.v0, u1, v1 };
    }

    private Texture loadTexture(String basePath, int texIdx) {
        if (bsp.textures == null || texIdx < 0 || texIdx >= bsp.textures.length)
            return checkerTexture();
        String name = bsp.textures[texIdx].name;
        for (String ext : new String[]{ ".tga", ".jpg", ".png" }) {
            FileHandle fh = Gdx.files.internal(basePath + "/" + name + ext);
            if (fh.exists()) {
                Texture t = new Texture(fh, true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                return t;
            }
        }
        return checkerTexture();
    }

    private static long batchKey(int texIdx, int lmIdx) {
        return ((long) texIdx << 32) | (lmIdx & 0xFFFFFFFFL);
    }

    private Texture whiteLightmap;
    private Texture getOrCreateWhiteLightmap() {
        if (whiteLightmap == null) {
            Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGB888);
            pm.setColor(1, 1, 1, 1); pm.fill();
            whiteLightmap = new Texture(pm); pm.dispose();
        }
        return whiteLightmap;
    }

    private Texture checker;
    private Texture checkerTexture() {
        if (checker == null) {
            Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                pm.drawPixel(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xFF00FFFF : 0x000000FF);
            checker = new Texture(pm); pm.dispose();
        }
        return checker;
    }

    private static short[] toShortArray(int[] src) {
        short[] dst = new short[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (short) src[i];
        return dst;
    }

    @Override
    public void dispose() {
        if (whiteLightmap    != null) whiteLightmap.dispose();
        if (checker          != null) checker.dispose();
        if (lightmapTextures != null)
            for (Texture t : lightmapTextures) if (t != null) t.dispose();
        for (FaceBatch b : batches.values()) b.dispose();
    }
}