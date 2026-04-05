/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BSP46 renderer for Quake 3 maps.
 *
 * Handles all 4 face types:
 *   POLYGON   — flat polygon, use meshVerts as triangle indices
 *   PATCH     — bicubic bezier surface, tessellate at load time
 *   MESH      — triangle mesh, same as polygon
 *   BILLBOARD — ignored (engine-specific effect)
 *
 * Coordinate system:
 *   Q3 uses Z-up right-handed. LibGDX/GL uses Y-up right-handed.
 *   Swizzle: Q3(x,y,z) → GL(x, z, -y)
 *
 * Lightmaps:
 *   Each 128×128 RGB lightmap is uploaded as a separate texture.
 *   The renderer does a two-pass multiply blend per face: base texture × lightmap.
 *   For simplicity this implementation uses a single shader with both samplers.
 */
public class BSPRenderer implements Disposable {

    private static final int PATCH_LEVEL = 4; // bezier tessellation quality
    private static final int MAX_FACES   = 65536;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private final BSPFile bsp;
    private final BSPTree tree;

    // Per-face GPU batch (keyed by texture index)
    private final Map<Integer, FaceBatch> batches = new HashMap<>();

    // Lightmap textures
    private Texture[] lightmapTextures;

    // Frame scratch
    private final int[]     visibleFaces = new int[MAX_FACES];
    private final boolean[] faceStamp    = new boolean[MAX_FACES];

    private ShaderProgram shader;

    // -----------------------------------------------------------------------
    // Shader with base texture + lightmap multiply
    // -----------------------------------------------------------------------
    private static final String VERT =
        "attribute vec3 a_position;\n" +
        "attribute vec2 a_uv0;\n" +
        "attribute vec2 a_uv1;\n" +
        "uniform mat4 u_mvp;\n" +
        "varying vec2 v_uv0;\n" +
        "varying vec2 v_uv1;\n" +
        "void main() {\n" +
        "  gl_Position = u_mvp * vec4(a_position, 1.0);\n" +
        "  v_uv0 = a_uv0;\n" +
        "  v_uv1 = a_uv1;\n" +
        "}\n";

    private static final String FRAG =
        "#ifdef GL_ES\n precision mediump float;\n #endif\n" +
        "uniform sampler2D u_base;\n" +
        "uniform sampler2D u_lightmap;\n" +
        "uniform float u_hasLightmap;\n" +
        "varying vec2 v_uv0;\n" +
        "varying vec2 v_uv1;\n" +
        "void main() {\n" +
        "  vec4 base = texture2D(u_base, v_uv0);\n" +
        "  vec4 lm   = texture2D(u_lightmap, v_uv1);\n" +
        "  vec3 light = mix(vec3(1.0), lm.rgb * 2.0, u_hasLightmap);\n" +
        "  gl_FragColor = vec4(base.rgb * light, base.a);\n" +
        "}\n";

    // -----------------------------------------------------------------------
    // Per-texture batch
    // -----------------------------------------------------------------------
    private static class FaceBatch implements Disposable {
        Texture texture;
        // Vertices stored as flat float array: x,y,z, u0,v0, u1,v1 (7 floats)
        float[] verts;
        int[]   indices;
        int     vertexCount;
        int     indexCount;
        Mesh    mesh;

        // Map from lightmap index → list of index ranges in this batch
        // (for a production engine; here we just render all at once)

        @Override public void dispose() {
            if (mesh    != null) mesh.dispose();
            if (texture != null) texture.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public BSPRenderer(BSPFile bsp) {
        this.bsp  = bsp;
        this.tree = new BSPTree(bsp);

        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("BSP46 shader error: " + shader.getLog());
        }
    }

    // -----------------------------------------------------------------------
    // Build geometry — call once after loading
    // -----------------------------------------------------------------------

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
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGB888);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int idx = (y * s + x) * 3;
                // Quake 3 lightmaps are often overbright — clamp to byte range
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
        // Accumulate vertex/index data per texture
        Map<Integer, List<float[]>> vertMap  = new HashMap<>();
        Map<Integer, List<Integer>> idxMap   = new HashMap<>();

        for (int fi = 0; fi < bsp.faces.length; fi++) {
            BSPFile.Face face = bsp.faces[fi];
            int texIdx = face.textureIndex;

            if (!vertMap.containsKey(texIdx)) {
                vertMap.put(texIdx, new ArrayList<>());
                idxMap .put(texIdx, new ArrayList<>());
            }

            List<float[]> verts   = vertMap.get(texIdx);
            List<Integer> indices = idxMap .get(texIdx);

            if (face.type == BSPFile.FACE_POLYGON || face.type == BSPFile.FACE_MESH) {
                buildMeshFace(face, verts, indices);
            } else if (face.type == BSPFile.FACE_PATCH) {
                buildPatchFace(face, verts, indices);
            }
            // BILLBOARD skipped
        }

        // Upload to GPU
        VertexAttribute[] attrs = {
            new VertexAttribute(Usage.Position,           3, "a_position"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv0"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "a_uv1"),
        };

        for (Map.Entry<Integer, List<float[]>> entry : vertMap.entrySet()) {
            int texIdx = entry.getKey();
            List<float[]> vList = entry.getValue();
            List<Integer> iList = idxMap.get(texIdx);
            if (vList.isEmpty()) continue;

            FaceBatch batch = new FaceBatch();

            // Flatten vertex list
            batch.vertexCount = vList.size();
            batch.verts = new float[batch.vertexCount * 7];
            for (int i = 0; i < batch.vertexCount; i++) {
                float[] v = vList.get(i);
                System.arraycopy(v, 0, batch.verts, i * 7, 7);
            }

            // Flatten index list
            batch.indexCount = iList.size();
            batch.indices = new int[batch.indexCount];
            for (int i = 0; i < batch.indexCount; i++) batch.indices[i] = iList.get(i);

            Mesh mesh = new Mesh(true, batch.vertexCount, batch.indexCount, attrs);
            mesh.setVertices(batch.verts);
            mesh.setIndices(toShortArray(batch.indices));
            batch.mesh = mesh;

            // Load texture
            batch.texture = loadTexture(textureBasePath, texIdx);

            batches.put(texIdx, batch);
        }
    }

    /** Polygon / mesh face — use meshVerts as index list. */
    private void buildMeshFace(BSPFile.Face face,
                                List<float[]> verts, List<Integer> indices) {
        int baseVertex = verts.size();

        for (int i = 0; i < face.numVertices; i++) {
            BSPFile.Vertex v = bsp.vertices[face.firstVertex + i];
            verts.add(toGLVertex(v));
        }
        for (int i = 0; i < face.numMeshVerts; i++) {
            indices.add(baseVertex + bsp.meshVerts[face.firstMeshVert + i]);
        }
    }

    /** Bezier patch — tessellate and append. */
    private void buildPatchFace(BSPFile.Face face,
                                 List<float[]> verts, List<Integer> indices) {
        int baseVertex = verts.size();
        List<float[]>  pVerts   = new ArrayList<>();
        List<Integer>  pIndices = new ArrayList<>();
        tree.tessellatePatch(face, PATCH_LEVEL, pVerts, pIndices);
        verts.addAll(pVerts); // already GL-swizzled inside tessellate
        for (int idx : pIndices) indices.add(baseVertex + idx);
    }

    // -----------------------------------------------------------------------
    // Per-frame render
    // -----------------------------------------------------------------------

    public void render(Camera camera, float camX, float camY, float camZ) {
        int cameraLeaf = tree.findLeaf(camX, camY, camZ);

        // Ensure faceStamp is big enough
        java.util.Arrays.fill(faceStamp, 0,
            Math.min(faceStamp.length, bsp.faces.length), false);

        int faceCount = tree.collectVisibleFaces(cameraLeaf, visibleFaces, faceStamp);

        // Mark which batches are needed
        for (FaceBatch b : batches.values()) b.mesh.getNumVertices(); // keep alive
        boolean[] batchUsed = new boolean[bsp.textures != null ? bsp.textures.length : 0];
        for (int i = 0; i < faceCount; i++) {
            int fi  = visibleFaces[i];
            int tex = bsp.faces[fi].textureIndex;
            if (tex >= 0 && tex < batchUsed.length) batchUsed[tex] = true;
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        //Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        //Gdx.gl.glCullFace(GL20.GL_BACK);

        shader.bind();
        shader.setUniformMatrix("u_mvp", camera.combined);
        shader.setUniformi("u_base",     0);
        shader.setUniformi("u_lightmap", 1);

        // White fallback lightmap for faces without one
        Texture whiteLM = getOrCreateWhiteLightmap();

        for (Map.Entry<Integer, FaceBatch> entry : batches.entrySet()) {
            int texIdx = entry.getKey();
            if (texIdx >= batchUsed.length || !batchUsed[texIdx]) continue;
            FaceBatch batch = entry.getValue();
            if (batch.mesh == null) continue;

            int lmIdx = findFirstLightmapForTexture(texIdx);
            Texture lm = (lmIdx >= 0 && lmIdx < lightmapTextures.length)
                ? lightmapTextures[lmIdx] : whiteLM;

            if (batch.texture != null) {
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                batch.texture.bind();
            }
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
            lm.bind();
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            shader.setUniformi("u_base", 0);
            shader.setUniformi("u_lightmap", 1);
            shader.setUniformf("u_hasLightmap", lm == whiteLM ? 0f : 1f);

            batch.mesh.render(shader, GL20.GL_TRIANGLES);
        }

        //Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Q3 (x,y,z) → GL (x, z, -y), plus both UV sets. */
    private static float[] toGLVertex(BSPFile.Vertex v) {
        // Inset lightmap UVs by half a pixel to prevent edge bleeding
        float lmSize = BSPFile.LIGHTMAP_SIZE; // 128
        float inset = 0.5f / lmSize;
        float u1 = Math.max(inset, Math.min(1f - inset, v.u1));
        float v1 = Math.max(inset, Math.min(1f - inset, v.v1));
        return new float[]{ v.x, v.z, -v.y, v.u0, v.v0, u1, v1 };
    }

    private Texture loadTexture(String basePath, int texIdx) {
        if (bsp.textures == null || texIdx < 0 || texIdx >= bsp.textures.length) {
            return checkerTexture();
        }
        String name = bsp.textures[texIdx].name;
        // Try TGA first, then JPG
        for (String ext : new String[]{ ".tga", ".jpg", ".png" }) {
            FileHandle fh = Gdx.files.internal(basePath + "/" + name + ext);
            if (fh.exists()) {
                Texture t = new Texture(fh, true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat); // add this
                return t;
            }
        }
        return checkerTexture();
    }

    private int findFirstLightmapForTexture(int texIdx) {
        for (BSPFile.Face f : bsp.faces) {
            if (f.textureIndex == texIdx && f.lightmapIndex >= 0) return f.lightmapIndex;
        }
        return -1;
    }

    private Texture whiteLightmap;
    private Texture getOrCreateWhiteLightmap() {
        if (whiteLightmap == null) {
            Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGB888);
            pm.setColor(1, 1, 1, 1);
            pm.fill();
            whiteLightmap = new Texture(pm);
            pm.dispose();
        }
        return whiteLightmap;
    }

    private Texture checker;
    private Texture checkerTexture() {
        if (checker == null) {
            Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                pm.drawPixel(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xFF00FFFF : 0x000000FF);
            }
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
        if (shader != null) shader.dispose();
        if (whiteLightmap != null) whiteLightmap.dispose();
        if (checker != null) checker.dispose();
        for (Texture t : lightmapTextures) if (t != null) t.dispose();
        for (FaceBatch b : batches.values()) b.dispose();
    }
}

