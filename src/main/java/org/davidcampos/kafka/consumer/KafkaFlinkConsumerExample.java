package org.davidcampos.kafka.consumer;


import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.davidcampos.kafka.commons.Commons;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;
import scala.Int;

import java.util.Properties;

public class KafkaFlinkConsumerExample {
    private static final Logger logger = LogManager.getLogger(KafkaFlinkConsumerExample.class);

    public static void main(final String... args) {
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Properties
        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Commons.EXAMPLE_KAFKA_SERVER);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "FlinkConsumerGroup");

        DataStream<String> messageStream = env.addSource(new FlinkKafkaConsumer010<>(Commons.EXAMPLE_KAFKA_TOPIC, new SimpleStringSchema(), props));
        // Split up the lines in pairs (2-tuples) containing: (word,1)
//        messageStream.flatMap(new Tokenizer())
//                // group by the tuple field "0" and sum up tuple field "1"
//                .keyBy(0)
//                .sum(1)
//                .print();

        FlinkJedisPoolConfig jedis = new FlinkJedisPoolConfig.Builder().setHost("172.29.0.2").setPort(6379).build();
        messageStream.addSink(new RedisSink<>(jedis,new MyRedis())); //向Redis写入数据

        try {
            env.execute();
        } catch (Exception e) {
            logger.error("An error occurred.", e);
        }
    }

    public static final class Tokenizer implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            // normalize and split the line
            String[] tokens = value.toLowerCase().split("\\W+");

            // emit the pairs
            for (String token : tokens) {
                if (token.length() > 0) {
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
    }
    // 该类写在 main 函数外面
    public static class MyRedis implements RedisMapper<String>{

        @Override
        public RedisCommandDescription getCommandDescription() {
            return new RedisCommandDescription(RedisCommand.RPUSH,"mytable");
        }

        @Override
        public String getKeyFromData(String s) {
            return s;
        }

        @Override
        public String getValueFromData(String s) {
            return s;
        }
    }

}
