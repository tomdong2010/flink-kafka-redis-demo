#!/usr/bin/env bash
# Runs a non-Flink mode (producer or dashboard) with the Flink image's own libraries.
set -euo pipefail
exec java ${JAVA_OPTS:-} \
  -Dlog4j.configurationFile=/opt/flink/conf/log4j-app.properties \
  -cp "/opt/flink/lib/*:/opt/flink/usrlib/flink-kafka-redis-demo.jar" \
  io.github.tomdong2010.fkr.Main "$@"
