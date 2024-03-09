package io.wdsj.imp.impl.util;

public class Vector2i {
    private int x, z;

    public Vector2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (x);
        result = prime * result + (z);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        // 在Java 8中，需要显式地进行类型转换, fuck!!!!!
        Vector2i other = (Vector2i) obj;

        return x == other.x && z == other.z;
    }
}
