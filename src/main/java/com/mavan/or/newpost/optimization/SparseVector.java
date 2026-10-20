package com.mavan.or.newpost.optimization;

import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * Compact mutable sparse vector backed by an open-addressing hash table.
 * Keys are non-negative variable/column indexes. Values whose absolute
 * magnitude is <= the supplied epsilon are removed.
 */
final class SparseVector {
    private static final int EMPTY = -1;
    private static final int TOMBSTONE = -2;
    private static final double ZERO_EPS = 1e-12;

    private int[] keys;
    private double[] values;
    private int size;
    private int used;

    SparseVector() {
        this(8);
    }

    SparseVector(int expectedEntries) {
        int capacity = 8;
        while (capacity < Math.max(8, expectedEntries * 2)) {
            capacity <<= 1;
        }
        keys = new int[capacity];
        values = new double[capacity];
        Arrays.fill(keys, EMPTY);
    }

    SparseVector copy() {
        SparseVector copy = new SparseVector();
        copy.keys = keys.clone();
        copy.values = values.clone();
        copy.size = size;
        copy.used = used;
        return copy;
    }

    double get(int key) {
        int slot = findSlot(key);
        return slot < 0 ? 0.0 : values[slot];
    }

    void set(int key, double value) {
        if (key < 0) throw new IllegalArgumentException("Sparse key must be non-negative");
        if (Math.abs(value) <= ZERO_EPS) {
            remove(key);
            return;
        }
        ensureCapacity();
        int slot = findInsertSlot(key);
        if (keys[slot] == key) {
            values[slot] = value;
            return;
        }
        if (keys[slot] == EMPTY) used++;
        keys[slot] = key;
        values[slot] = value;
        size++;
    }

    void add(int key, double delta) {
        if (Math.abs(delta) <= ZERO_EPS) return;
        if (key < 0) throw new IllegalArgumentException("Sparse key must be non-negative");
        ensureCapacity();
        int slot = findInsertSlot(key);
        if (keys[slot] == key) {
            double value = values[slot] + delta;
            if (Math.abs(value) <= ZERO_EPS) {
                removeAt(slot);
            } else {
                values[slot] = value;
            }
            return;
        }
        if (keys[slot] == EMPTY) used++;
        keys[slot] = key;
        values[slot] = delta;
        size++;
    }

    void scale(double factor) {
        if (Math.abs(factor - 1.0) <= ZERO_EPS) return;
        for (int i = 0; i < keys.length; i++) {
            int key = keys[i];
            if (key >= 0) values[i] *= factor;
        }
        compactZeros();
    }

    void forEach(BiConsumer<Integer, Double> consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] >= 0) consumer.accept(keys[i], values[i]);
        }
    }

    int size() {
        return size;
    }

    void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, 0.0);
        size = 0;
        used = 0;
    }

    private int hash(int key) {
        int x = key;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    private int findSlot(int key) {
        int mask = keys.length - 1;
        int slot = hash(key) & mask;
        while (true) {
            int current = keys[slot];
            if (current == EMPTY) return -1;
            if (current == key) return slot;
            slot = (slot + 1) & mask;
        }
    }

    private int findInsertSlot(int key) {
        int mask = keys.length - 1;
        int slot = hash(key) & mask;
        int firstTombstone = -1;
        while (true) {
            int current = keys[slot];
            if (current == EMPTY) return firstTombstone >= 0 ? firstTombstone : slot;
            if (current == key) return slot;
            if (current == TOMBSTONE && firstTombstone < 0) firstTombstone = slot;
            slot = (slot + 1) & mask;
        }
    }

    private void ensureCapacity() {
        if ((used + 1) * 10 < keys.length * 7) return;
        rehash(keys.length << 1);
    }

    private void remove(int key) {
        int slot = findSlot(key);
        if (slot >= 0) removeAt(slot);
    }

    private void removeAt(int slot) {
        keys[slot] = TOMBSTONE;
        values[slot] = 0.0;
        size--;
    }

    private void compactZeros() {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] >= 0 && Math.abs(values[i]) <= ZERO_EPS) removeAt(i);
        }
        if (used > size * 2 + 8) rehash(keys.length);
    }

    private void rehash(int newCapacity) {
        int[] oldKeys = keys;
        double[] oldValues = values;
        keys = new int[newCapacity];
        values = new double[newCapacity];
        Arrays.fill(keys, EMPTY);
        size = 0;
        used = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            int key = oldKeys[i];
            if (key < 0) continue;
            int slot = findInsertSlot(key);
            keys[slot] = key;
            values[slot] = oldValues[i];
            size++;
            used++;
        }
    }
}
