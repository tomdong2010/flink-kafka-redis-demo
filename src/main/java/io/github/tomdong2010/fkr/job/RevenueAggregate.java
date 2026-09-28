package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.UserEvent;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;

/** Sums revenue and counts orders for purchase events. The accumulator is (revenue, orders). */
public class RevenueAggregate implements AggregateFunction<UserEvent, Tuple2<Double, Long>, Tuple2<Double, Long>> {
    @Override
    public Tuple2<Double, Long> createAccumulator() {
        return Tuple2.of(0.0, 0L);
    }

    @Override
    public Tuple2<Double, Long> add(UserEvent event, Tuple2<Double, Long> acc) {
        return Tuple2.of(acc.f0 + event.price, acc.f1 + 1);
    }

    @Override
    public Tuple2<Double, Long> getResult(Tuple2<Double, Long> acc) {
        return acc;
    }

    @Override
    public Tuple2<Double, Long> merge(Tuple2<Double, Long> a, Tuple2<Double, Long> b) {
        return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1);
    }
}
