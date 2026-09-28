package io.github.tomdong2010.fkr.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Application settings read from environment variables, with defaults that work for
 * {@code docker compose up} and for running from an IDE against {@code localhost}.
 */
public final class Settings implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String kafkaBootstrapServers;
    public final String kafkaTopic;
    public final String kafkaGroupId;
    public final String redisHost;
    public final int redisPort;
    public final int topN;
    public final Duration trendingWindow;
    public final Duration trendingSlide;
    public final Duration gmvWindow;
    public final Duration maxOutOfOrderness;
    public final Duration checkpointInterval;
    public final int eventsPerSecond;
    public final double lateEventRatio;
    public final int dashboardPort;

    private Settings(Map<String, String> env) {
        kafkaBootstrapServers = get(env, "KAFKA_BOOTSTRAP_SERVERS", get(env, "EXAMPLE_KAFKA_SERVER", "localhost:9092"));
        kafkaTopic = get(env, "KAFKA_TOPIC", get(env, "EXAMPLE_KAFKA_TOPIC", "user-events"));
        kafkaGroupId = get(env, "KAFKA_GROUP_ID", "flink-trending");
        redisHost = get(env, "REDIS_HOST", "localhost");
        redisPort = Integer.parseInt(get(env, "REDIS_PORT", "6379"));
        topN = Integer.parseInt(get(env, "TOP_N", "10"));
        trendingWindow = parseDuration(get(env, "TRENDING_WINDOW", "60s"));
        trendingSlide = parseDuration(get(env, "TRENDING_SLIDE", "5s"));
        gmvWindow = parseDuration(get(env, "GMV_WINDOW", "10s"));
        maxOutOfOrderness = parseDuration(get(env, "MAX_OUT_OF_ORDERNESS", "5s"));
        checkpointInterval = parseDuration(get(env, "CHECKPOINT_INTERVAL", "10s"));
        eventsPerSecond = Integer.parseInt(get(env, "EVENTS_PER_SECOND", "50"));
        lateEventRatio = Double.parseDouble(get(env, "LATE_EVENT_RATIO", "0.05"));
        dashboardPort = Integer.parseInt(get(env, "DASHBOARD_PORT", "8088"));

        require(topN > 0, "TOP_N must be positive");
        require(eventsPerSecond > 0, "EVENTS_PER_SECOND must be positive");
        require(lateEventRatio >= 0 && lateEventRatio <= 1, "LATE_EVENT_RATIO must be between 0 and 1");
        require(trendingWindow.compareTo(trendingSlide) >= 0, "TRENDING_WINDOW must not be shorter than TRENDING_SLIDE");
    }

    /** Settings from the process environment. */
    public static Settings fromEnv() {
        return new Settings(System.getenv());
    }

    /** Settings from an explicit map, for tests. */
    public static Settings of(Map<String, String> env) {
        return new Settings(env);
    }

    /** Parses durations such as {@code 500ms}, {@code 5s}, {@code 2m} or {@code 1h}. */
    public static Duration parseDuration(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2)));
            }
            long amount = Long.parseLong(v.substring(0, v.length() - 1));
            switch (v.charAt(v.length() - 1)) {
                case 's':
                    return Duration.ofSeconds(amount);
                case 'm':
                    return Duration.ofMinutes(amount);
                case 'h':
                    return Duration.ofHours(amount);
                default:
                    break;
            }
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            // fall through to the error below
        }
        throw new IllegalArgumentException("invalid duration '" + value + "' (use e.g. 500ms, 5s, 2m, 1h)");
    }

    private static String get(Map<String, String> env, String key, String def) {
        String v = env.get(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
