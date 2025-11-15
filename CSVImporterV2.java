package org.example;

// 导入
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * 数据库导入器 (V2 - 高速 COPY 版 - 已修复)
 * * [!! 修复 !!]
 * * 更新了所有 13 个 COPY 语句，以使用与 V8 Decomposer
 * * 生成的 CSV 表头完全匹配的列名 (例如 UserID, RecipeID)。
 * * 保留了 ((BaseConnection) conn).getCopyAPI() 以兼容旧版驱动。
 */
public class CSVImporterV2 { // [!! 重命名 !!]

    // --- 数据库和文件路径配置 (保持不变) ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "20060524Jay";

    private static final String FILE_PATH_PREFIX ="D:/RecipeImporter/CS307/processedData/";
    private static final String RECIPES_FILE = FILE_PATH_PREFIX + "Recipe.csv";
    private static final String REVIEWS_FILE = FILE_PATH_PREFIX + "Review.csv";
    private static final String USERS_FILE = FILE_PATH_PREFIX + "User.csv";
    private static final String CATEGORIES_FILE = FILE_PATH_PREFIX + "Category.csv";
    private static final String KEYWORDS_FILE = FILE_PATH_PREFIX + "Keyword.csv";
    private static final String INGREDIENTS_FILE = FILE_PATH_PREFIX + "Ingredient.csv";
    private static final String NUTRITION_FILE = FILE_PATH_PREFIX + "Nutrition.csv";
    private static final String RECIPE_CATEGORIES_FILE = FILE_PATH_PREFIX + "Recipe_Category.csv";
    private static final String RECIPE_KEYWORDS_FILE = FILE_PATH_PREFIX + "Recipe_Keyword.csv";
    private static final String RECIPE_INGREDIENTS_FILE = FILE_PATH_PREFIX + "Recipe_Ingredient.csv";
    private static final String USER_FAVORITES_FILE = FILE_PATH_PREFIX + "User_Favorite_Recipe.csv";
    private static final String USER_FOLLOWS_FILE = FILE_PATH_PREFIX + "User_Follow.csv";
    private static final String REVIEW_LIKES_FILE = FILE_PATH_PREFIX + "User_Like_Review.csv";

    // ... main 和 runImport 方法 (保持不变) ...

    public static void main(String[] args) {
        CSVImporterV2 importer = new CSVImporterV2();
        importer.runImport();
    }

    @FunctionalInterface
    interface ImportTask {
        void run(Connection conn) throws IOException, SQLException;
    }

    // (runSingleImport 保持不变)
    private void runSingleImport(Connection conn, String taskName, ImportTask task) {
        try {
            System.out.println("\n-----------------------------------------");
            System.out.println("开始导入 " + taskName + "...");
            long taskStartTime = System.currentTimeMillis();

            task.run(conn);

            long taskEndTime = System.currentTimeMillis();
            double taskDuration = (taskEndTime - taskStartTime) / 1000.0;
            System.out.printf("... %s 导入任务结束。耗时: %.2f 秒。\n", taskName, taskDuration);

        } catch (Exception e) {
            System.err.println("\n[!! 严重错误 !!] 导入 " + taskName + " 时失败: " + e.getMessage());
            System.err.println("  > [!! 提示 !!] COPY 失败通常意味着 CSV 数据类型与数据库表不匹配，");
            System.err.println("  >            或者 CSV 列的顺序与 COPY 语句中指定的顺序不符。");
            e.printStackTrace();
        }
    }

    // (runImport 保持不变)
    public void runImport() {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("导入程序已启动 (V2 - COPY 模式 - 已修复)...");

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("未找到 PostgreSQL JDBC 驱动程序！");
            e.printStackTrace();
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            conn.setAutoCommit(true);
            System.out.println("数据库连接成功。");

            // 按顺序执行导入
            runSingleImport(conn, "Users", this::importUsers);
            runSingleImport(conn, "Categories", this::importCategories);
            runSingleImport(conn, "Keywords", this::importKeywords);
            runSingleImport(conn, "Ingredients", this::importIngredients);
            runSingleImport(conn, "Recipes", this::importRecipes);
            runSingleImport(conn, "Reviews", this::importReviews);
            runSingleImport(conn, "Nutrition", this::importNutrition);
            runSingleImport(conn, "RecipeCategories", this::importRecipeCategories);
            runSingleImport(conn, "RecipeKeywords", this::importRecipeKeywords);
            runSingleImport(conn, "RecipeIngredients", this::importRecipeIngredients);
            runSingleImport(conn, "UserFavorites", this::importUserFavorites);
            runSingleImport(conn, "UserFollows", this::importUserFollows);
            runSingleImport(conn, "ReviewLikes", this::importReviewLikes);

            System.out.println("\n-----------------------------------------");
            System.out.println("🎉 所有 13 个导入任务已尝试执行。");

        } catch (SQLException e) {
            System.err.println("数据库连接失败！");
            e.printStackTrace();
        }

        // ... 总时间计算 (保持不变) ...
        long totalEndTime = System.currentTimeMillis();
        long totalDurationMs = totalEndTime - totalStartTime;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(totalDurationMs) % 60;
        long millis = totalDurationMs % 1000;

        System.out.println("\n=========================================");
        System.out.printf("   [!! INFO !!] 导入程序执行完毕。\n");
        System.out.printf("   [!! INFO !!] 总耗时: %d 分 %d.%03d 秒 (总共: %d 毫秒)\n",
                minutes, seconds, millis, totalDurationMs);
        System.out.println("=========================================");
    }

    // -----------------------------------------------------------------
    //  通用 COPY 导入器
    // -----------------------------------------------------------------

    /**
     * [!! 保持您工作中的版本 !!]
     * 使用 ((BaseConnection) conn).getCopyAPI() 来兼容您的旧版驱动。
     */
    private void importWithCopy(Connection conn, String filePath, String copySql)
            throws SQLException, IOException {

        // [!! 保留 !!] 使用您能工作的 .getCopyAPI()
        CopyManager copyManager = ((BaseConnection) conn).getCopyAPI();

        try (Reader reader = new FileReader(filePath)) {
            long rowsAffected = copyManager.copyIn(copySql, reader);
            System.out.println("  > [COPY] 成功导入 " + rowsAffected + " 条记录。");
        }
    }

    /**
     * [!! COPY 选项保持不变 !!]
     * (V8 Decomposer 生成的 NULL 仍然是 "" 空字符串)
     */
    private static final String COPY_OPTIONS = "FROM STDIN WITH (FORMAT csv, HEADER true, NULL '', DELIMITER ',')";


    // -----------------------------------------------------------------
    //  [!! 已修复 !!] 导入方法 (现在使用正确的列名)
    // -----------------------------------------------------------------

    // 1. Users
    private void importUsers(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 UserID, ...
        String sql = "COPY Users (UserID, UserName, Gender, Age, Followers, Following) " + COPY_OPTIONS;
        importWithCopy(conn, USERS_FILE, sql);
    }

    // 2. Categories
    private void importCategories(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 CategoryID, ...
        String sql = "COPY Categories (CategoryID, CategoryName) " + COPY_OPTIONS;
        importWithCopy(conn, CATEGORIES_FILE, sql);
    }

    // 3. Keywords
    private void importKeywords(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 KeywordID, ...
        String sql = "COPY Keywords (KeywordID, KeywordName) " + COPY_OPTIONS;
        importWithCopy(conn, KEYWORDS_FILE, sql);
    }

    // 4. Ingredients
    private void importIngredients(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 IngredientID, ...
        String sql = "COPY Ingredients (IngredientID, IngredientName) " + COPY_OPTIONS;
        importWithCopy(conn, INGREDIENTS_FILE, sql);
    }

    // 5. Recipes
    private void importRecipes(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 RecipeID, AuthorUserID, ...
        String sql = "COPY Recipes (RecipeID, AuthorUserID, Name, CookingTime, PreparationTime, TotalTime, DatePublished, Description, AggregateRating, ReviewCount, RecipeServings, RecipeYield, RecipeInstructions) " + COPY_OPTIONS;
        importWithCopy(conn, RECIPES_FILE, sql);
    }

    // 6. Reviews
    private void importReviews(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 ReviewID, RecipeID, UserID, ...
        String sql = "COPY Reviews (ReviewID, RecipeID, UserID, Rating, ReviewText, DateSubmitted, DateModified, Likes) " + COPY_OPTIONS;
        importWithCopy(conn, REVIEWS_FILE, sql);
    }

    // 7. Nutrition
    private void importNutrition(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 RecipeID, ...
        String sql = "COPY Nutrition (RecipeID, Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, CarbohydrateContent, FiberContent, SugarContent, ProteinContent) " + COPY_OPTIONS;
        importWithCopy(conn, NUTRITION_FILE, sql);
    }

    // 8. RecipeCategories
    private void importRecipeCategories(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 RecipeID, CategoryID
        String sql = "COPY RecipeCategories (RecipeID, CategoryID) " + COPY_OPTIONS;
        importWithCopy(conn, RECIPE_CATEGORIES_FILE, sql);
    }

    // 9. RecipeKeywords
    private void importRecipeKeywords(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 RecipeID, KeywordID
        String sql = "COPY RecipeKeywords (RecipeID, KeywordID) " + COPY_OPTIONS;
        importWithCopy(conn, RECIPE_KEYWORDS_FILE, sql);
    }

    // 10. RecipeIngredients
    private void importRecipeIngredients(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 RecipeID, IngredientID, ...
        String sql = "COPY RecipeIngredients (RecipeID, IngredientID, Quantity) " + COPY_OPTIONS;
        importWithCopy(conn, RECIPE_INGREDIENTS_FILE, sql);
    }

    // 11. UserFavorites
    private void importUserFavorites(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 UserID, RecipeID
        String sql = "COPY UserFavorites (UserID, RecipeID) " + COPY_OPTIONS;
        importWithCopy(conn, USER_FAVORITES_FILE, sql);
    }

    // 12. UserFollows
    private void importUserFollows(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 FollowerUserID, FollowingUserID
        String sql = "COPY UserFollows (FollowerUserID, FollowingUserID) " + COPY_OPTIONS;
        importWithCopy(conn, USER_FOLLOWS_FILE, sql);
    }

    // 13. ReviewLikes
    private void importReviewLikes(Connection conn) throws IOException, SQLException {
        // [!! 修复 !!] 使用 UserID, ReviewID
        String sql = "COPY ReviewLikes (UserID, ReviewID) " + COPY_OPTIONS;
        importWithCopy(conn, REVIEW_LIKES_FILE, sql);
    }
}