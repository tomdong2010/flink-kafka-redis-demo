package io.github.tomdong2010.fkr.redis;

import redis.clients.jedis.Pipeline;

import java.io.Serializable;

/**
 * Turns one record into Redis commands on a pipeline. Implementations must be idempotent: after
 * a failure Flink replays records since the last checkpoint, so the same record can be written
 * more than once.
 */
@FunctionalInterface
public interface RedisWriter<T> extends Serializable {
    void write(Pipeline pipeline, T value);
}
