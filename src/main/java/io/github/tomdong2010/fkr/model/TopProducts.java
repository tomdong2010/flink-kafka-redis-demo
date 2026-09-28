package io.github.tomdong2010.fkr.model;

import java.util.Arrays;

/** The highest scoring products of one window, best first. */
public class TopProducts {
    public long windowEnd;
    /** An array rather than a List, so Flink serializes it as a POJO array instead of via Kryo. */
    public ProductScore[] items;

    public TopProducts() {
    }

    public TopProducts(long windowEnd, ProductScore[] items) {
        this.windowEnd = windowEnd;
        this.items = items;
    }

    @Override
    public String toString() {
        return "TopProducts{" + windowEnd + " " + Arrays.toString(items) + "}";
    }
}
