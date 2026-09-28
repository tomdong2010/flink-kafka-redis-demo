# Changelog

## [2.0.0] - 2026-09-28

Rewritten as a real-time trending and GMV pipeline on current versions.

### Added
- Simulated shop traffic with flash sales, out-of-order and late events.
- Flink 2.2 job: event time with watermarks, top-N products over a sliding window, revenue per
  category over tumbling windows, checkpoints, stable operator uids.
- Redis sink on the Sink V2 API; idempotent writes, and a Lua script that never lets an older
  window overwrite a newer one.
- Web dashboard (no external dependencies) served from the same jar.
- Malformed records are skipped and counted instead of failing the job.
- Tests (Flink MiniCluster, Testcontainers Redis), CI with an end-to-end smoke test.
- English and Simplified Chinese documentation.

### Changed
- Kafka 4.2 in KRaft mode (no ZooKeeper), Redis 7.4, Java 17. One Docker image runs every part.
- Configuration via `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, ... (`EXAMPLE_KAFKA_SERVER` and
  `EXAMPLE_KAFKA_TOPIC` are still accepted).

### Removed
- The Spark and plain Kafka consumers, to keep the project focused on Flink.
- ZooKeeper, Kafka Manager and `wait-for-it.sh`.

### Fixed
- The image could not be built: the Dockerfile copied a jar name the build did not produce.
- The Redis sink pointed at a hard-coded container IP and port, and used a Scala 2.10 connector
  incompatible with the rest of the build.
- log4j 2.11 (Log4Shell, CVE-2021-44228) is gone.

[2.0.0]: https://github.com/tomdong2010/flink-kafka-redis-demo/releases/tag/v2.0.0
