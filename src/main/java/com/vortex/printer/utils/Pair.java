package com.vortex.printer.utils;

public class Pair<L, R> {
    private final L left;
    private final R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public R getRight() {
        return right;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) obj;
        return java.util.Objects.equals(left, pair.left) && java.util.Objects.equals(right, pair.right);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(left, right);
    }
}
