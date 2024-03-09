package io.wdsj.imp.impl.util;

public class ChunkHelper {
    public static long chunkPositionToLong(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL);
    }
}
