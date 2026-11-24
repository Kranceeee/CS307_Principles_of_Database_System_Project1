package io.recipelab;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class RecipeApi {
    private final RecipeCatalog catalog;
    private final JdbcClient jdbc;

    RecipeApi(RecipeCatalog catalog, JdbcClient jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
        return Map.of("status", "ok");
    }

    @GetMapping("/recipes")
    RecipeCatalog.Page search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String ingredient,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) Integer maxMinutes,
                              @RequestParam(required = false) Integer after,
                              @RequestParam(defaultValue = "20") int limit) {
        validateLimit(limit);
        if (maxMinutes != null && maxMinutes < 0) throw invalid("maxMinutes must be non-negative");
        return catalog.search(new RecipeCatalog.Filter(filter(q), filter(ingredient), filter(category), maxMinutes, after, limit));
    }

    @GetMapping("/recipes/{id}")
    RecipeCatalog.Detail detail(@PathVariable int id) {
        var result = catalog.detail(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        return result;
    }

    @GetMapping("/recipes/{id}/reviews")
    List<RecipeCatalog.Review> reviews(@PathVariable int id, @RequestParam(defaultValue = "20") int limit) {
        validateLimit(limit);
        if (!jdbc.sql("SELECT EXISTS (SELECT 1 FROM recipe_lab.recipes WHERE recipeid = :id)")
                .param("id", id).query(Boolean.class).single()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
        }
        return catalog.reviews(id, limit);
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) throw invalid("limit must be between 1 and 100");
    }

    private static String filter(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.strip();
        if (value.length() > 120) throw invalid("Search terms must be at most 120 characters");
        return value;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
