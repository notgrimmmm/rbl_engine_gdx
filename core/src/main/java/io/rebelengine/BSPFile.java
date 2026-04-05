/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.rebelengine;

/**
 * File .bsp format bsp46
 */
public class BSPFile {

    // -----------------------------------------------------------------------
    // Format constants
    // -----------------------------------------------------------------------
    public static final int MAGIC   = 0x50534249; // "IBSP"
    public static final int VERSION = 46;

    public static final int LUMP_ENTITIES     = 0;
    public static final int LUMP_TEXTURES     = 1;
    public static final int LUMP_PLANES       = 2;
    public static final int LUMP_NODES        = 3;
    public static final int LUMP_LEAVES       = 4;
    public static final int LUMP_LEAFFACES    = 5;
    public static final int LUMP_LEAFBRUSHES  = 6;
    public static final int LUMP_MODELS       = 7;
    public static final int LUMP_BRUSHES      = 8;
    public static final int LUMP_BRUSHSIDES   = 9;
    public static final int LUMP_VERTICES     = 10;
    public static final int LUMP_MESHVERTS    = 11;
    public static final int LUMP_EFFECTS      = 12;
    public static final int LUMP_FACES        = 13;
    public static final int LUMP_LIGHTMAPS    = 14;
    public static final int LUMP_LIGHTGRID    = 15;
    public static final int LUMP_VISIBILITY   = 16;
    public static final int LUMP_COUNT        = 17;

    // Face types
    public static final int FACE_POLYGON   = 1;
    public static final int FACE_PATCH     = 2;
    public static final int FACE_MESH      = 3;
    public static final int FACE_BILLBOARD = 4;

    // Leaf contents flags
    public static final int CONTENTS_SOLID        = 1;
    public static final int CONTENTS_LAVA         = 8;
    public static final int CONTENTS_SLIME        = 16;
    public static final int CONTENTS_WATER        = 32;
    public static final int CONTENTS_FOG          = 64;
    public static final int CONTENTS_NOTTEAM1     = 0x0080;
    public static final int CONTENTS_NOTTEAM2     = 0x0100;

    // -----------------------------------------------------------------------
    // Lump descriptor
    // -----------------------------------------------------------------------
    public static class Lump {
        public int offset;
        public int length;
    }

    // -----------------------------------------------------------------------
    // Texture — just a name + surface/content flags.
    // The actual image is loaded separately (TGA/JPG from PK3).
    // -----------------------------------------------------------------------
    public static class Texture {
        public String name;       // up to 64 chars, relative path without extension
        public int    surfaceFlags;
        public int    contentFlags;
    }

    // -----------------------------------------------------------------------
    // Plane
    // -----------------------------------------------------------------------
    public static class Plane {
        public float nx, ny, nz;
        public float dist;
    }

    // -----------------------------------------------------------------------
    // Node
    // -----------------------------------------------------------------------
    public static class Node {
        public int   planeIndex;
        public int[] children = new int[2]; // negative = leaf (-(idx+1))
        // Integer AABB
        public int   minX, minY, minZ;
        public int   maxX, maxY, maxZ;
    }

    // -----------------------------------------------------------------------
    // Leaf — references a visibility cluster
    // -----------------------------------------------------------------------
    public static class Leaf {
        public int cluster;         // -1 = not in PVS
        public int area;
        public int minX, minY, minZ;
        public int maxX, maxY, maxZ;
        public int firstLeafFace;
        public int numLeafFaces;
        public int firstLeafBrush;
        public int numLeafBrushes;
    }

    // -----------------------------------------------------------------------
    // Model — brush model (doors, platforms etc.)
    // -----------------------------------------------------------------------
    public static class Model {
        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;
        public int   firstFace, numFaces;
        public int   firstBrush, numBrushes;
    }

    // -----------------------------------------------------------------------
    // Brush / BrushSide — for collision, not rendering
    // -----------------------------------------------------------------------
    public static class Brush {
        public int firstSide, numSides;
        public int textureIndex;
    }

    public static class BrushSide {
        public int planeIndex;
        public int textureIndex;
    }

    // -----------------------------------------------------------------------
    // Vertex — rich format with two UV sets, normal, and color
    // -----------------------------------------------------------------------
    public static class Vertex {
        public float x, y, z;          // world position
        public float u0, v0;            // surface UV (texture)
        public float u1, v1;            // lightmap UV
        public float nx, ny, nz;        // normal
        public byte  r, g, b, a;        // vertex color
    }

    // -----------------------------------------------------------------------
    // Face — the key structure; type determines how to render it
    // -----------------------------------------------------------------------
    public static class Face {
        public int textureIndex;
        public int effectIndex;     // -1 if none
        public int type;            // FACE_POLYGON, FACE_PATCH, FACE_MESH, FACE_BILLBOARD

        public int firstVertex;
        public int numVertices;

        public int firstMeshVert;   // index into meshVerts[]
        public int numMeshVerts;    // number of indices

        public int lightmapIndex;   // -1 if no lightmap
        public int lightmapX, lightmapY;        // corner in lightmap texture
        public int lightmapWidth, lightmapHeight;

        public float lightmapOriginX, lightmapOriginY, lightmapOriginZ;
        public float lightmapSX, lightmapSY, lightmapSZ; // S vector
        public float lightmapTX, lightmapTY, lightmapTZ; // T vector

        public float normalX, normalY, normalZ;

        // For FACE_PATCH only: control point grid dimensions
        public int patchWidth, patchHeight;
    }

    // -----------------------------------------------------------------------
    // Effect (shader reference for fog/portal etc.) — mostly ignored for now
    // -----------------------------------------------------------------------
    public static class Effect {
        public String name;
        public int    brushIndex;
        public int    unknown;
    }

    // -----------------------------------------------------------------------
    // Lightmap — always 128×128 RGB
    // -----------------------------------------------------------------------
    public static final int LIGHTMAP_SIZE = 128;

    public static class Lightmap {
        public byte[] rgb; // 128 * 128 * 3 bytes
    }

    // -----------------------------------------------------------------------
    // Visibility — cluster-based PVS
    // -----------------------------------------------------------------------
    public static class Visibility {
        public int    numClusters;
        public int    bytesPerCluster;
        public byte[] data;           // numClusters * bytesPerCluster bytes

        /** Returns true if clusterB is visible from clusterA. */
        public boolean isVisible(int clusterA, int clusterB) {
            if (clusterA < 0 || clusterB < 0) return true;
            if (data == null || data.length == 0) return true;
            int byteIdx = clusterA * bytesPerCluster + (clusterB >> 3);
            if (byteIdx >= data.length) return false;
            return (data[byteIdx] & (1 << (clusterB & 7))) != 0;
        }
    }

    // -----------------------------------------------------------------------
    // Assembled BSP data (filled by BSP46Loader)
    // -----------------------------------------------------------------------
    public Lump[]      lumps        = new Lump[LUMP_COUNT];
    public Texture[]   textures;
    public Plane[]     planes;
    public Node[]      nodes;
    public Leaf[]      leaves;
    public int[]       leafFaces;    // indices into faces[]
    public int[]       leafBrushes;
    public Model[]     models;
    public Brush[]     brushes;
    public BrushSide[] brushSides;
    public Vertex[]    vertices;
    public int[]       meshVerts;    // triangle indices relative to face.firstVertex
    public Effect[]    effects;
    public Face[]      faces;
    public Lightmap[]  lightmaps;
    public Visibility  visibility;
    public String      entities;
}

