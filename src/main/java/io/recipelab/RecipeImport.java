package io.recipelab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.springframework.stereotype.Service;

@Service
class RecipeImport {
    record FileResult(String file, long rows, long millis) {}

    private record Input(String file, String table, String columns) {
        String target() {
            return "recipe_lab." + table;
        }

        String copySql() {
            return "COPY " + target() + " (" + columns + ") FROM STDIN WITH (FORMAT csv)";
        }

        boolean headerMatches(String header) {
            if (header == null) return false;
            String value = header.startsWith("\ufeff") ? header.substring(1) : header;
            String[] fields = value.split(",", -1);
            String[] expected = columns.split(",");
            if (fields.length != expected.length) return false;
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i].strip();
                if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\"")) {
                    field = field.substring(1, field.length() - 1);
                }
                if (!expected[i].equalsIgnoreCase(field)) return false;
            }
            return true;
        }
    }

    private static final List<Input> INPUTS = List.of(
            new Input("User.csv", "users", "userid,username,gender,age,followers,following"),
            new Input("Category.csv", "categories", "categoryid,categoryname"),
            new Input("Keyword.csv", "keywords", "keywordid,keywordname"),
            new Input("Ingredient.csv", "ingredients", "ingredientid,ingredientname"),
            new Input("Recipe.csv", "recipes", "recipeid,authoruserid,name,cookingtime,preparationtime,totaltime,datepublished,description,aggregaterating,reviewcount,recipeservings,recipeyield,recipeinstructions"),
            new Input("Nutrition.csv", "nutrition", "recipeid,calories,fatcontent,saturatedfatcontent,cholesterolcontent,sodiumcontent,carbohydratecontent,fibercontent,sugarcontent,proteincontent"),
            new Input("Review.csv", "reviews", "reviewid,recipeid,userid,rating,reviewtext,datesubmitted,datemodified,likes"),
            new Input("Recipe_Category.csv", "recipecategories", "recipeid,categoryid"),
            new Input("Recipe_Keyword.csv", "recipekeywords", "recipeid,keywordid"),
            new Input("Recipe_Ingredient.csv", "recipeingredients", "recipeid,ingredientid,quantity"),
            new Input("User_Favorite_Recipe.csv", "userfavorites", "userid,recipeid"),
            new Input("User_Follow.csv", "userfollows", "followeruserid,followinguserid"),
            new Input("User_Like_Review.csv", "reviewlikes", "userid,reviewid"));

    private final DataSource dataSource;

    RecipeImport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<FileResult> load(Path directory) throws IOException, SQLException {
        for (Input input : INPUTS) {
            if (!Files.isRegularFile(directory.resolve(input.file()))) {
                throw new IllegalArgumentException("Missing input: " + input.file());
            }
        }
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(540331921)");
                    var queries = new StringJoiner(" OR ", "SELECT ", "");
                    for (Input input : INPUTS) queries.add("EXISTS (SELECT 1 FROM " + input.target() + ")");
                    try (var result = statement.executeQuery(queries.toString())) {
                        result.next();
                        if (result.getBoolean(1)) {
                            throw new IllegalStateException("The recipe_lab schema already contains data. Import into a fresh database.");
                        }
                    }
                }

                var copy = connection.unwrap(PGConnection.class).getCopyAPI();
                var results = new ArrayList<FileResult>();
                for (Input input : INPUTS) {
                    long start = System.nanoTime();
                    try (var reader = Files.newBufferedReader(directory.resolve(input.file()), StandardCharsets.UTF_8)) {
                        if (!input.headerMatches(reader.readLine())) {
                            throw new IllegalArgumentException("Unexpected header in " + input.file() + ". Expected: " + input.columns());
                        }
                        long rows = copy.copyIn(input.copySql(), reader);
                        if ("recipes".equals(input.table()) && rows == 0) {
                            throw new IllegalArgumentException("Recipe.csv must contain at least one recipe");
                        }
                        results.add(new FileResult(input.file(), rows, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
                    } catch (SQLException e) {
                        throw new SQLException("Import failed for " + input.file() + " (SQLSTATE " + e.getSQLState()
                                + "). Check CSV values and references.", e.getSQLState(), e);
                    }
                }
                connection.commit();
                return List.copyOf(results);
            } catch (SQLException | IOException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }
}
