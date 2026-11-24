package io.recipelab;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class LookupBenchmark {
    record Settings(int lookups, int batchSize, int workers, int rounds) {
        Settings {
            if (lookups < 1 || lookups > 100_000 || batchSize < 1 || batchSize > 500 || batchSize > lookups
                    || workers < 1 || workers > 32 || rounds < 1 || rounds > 10) {
                throw new IllegalArgumentException("Use lookups 1–100000, batch-size 1–500 (at most lookups), workers 1–32, rounds 1–10");
            }
        }
    }

    record GroupResult(int ids, int statements, long nanos) {}
    record Result(int groups, int ids, int statements, double elapsedMs, double idsPerSecond,
                  double statementsPerSecond, double p95GroupMs) {}

    private enum Mode { POINT, ARRAY }

    private final DataSource dataSource;

    LookupBenchmark(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void run(Settings settings) throws Exception {
        var ids = JdbcClient.create(dataSource).sql("SELECT recipeid FROM recipe_lab.recipes ORDER BY recipeid")
                .query(Integer.class).list();
        var groups = workload(ids, settings);
        var warmup = groups.subList(0, Math.min(groups.size(), Math.max(20, settings.workers())));
        measure(Mode.POINT, warmup, settings.workers());
        measure(Mode.ARRAY, warmup, settings.workers());
        System.out.printf("Recipe rows: %,d | seed: 307 | worker threads: %d | batch size: %d%n",
                ids.size(), settings.workers(), settings.batchSize());
        System.out.println("round,mode,groups,verified_ids,completed_sql,wall_ms,verified_ids_s,completed_sql_s,group_p95_ms");
        for (int round = 1; round <= settings.rounds(); round++) {
            Mode first = round % 2 == 1 ? Mode.POINT : Mode.ARRAY;
            Mode second = first == Mode.POINT ? Mode.ARRAY : Mode.POINT;
            Result firstResult = measure(first, groups, settings.workers());
            Result secondResult = measure(second, groups, settings.workers());
            print(round, first, firstResult);
            print(round, second, secondResult);
        }
    }

    static List<List<Integer>> workload(List<Integer> ids, Settings settings) {
        if (ids.size() < settings.batchSize()) {
            throw new IllegalArgumentException("Import at least " + settings.batchSize() + " different recipes before this benchmark");
        }
        var shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, new Random(307));
        var groups = new ArrayList<List<Integer>>();
        for (int first = 0; first < settings.lookups(); first += settings.batchSize()) {
            var group = new ArrayList<Integer>();
            for (int i = first; i < Math.min(first + settings.batchSize(), settings.lookups()); i++) {
                group.add(shuffled.get(i % shuffled.size()));
            }
            groups.add(List.copyOf(group));
        }
        return groups;
    }

    private Result measure(Mode mode, List<List<Integer>> groups, int workers) throws Exception {
        int activeWorkers = Math.min(workers, groups.size());
        var executor = Executors.newFixedThreadPool(activeWorkers);
        try {
            var tasks = new ArrayList<Callable<List<GroupResult>>>();
            for (int i = 0; i < activeWorkers; i++) {
                int startAt = i;
                tasks.add(() -> runWorker(mode, groups, startAt, activeWorkers));
            }
            long start = System.nanoTime();
            var futures = executor.invokeAll(tasks);
            long elapsed = System.nanoTime() - start;
            var measured = new ArrayList<GroupResult>();
            for (var future : futures) measured.addAll(future.get());
            return summarize(measured, elapsed);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<GroupResult> runWorker(Mode mode, List<List<Integer>> groups, int startAt, int stride) throws SQLException {
        String predicate = mode == Mode.POINT ? "recipeid = ?" : "recipeid = ANY (?)";
        var measured = new ArrayList<GroupResult>();
        try (var connection = dataSource.getConnection();
             var sql = connection.prepareStatement("SELECT recipeid FROM recipe_lab.recipes WHERE " + predicate)) {
            sql.setQueryTimeout(30);
            for (int i = startAt; i < groups.size(); i += stride) {
                List<Integer> expected = groups.get(i);
                var found = new HashSet<Integer>();
                long start = System.nanoTime();
                int statements = 0;
                if (mode == Mode.POINT) {
                    for (int id : expected) {
                        sql.setInt(1, id);
                        readIds(sql, found);
                        statements++;
                    }
                } else {
                    var array = connection.createArrayOf("integer", expected.toArray(Integer[]::new));
                    try {
                        sql.setArray(1, array);
                        readIds(sql, found);
                        statements++;
                    } finally {
                        array.free();
                    }
                }
                long elapsed = System.nanoTime() - start;
                if (!found.equals(new HashSet<>(expected))) {
                    throw new SQLException("Lookup returned unexpected IDs in group " + i + " for " + mode);
                }
                measured.add(new GroupResult(found.size(), statements, elapsed));
            }
        }
        return measured;
    }

    private static void readIds(PreparedStatement query, HashSet<Integer> found) throws SQLException {
        try (var result = query.executeQuery()) {
            while (result.next()) {
                if (!found.add(result.getInt(1))) throw new SQLException("Lookup returned the same ID twice in one group");
            }
        }
    }

    static Result summarize(List<GroupResult> results, long elapsed) {
        int ids = results.stream().mapToInt(GroupResult::ids).sum();
        int statements = results.stream().mapToInt(GroupResult::statements).sum();
        long[] times = results.stream().mapToLong(GroupResult::nanos).sorted().toArray();
        double totalSeconds = elapsed / 1_000_000_000.0;
        long p95 = times[(int) Math.ceil(0.95 * times.length) - 1];
        return new Result(times.length, ids, statements, elapsed / 1_000_000.0, ids / totalSeconds,
                statements / totalSeconds, p95 / 1_000_000.0);
    }

    private static void print(int round, Mode mode, Result result) {
        System.out.printf(Locale.ROOT, "%d,%s,%d,%d,%d,%.3f,%.2f,%.2f,%.3f%n", round, mode,
                result.groups(), result.ids(), result.statements(), result.elapsedMs(), result.idsPerSecond(),
                result.statementsPerSecond(), result.p95GroupMs());
    }
}
