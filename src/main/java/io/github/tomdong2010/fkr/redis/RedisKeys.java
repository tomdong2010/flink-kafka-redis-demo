package io.github.tomdong2010.fkr.redis;

/**
 * Redis keys written by the job and read by the dashboard. Keys that change together share a
 * {@code {hash tag}} so the scripts also work on Redis Cluster.
 */
public final class RedisKeys {
    /** Sorted set: product id -> popularity score of the latest window. */
    public static final String TRENDING = "{trending}:products";
    /** String: end of the window stored in {@link #TRENDING}, epoch millis. */
    public static final String TRENDING_WINDOW_END = "{trending}:window_end";
    /** Sorted set of revenue window starts (score and member are both the start, epoch millis). */
    public static final String GMV_WINDOWS = "gmv:windows";
    /** How many revenue windows are kept. */
    public static final int GMV_WINDOWS_KEPT = 180;
    /** Seconds a revenue window hash lives. */
    public static final long GMV_TTL_SECONDS = 3_600;

    private RedisKeys() {
    }

    /** Hash for one revenue window: {@code <category>} -> revenue, {@code <category>:orders} -> orders. */
    public static String gmvWindow(long windowStart) {
        return "gmv:window:" + windowStart;
    }
}
