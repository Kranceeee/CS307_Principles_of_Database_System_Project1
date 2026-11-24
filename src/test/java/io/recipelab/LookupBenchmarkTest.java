package io.recipelab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LookupBenchmarkTest {
    @Test
    void includesTheFinalPartialGroupAndAvoidsDuplicateIdsInEachGroup() {
        var settings = new LookupBenchmark.Settings(11, 3, 2, 2);
        var input = List.of(101, 103, 107, 109);
        var groups = LookupBenchmark.workload(input, settings);

        assertEquals(List.of(3, 3, 3, 2), groups.stream().map(List::size).toList());
        assertEquals(groups, LookupBenchmark.workload(input, settings));
        assertTrue(groups.stream().allMatch(group -> new HashSet<>(group).size() == group.size()));
    }

    @Test
    void reportsMeasuredIdsAndStatementsSeparately() {
        var samples = List.of(new LookupBenchmark.GroupResult(25, 1, 2_000_000),
                new LookupBenchmark.GroupResult(2, 1, 3_000_000));
        var result = LookupBenchmark.summarize(samples, 500_000_000);

        assertEquals(27, result.ids());
        assertEquals(2, result.statements());
        assertEquals(54.0, result.idsPerSecond());
        assertEquals(4.0, result.statementsPerSecond());
    }

    @Test
    void computesNearestRankP95OfGroupLatencies() {
        var samples = IntStream.rangeClosed(1, 20)
                .mapToObj(ms -> new LookupBenchmark.GroupResult(1, 1, ms * 1_000_000L)).toList();

        assertEquals(19.0, LookupBenchmark.summarize(samples, 1_000_000_000).p95GroupMs());
    }

    @Test
    void rejectsImpossibleBatchSizes() {
        assertThrows(IllegalArgumentException.class, () -> new LookupBenchmark.Settings(500, 501, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> LookupBenchmark.workload(List.of(1, 2),
                new LookupBenchmark.Settings(100, 3, 1, 1)));
    }
}
