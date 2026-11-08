package com.factionclash.logic;

/**
 * Stock of one resource sampled over time (a ring of the last {@value #CAPACITY} samples): its trend per in-game day
 * and how long it lasts at that rate. Pure logic, so the arithmetic is unit-tested.
 */
public final class Ledger {
    public static final int CAPACITY = 32;
    public static final long DAY = 24000;

    private final long[] times = new long[CAPACITY];
    private final int[] values = new int[CAPACITY];
    private int size, head;

    public void record(long time, int value) {
        times[head] = time;
        values[head] = value;
        head = (head + 1) % CAPACITY;
        size = Math.min(size + 1, CAPACITY);
    }

    public int size() {
        return size;
    }

    private int index(int i) { // i = 0 oldest .. size-1 newest
        return Math.floorMod(head - size + i, CAPACITY);
    }

    public int latest() {
        return size == 0 ? 0 : values[index(size - 1)];
    }

    /**
     * Change per day, measured from the oldest sample of the last day to the newest; NaN when the samples cover less
     * than a tenth of a day.
     */
    public double perDay() {
        if (size < 2) {
            return Double.NaN;
        }
        long newest = times[index(size - 1)];
        int from = 0;
        while (from < size - 1 && newest - times[index(from)] > DAY) {
            from++;
        }
        long span = newest - times[index(from)];
        if (span < DAY / 10) {
            return Double.NaN;
        }
        return (values[index(size - 1)] - values[index(from)]) * (double) DAY / span;
    }

    /** Days until the stock is gone at the current rate; infinite when it is not falling. */
    public double daysLeft() {
        double r = perDay();
        if (Double.isNaN(r) || r >= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return latest() / -r;
    }

    public long[] times() {
        long[] out = new long[size];
        for (int i = 0; i < size; i++) {
            out[i] = times[index(i)];
        }
        return out;
    }

    public int[] values() {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            out[i] = values[index(i)];
        }
        return out;
    }

    public static Ledger of(long[] t, int[] v) {
        Ledger l = new Ledger();
        for (int i = 0; i < Math.min(t.length, v.length); i++) {
            l.record(t[i], v[i]);
        }
        return l;
    }
}
