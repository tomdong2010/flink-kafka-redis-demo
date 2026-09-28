package io.github.tomdong2010.fkr.redis;

import io.github.tomdong2010.fkr.model.CategoryRevenue;
import redis.clients.jedis.Pipeline;

/**
 * Stores each category's revenue under its window. Every command overwrites with an absolute
 * value (no increments), so writing the same result twice is harmless.
 */
public class RevenueWriter implements RedisWriter<CategoryRevenue> {
    private static final long serialVersionUID = 1L;

    @Override
    public void write(Pipeline pipeline, CategoryRevenue r) {
        String key = RedisKeys.gmvWindow(r.windowStart);
        pipeline.hset(key, r.category, Double.toString(r.revenue));
        pipeline.hset(key, r.category + ":orders", Long.toString(r.orders));
        pipeline.expire(key, RedisKeys.GMV_TTL_SECONDS);
        pipeline.zadd(RedisKeys.GMV_WINDOWS, r.windowStart, Long.toString(r.windowStart));
        pipeline.zremrangeByRank(RedisKeys.GMV_WINDOWS, 0, -(RedisKeys.GMV_WINDOWS_KEPT + 1));
    }
}
