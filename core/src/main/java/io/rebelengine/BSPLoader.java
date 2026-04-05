/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

import java.io.*;
import java.nio.*;
import java.nio.file.*;

/**
 * Loads a Quake 3 BSP46 binary file into a BSP46File object.
 * All values are little-endian.
 *
 * Usage:
 *   BSP46File bsp = BSP46Loader.load(Paths.get("maps/q3dm1.bsp"));
 */
public class BSPLoader {

    public static BSPFile load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        return load(buf);
    }

    public static BSPFile load(ByteBuffer buf) throws IOException {
        BSPFile bsp = new BSPFile();

        // Header
        int magic   = buf.getInt();
        int version = buf.getInt();

        if (magic != BSPFile.MAGIC) {
            throw new IOException(String.format(
                "Not a Q3 BSP file (magic 0x%08X, expected 0x%08X)", magic, BSPFile.MAGIC));
        }
        if (version != BSPFile.VERSION) {
            throw new IOException("Unsupported BSP version: " + version +
                " (expected " + BSPFile.VERSION + ")");
        }

        // Lump directory — 17 lumps × 8 bytes each
        for (int i = 0; i < BSPFile.LUMP_COUNT; i++) {
            BSPFile.Lump lump = new BSPFile.Lump();
            lump.offset = buf.getInt();
            lump.length = buf.getInt();
            bsp.lumps[i] = lump;
        }

        readEntities(bsp, buf);
        readTextures(bsp, buf);
        readPlanes(bsp, buf);
        readNodes(bsp, buf);
        readLeaves(bsp, buf);
        readLeafFaces(bsp, buf);
        readLeafBrushes(bsp, buf);
        readModels(bsp, buf);
        readBrushes(bsp, buf);
        readBrushSides(bsp, buf);
        readVertices(bsp, buf);
        readMeshVerts(bsp, buf);
        readEffects(bsp, buf);
        readFaces(bsp, buf);
        readLightmaps(bsp, buf);
        readVisibility(bsp, buf);

        return bsp;
    }

    // -----------------------------------------------------------------------
    // Per-lump readers
    // -----------------------------------------------------------------------

    private static void readEntities(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_ENTITIES];
        if (lump.length == 0) { bsp.entities = ""; return; }
        byte[] bytes = new byte[lump.length];
        buf.position(lump.offset);
        buf.get(bytes);
        bsp.entities = new String(bytes).trim();
    }

    private static void readTextures(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_TEXTURES];
        int count = lump.length / 72; // 64 bytes name + 2 ints
        bsp.textures = new BSPFile.Texture[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Texture t = new BSPFile.Texture();
            byte[] nameBytes = new byte[64];
            buf.get(nameBytes);
            t.name = nullTerminated(nameBytes);
            t.surfaceFlags  = buf.getInt();
            t.contentFlags  = buf.getInt();
            bsp.textures[i] = t;
        }
    }

    private static void readPlanes(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_PLANES];
        int count = lump.length / 16; // 4 floats
        bsp.planes = new BSPFile.Plane[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Plane p = new BSPFile.Plane();
            p.nx   = buf.getFloat();
            p.ny   = buf.getFloat();
            p.nz   = buf.getFloat();
            p.dist = buf.getFloat();
            bsp.planes[i] = p;
        }
    }

    private static void readNodes(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_NODES];
        int count = lump.length / 36; // 1 int + 2 ints + 3 ints + 3 ints
        bsp.nodes = new BSPFile.Node[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Node n = new BSPFile.Node();
            n.planeIndex  = buf.getInt();
            n.children[0] = buf.getInt();
            n.children[1] = buf.getInt();
            n.minX = buf.getInt(); n.minY = buf.getInt(); n.minZ = buf.getInt();
            n.maxX = buf.getInt(); n.maxY = buf.getInt(); n.maxZ = buf.getInt();
            bsp.nodes[i] = n;
        }
    }

    private static void readLeaves(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_LEAVES];
        int count = lump.length / 48;
        bsp.leaves = new BSPFile.Leaf[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Leaf leaf = new BSPFile.Leaf();
            leaf.cluster          = buf.getInt();
            leaf.area             = buf.getInt();
            leaf.minX = buf.getInt(); leaf.minY = buf.getInt(); leaf.minZ = buf.getInt();
            leaf.maxX = buf.getInt(); leaf.maxY = buf.getInt(); leaf.maxZ = buf.getInt();
            leaf.firstLeafFace    = buf.getInt();
            leaf.numLeafFaces     = buf.getInt();
            leaf.firstLeafBrush   = buf.getInt();
            leaf.numLeafBrushes   = buf.getInt();
            bsp.leaves[i] = leaf;
        }
    }

    private static void readLeafFaces(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_LEAFFACES];
        int count = lump.length / 4;
        bsp.leafFaces = new int[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) bsp.leafFaces[i] = buf.getInt();
    }

    private static void readLeafBrushes(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_LEAFBRUSHES];
        int count = lump.length / 4;
        bsp.leafBrushes = new int[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) bsp.leafBrushes[i] = buf.getInt();
    }

    private static void readModels(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_MODELS];
        int count = lump.length / 40;
        bsp.models = new BSPFile.Model[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Model m = new BSPFile.Model();
            m.minX = buf.getFloat(); m.minY = buf.getFloat(); m.minZ = buf.getFloat();
            m.maxX = buf.getFloat(); m.maxY = buf.getFloat(); m.maxZ = buf.getFloat();
            m.firstFace  = buf.getInt(); m.numFaces   = buf.getInt();
            m.firstBrush = buf.getInt(); m.numBrushes = buf.getInt();
            bsp.models[i] = m;
        }
    }

    private static void readBrushes(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_BRUSHES];
        int count = lump.length / 12;
        bsp.brushes = new BSPFile.Brush[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Brush b = new BSPFile.Brush();
            b.firstSide    = buf.getInt();
            b.numSides     = buf.getInt();
            b.textureIndex = buf.getInt();
            bsp.brushes[i] = b;
        }
    }

    private static void readBrushSides(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_BRUSHSIDES];
        int count = lump.length / 8;
        bsp.brushSides = new BSPFile.BrushSide[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.BrushSide bs = new BSPFile.BrushSide();
            bs.planeIndex   = buf.getInt();
            bs.textureIndex = buf.getInt();
            bsp.brushSides[i] = bs;
        }
    }

    private static void readVertices(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_VERTICES];
        int count = lump.length / 44; // 3+2+2+3 floats + 4 bytes = 44
        bsp.vertices = new BSPFile.Vertex[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Vertex v = new BSPFile.Vertex();
            v.x  = buf.getFloat(); v.y  = buf.getFloat(); v.z  = buf.getFloat();
            v.u0 = buf.getFloat(); v.v0 = buf.getFloat(); // surface UV
            v.u1 = buf.getFloat(); v.v1 = buf.getFloat(); // lightmap UV
            v.nx = buf.getFloat(); v.ny = buf.getFloat(); v.nz = buf.getFloat();
            v.r  = buf.get();      v.g  = buf.get();
            v.b  = buf.get();      v.a  = buf.get();
            bsp.vertices[i] = v;
        }
    }

    private static void readMeshVerts(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_MESHVERTS];
        int count = lump.length / 4;
        bsp.meshVerts = new int[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) bsp.meshVerts[i] = buf.getInt();
    }

    private static void readEffects(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_EFFECTS];
        int count = lump.length / 72;
        bsp.effects = new BSPFile.Effect[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Effect e = new BSPFile.Effect();
            byte[] nameBytes = new byte[64];
            buf.get(nameBytes);
            e.name         = nullTerminated(nameBytes);
            e.brushIndex   = buf.getInt();
            e.unknown      = buf.getInt();
            bsp.effects[i] = e;
        }
    }

    private static void readFaces(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_FACES];
        int count = lump.length / 104;
        bsp.faces = new BSPFile.Face[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Face f = new BSPFile.Face();
            f.textureIndex  = buf.getInt();
            f.effectIndex   = buf.getInt();
            f.type          = buf.getInt();
            f.firstVertex   = buf.getInt();
            f.numVertices   = buf.getInt();
            f.firstMeshVert = buf.getInt();
            f.numMeshVerts  = buf.getInt();
            f.lightmapIndex = buf.getInt();
            f.lightmapX     = buf.getInt();
            f.lightmapY     = buf.getInt();
            f.lightmapWidth  = buf.getInt();
            f.lightmapHeight = buf.getInt();
            f.lightmapOriginX = buf.getFloat(); f.lightmapOriginY = buf.getFloat(); f.lightmapOriginZ = buf.getFloat();
            f.lightmapSX      = buf.getFloat(); f.lightmapSY      = buf.getFloat(); f.lightmapSZ      = buf.getFloat();
            f.lightmapTX      = buf.getFloat(); f.lightmapTY      = buf.getFloat(); f.lightmapTZ      = buf.getFloat();
            f.normalX = buf.getFloat(); f.normalY = buf.getFloat(); f.normalZ = buf.getFloat();
            f.patchWidth  = buf.getInt();
            f.patchHeight = buf.getInt();
            bsp.faces[i] = f;
        }
    }

    private static void readLightmaps(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_LIGHTMAPS];
        int size  = BSPFile.LIGHTMAP_SIZE * BSPFile.LIGHTMAP_SIZE * 3;
        int count = lump.length / size;
        bsp.lightmaps = new BSPFile.Lightmap[count];
        buf.position(lump.offset);
        for (int i = 0; i < count; i++) {
            BSPFile.Lightmap lm = new BSPFile.Lightmap();
            lm.rgb = new byte[size];
            buf.get(lm.rgb);
            bsp.lightmaps[i] = lm;
        }
    }

    private static void readVisibility(BSPFile bsp, ByteBuffer buf) {
        BSPFile.Lump lump = bsp.lumps[BSPFile.LUMP_VISIBILITY];
        if (lump.length == 0) { bsp.visibility = new BSPFile.Visibility(); return; }
        buf.position(lump.offset);
        BSPFile.Visibility vis = new BSPFile.Visibility();
        vis.numClusters      = buf.getInt();
        vis.bytesPerCluster  = buf.getInt();
        int dataSize = vis.numClusters * vis.bytesPerCluster;
        vis.data = new byte[dataSize];
        buf.get(vis.data);
        bsp.visibility = vis;
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------
    private static String nullTerminated(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) len++;
        return new String(bytes, 0, len);
    }
}
