package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.UserEvent;
import org.apache.flink.api.common.functions.AggregateFunction;

/** Sums {@link UserEvent#popularityScore()} incrementally, so a window keeps one long per key. */
public class ScoreAggregate implements AggregateFunction<UserEvent, Long, Long> {
    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(UserEvent event, Long acc) {
        return acc + event.popularityScore();
    }

    @Override
    public Long getResult(Long acc) {
        return acc;
    }

    @Override
    public Long merge(Long a, Long b) {
        return a + b;
    }
}
