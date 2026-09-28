package io.github.tomdong2010.fkr.redis;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** One Redis container shared by all Redis tests in the JVM. */
public final class RedisTestSupport {
    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private RedisTestSupport() {
    }

    public static synchronized void start() {
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
    }
}
