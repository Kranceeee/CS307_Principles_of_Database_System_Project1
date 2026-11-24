package io.recipelab;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecipeCatalog {
    record Filter(String query, String ingredient, String category, Integer maxMinutes, Integer after, int limit) {}
    record RecipeSummary(int id, String name, Integer totalMinutes, BigDecimal rating, Integer reviewCount) {}
    record Page(List<RecipeSummary> items, Integer nextAfter) {}
    record Recipe(int id, String name, String author, String description, Integer cookingMinutes, Integer preparationMinutes,
                  Integer totalMinutes, OffsetDateTime publishedAt, BigDecimal rating, Integer reviewCount,
                  Integer servings, String yield, String instructions) {}
    record Ingredient(int id, String name, BigDecimal quantity) {}
    record Nutrition(BigDecimal calories, BigDecimal fat, BigDecimal saturatedFat, BigDecimal cholesterol,
                     BigDecimal sodium, BigDecimal carbohydrate, BigDecimal fiber, BigDecimal sugar, BigDecimal protein) {}
    record Detail(Recipe recipe, List<Ingredient> ingredients, List<String> categories, List<String> keywords,
                  Nutrition nutrition) {}
    record Review(int id, String author, int rating, String text, OffsetDateTime submittedAt, int likes) {}

    private final JdbcClient jdbc;

    RecipeCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Page search(Filter query) {
        var sql = new StringBuilder("""
                SELECT r.recipeid AS id, r.name, r.totaltime AS total_minutes,
                       r.aggregaterating AS rating, r.reviewcount AS review_count
                FROM recipe_lab.recipes r WHERE TRUE
                """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (query.after() != null) {
            sql.append(" AND r.recipeid > :after");
            parameters.put("after", query.after());
        }
        if (query.query() != null) {
            sql.append(" AND strpos(lower(r.name), lower(:query)) > 0");
            parameters.put("query", query.query());
        }
        if (query.maxMinutes() != null) {
            sql.append(" AND r.totaltime <= :maxMinutes");
            parameters.put("maxMinutes", query.maxMinutes());
        }
        if (query.ingredient() != null) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM recipe_lab.recipeingredients ri
                         JOIN recipe_lab.ingredients i USING (ingredientid)
                         WHERE ri.recipeid = r.recipeid AND lower(i.ingredientname) = lower(:ingredient))
                    """);
            parameters.put("ingredient", query.ingredient());
        }
        if (query.category() != null) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM recipe_lab.recipecategories rc
                         JOIN recipe_lab.categories c USING (categoryid)
                         WHERE rc.recipeid = r.recipeid AND lower(c.categoryname) = lower(:category))
                    """);
            parameters.put("category", query.category());
        }
        sql.append(" ORDER BY r.recipeid LIMIT :limit");
        parameters.put("limit", query.limit() + 1);
        List<RecipeSummary> rows = jdbc.sql(sql.toString()).params(parameters).query(RecipeSummary.class).list();
        if (rows.size() <= query.limit()) return new Page(rows, null);
        List<RecipeSummary> shown = List.copyOf(rows.subList(0, query.limit()));
        return new Page(shown, shown.get(shown.size() - 1).id());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Detail detail(int id) {
        Recipe recipe = jdbc.sql("""
                SELECT r.recipeid AS id, r.name, u.username AS author, r.description, r.cookingtime AS cooking_minutes,
                       r.preparationtime AS preparation_minutes, r.totaltime AS total_minutes, r.datepublished AS published_at,
                       r.aggregaterating AS rating, r.reviewcount AS review_count, r.recipeservings AS servings,
                       r.recipeyield AS yield, r.recipeinstructions AS instructions
                FROM recipe_lab.recipes r JOIN recipe_lab.users u ON u.userid = r.authoruserid
                WHERE r.recipeid = :id
                """).param("id", id).query(Recipe.class).optional().orElse(null);
        if (recipe == null) return null;
        var ingredients = jdbc.sql("""
                SELECT i.ingredientid AS id, i.ingredientname AS name, ri.quantity
                FROM recipe_lab.recipeingredients ri JOIN recipe_lab.ingredients i USING (ingredientid)
                WHERE ri.recipeid = :id ORDER BY ri.recipeingredientid
                """).param("id", id).query(Ingredient.class).list();
        var categories = jdbc.sql("""
                SELECT c.categoryname FROM recipe_lab.recipecategories rc JOIN recipe_lab.categories c USING (categoryid)
                WHERE rc.recipeid = :id ORDER BY c.categoryname, c.categoryid
                """).param("id", id).query(String.class).list();
        var keywords = jdbc.sql("""
                SELECT k.keywordname FROM recipe_lab.recipekeywords rk JOIN recipe_lab.keywords k USING (keywordid)
                WHERE rk.recipeid = :id ORDER BY k.keywordname, k.keywordid
                """).param("id", id).query(String.class).list();
        var nutrition = jdbc.sql("""
                SELECT calories, fatcontent AS fat, saturatedfatcontent AS saturated_fat, cholesterolcontent AS cholesterol,
                       sodiumcontent AS sodium, carbohydratecontent AS carbohydrate, fibercontent AS fiber,
                       sugarcontent AS sugar, proteincontent AS protein
                FROM recipe_lab.nutrition WHERE recipeid = :id
                """).param("id", id).query(Nutrition.class).optional().orElse(null);
        return new Detail(recipe, ingredients, categories, keywords, nutrition);
    }

    List<Review> reviews(int id, int limit) {
        return jdbc.sql("""
                SELECT rv.reviewid AS id, u.username AS author, rv.rating, rv.reviewtext AS text,
                       rv.datesubmitted AS submitted_at, rv.likes
                FROM recipe_lab.reviews rv JOIN recipe_lab.users u ON u.userid = rv.userid
                WHERE rv.recipeid = :id ORDER BY rv.reviewid DESC LIMIT :limit
                """).param("id", id).param("limit", limit).query(Review.class).list();
    }
}
