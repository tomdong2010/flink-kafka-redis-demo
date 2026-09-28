package io.github.tomdong2010.fkr.producer;

import io.github.tomdong2010.fkr.config.Settings;
import io.github.tomdong2010.fkr.model.UserEvent;
import io.github.tomdong2010.fkr.serde.Json;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Publishes simulated {@link UserEvent}s to Kafka at a steady rate. */
public final class EventProducer {
    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);
    static final int PARTITIONS = 3;

    private EventProducer() {
    }

    public static void run(Settings settings) throws Exception {
        ensureTopic(settings);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.kafkaBootstrapServers);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "event-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        EventGenerator generator = new EventGenerator(System.nanoTime(), System::currentTimeMillis, settings.lateEventRatio);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong sent = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            try {
                main.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        long intervalNanos = TimeUnit.SECONDS.toNanos(1) / settings.eventsPerSecond;
        LOG.info("Producing {} events/s to topic '{}' on {}", settings.eventsPerSecond, settings.kafkaTopic,
                settings.kafkaBootstrapServers);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            long next = System.nanoTime();
            long lastReport = System.currentTimeMillis();
            while (running.get()) {
                UserEvent event = generator.next();
                // Keyed by user, so one user's actions stay in order within a partition.
                producer.send(new ProducerRecord<>(settings.kafkaTopic, event.userId, Json.MAPPER.writeValueAsString(event)),
                        (metadata, error) -> {
                            if (error == null) {
                                sent.incrementAndGet();
                            } else if (failed.incrementAndGet() % 100 == 1) {
                                LOG.warn("Send failed", error);
                            }
                        });

                next += intervalNanos;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    TimeUnit.NANOSECONDS.sleep(sleep);
                }
                if (System.currentTimeMillis() - lastReport >= 10_000) {
                    lastReport = System.currentTimeMillis();
                    LOG.info("Sent {} events ({} failed), flash sale on: {}", sent.get(), failed.get(),
                            generator.hottestProductId());
                }
            }
            producer.flush();
        }
        LOG.info("Producer stopped after {} events", sent.get());
    }

    /** Creates the topic if it does not exist, retrying until the broker is reachable. */
    static void ensureTopic(Settings settings) throws InterruptedException {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, settings.kafkaBootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10_000);
        for (int attempt = 1; ; attempt++) {
            try (Admin admin = Admin.create(props)) {
                admin.createTopics(List.of(new NewTopic(settings.kafkaTopic, PARTITIONS, (short) 1))).all().get();
                LOG.info("Created topic '{}' with {} partitions", settings.kafkaTopic, PARTITIONS);
                return;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    LOG.info("Topic '{}' already exists", settings.kafkaTopic);
                    return;
                }
                if (attempt >= 30) {
                    throw new IllegalStateException("Kafka is not reachable at " + settings.kafkaBootstrapServers, e);
                }
                LOG.info("Waiting for Kafka at {} ({})", settings.kafkaBootstrapServers, e.getCause().getMessage());
                TimeUnit.SECONDS.sleep(2);
            }
        }
    }
}
