
package com.dbtraining.reconx.service;
import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.dto.ReconSummary;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ReconSummaryCollector implements Collector<ReconResult, long[], ReconSummary> {

    @Override
    public Supplier<long[]> supplier() {
        // Use an array to store counts: [0] = total, [1] = matched, [2] = broken
        return () -> new long[3];
    }

    @Override
    public BiConsumer<long[], ReconResult> accumulator() {
        return (acc, res) -> {
            acc[0]++; // Increment total count
            if (res.status() == ReconResult.Status.MATCHED) {
                acc[1]++; // Increment MATCHED count
            } else if (res.status() == ReconResult.Status.BREAK) {
                acc[2]++; // Increment BREAK count
            }
        };
    }

    @Override
    public BinaryOperator<long[]> combiner() {
        return (acc1, acc2) -> {
            // Merge counts when running in parallel streams
            acc1[0] += acc2[0];
            acc1[1] += acc2[1];
            acc1[2] += acc2[2];
            return acc1;
        };
    }

    @Override
    public Function<long[], ReconSummary> finisher() {
        // Transform the accumulated array counts into the final ReconSummary record
        return acc -> new ReconSummary(acc[0], acc[1], acc[2]);
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
}
