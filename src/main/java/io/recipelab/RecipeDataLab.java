package io.recipelab;

import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class RecipeDataLab {
    public static void main(String[] args) {
        if (args.length == 0 || args[0].startsWith("--spring.") || args[0].startsWith("--server.")) {
            new SpringApplicationBuilder(RecipeDataLab.class).run(args);
            return;
        }
        try {
            if (args.length == 1 && "--help".equals(args[0])) {
                usage();
                return;
            }
            switch (args[0]) {
                case "import" -> {
                    if (args.length != 2) throw new IllegalArgumentException("Usage: import <csv-directory>");
                    Path directory = Path.of(args[1]);
                    try (var app = admin().run()) {
                        app.getBean(RecipeImport.class).load(directory)
                                .forEach(row -> System.out.printf("%s: %,d rows (%d ms)%n",
                                        row.file(), row.rows(), row.millis()));
                    }
                }
                case "benchmark" -> {
                    if (args.length != 5) {
                        throw new IllegalArgumentException("Usage: benchmark <lookups> <batch-size> <workers> <rounds>");
                    }
                    var settings = new LookupBenchmark.Settings(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                    try (var app = admin().run()) {
                        app.getBean(LookupBenchmark.class).run(settings);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static SpringApplicationBuilder admin() {
        return new SpringApplicationBuilder(RecipeDataLab.class).web(WebApplicationType.NONE);
    }

    private static void usage() {
        System.out.println("Run with no arguments to start the API.");
        System.out.println("import <csv-directory>");
        System.out.println("benchmark <lookups> <batch-size> <workers> <rounds>");
    }
}
