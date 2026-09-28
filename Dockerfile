# syntax=docker/dockerfile:1
# One image for everything: the Flink JobManager/TaskManager run the job jar from usrlib/,
# and docker/app.sh runs the producer and the dashboard with the same libraries.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM flink:2.2.1-java17
LABEL org.opencontainers.image.source=https://github.com/tomdong2010/flink-kafka-redis-demo
LABEL org.opencontainers.image.licenses=MIT
COPY docker/log4j-app.properties /opt/flink/conf/log4j-app.properties
COPY docker/app.sh /opt/flink/bin/app.sh
COPY --from=build /src/target/flink-kafka-redis-demo.jar /opt/flink/usrlib/flink-kafka-redis-demo.jar
# Checkpoints go to a volume shared by the JobManager and TaskManager; the image runs as "flink".
RUN mkdir -p /opt/flink/checkpoints && chown flink:flink /opt/flink/checkpoints
