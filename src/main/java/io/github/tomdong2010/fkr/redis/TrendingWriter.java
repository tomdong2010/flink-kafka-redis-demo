package io.github.tomdong2010.fkr.redis;

import io.github.tomdong2010.fkr.model.ProductScore;
import io.github.tomdong2010.fkr.model.TopProducts;
import redis.clients.jedis.Pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the leaderboard with the top products of a window, atomically and only if that
 * window is not older than the one already stored. Replays after a failure, or results arriving
 * out of order from parallel subtasks, can therefore never move the leaderboard backwards.
 */
public class TrendingWriter implements RedisWriter<TopProducts> {
    private static final long serialVersionUID = 1L;

    static final String SCRIPT = String.join("\n",
            "local current = tonumber(redis.call('GET', KEYS[2]) or '-1')",
            "if tonumber(ARGV[1]) < current then return 0 end",
            "redis.call('DEL', KEYS[1])",
            "for i = 2, #ARGV, 2 do",
            "  redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i + 1])",
            "end",
            "redis.call('SET', KEYS[2], ARGV[1])",
            "return 1");

    @Override
    public void write(Pipeline pipeline, TopProducts top) {
        List<String> args = new ArrayList<>(1 + 2 * top.items.length);
        args.add(Long.toString(top.windowEnd));
        for (ProductScore item : top.items) {
            args.add(Long.toString(item.score));
            args.add(item.productId);
        }
        pipeline.eval(SCRIPT, List.of(RedisKeys.TRENDING, RedisKeys.TRENDING_WINDOW_END), args);
    }
}
