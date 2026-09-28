package io.github.tomdong2010.fkr.redis;

import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.metrics.Counter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.io.IOException;

/**
 * A Flink Sink V2 that writes records to Redis through a pipeline. Buffered commands are sent
 * when the buffer fills up and on every checkpoint, so each checkpoint only completes after its
 * records are in Redis (at-least-once). Combined with idempotent {@link RedisWriter}s, replays
 * after a failure leave Redis in the same state as a failure-free run.
 */
public class RedisSink<T> implements Sink<T> {
    private static final long serialVersionUID = 1L;
    static final int MAX_BUFFERED = 500;

    private final String host;
    private final int port;
    private final RedisWriter<T> writer;

    public RedisSink(String host, int port, RedisWriter<T> writer) {
        this.host = host;
        this.port = port;
        this.writer = writer;
    }

    @Override
    public SinkWriter<T> createWriter(WriterInitContext context) {
        return new Writer<>(new Jedis(host, port), writer, context.metricGroup().counter("redisRecordsWritten"));
    }

    static final class Writer<T> implements SinkWriter<T> {
        private final Jedis jedis;
        private final RedisWriter<T> writer;
        private final Counter written;
        private Pipeline pipeline;
        private int buffered;

        Writer(Jedis jedis, RedisWriter<T> writer, Counter written) {
            this.jedis = jedis;
            this.writer = writer;
            this.written = written;
        }

        @Override
        public void write(T value, Context context) throws IOException {
            if (pipeline == null) {
                pipeline = jedis.pipelined();
            }
            writer.write(pipeline, value);
            if (++buffered >= MAX_BUFFERED) {
                sync();
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            sync();
        }

        private void sync() throws IOException {
            if (pipeline == null) {
                return;
            }
            Pipeline sent = pipeline;
            int count = buffered;
            pipeline = null;
            buffered = 0;
            // A pipeline reports failed commands as exceptions in its result list instead of
            // throwing, so check them: failing here fails the checkpoint and the task, and Flink
            // replays from the last checkpoint instead of silently losing the results.
            for (Object result : sent.syncAndReturnAll()) {
                if (result instanceof Exception) {
                    throw new IOException("Redis command failed", (Exception) result);
                }
            }
            written.inc(count);
        }

        @Override
        public void close() throws IOException {
            try {
                sync();
            } finally {
                jedis.close();
            }
        }
    }
}
