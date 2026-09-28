package io.github.tomdong2010.fkr.serde;

import io.github.tomdong2010.fkr.model.UserEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.metrics.Counter;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Turns Kafka records into {@link UserEvent}s. A record that is not valid JSON or misses a
 * required field is counted in the {@code malformedEvents} metric and skipped, instead of
 * failing the job and replaying the same poison record forever.
 */
public class UserEventDeserializer implements KafkaRecordDeserializationSchema<UserEvent> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(UserEventDeserializer.class);

    private transient Counter malformed;

    @Override
    public void open(DeserializationSchema.InitializationContext context) {
        malformed = context.getMetricGroup().counter("malformedEvents");
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<UserEvent> out) throws IOException {
        UserEvent event = parse(record.value());
        if (event == null) {
            if (malformed != null) {
                malformed.inc();
            }
            LOG.warn("Skipping malformed record at {}-{}@{}", record.topic(), record.partition(), record.offset());
            return;
        }
        out.collect(event);
    }

    /** Parses one record value, returning {@code null} when it is not a valid event. */
    public static UserEvent parse(byte[] value) {
        if (value == null) {
            return null;
        }
        try {
            UserEvent event = Json.MAPPER.readValue(value, UserEvent.class);
            return event != null && event.isValid() ? event : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public TypeInformation<UserEvent> getProducedType() {
        return TypeInformation.of(UserEvent.class);
    }
}
