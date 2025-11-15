package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit; // [新增] 导入用于时间单位转换

/**
 * 数据库导入器 (已修正错误处理)
 * * 功能：从 13 个 CSV 文件中读取数据，并将其导入到 PostgreSQL 数据库中。
 * * [!! 修正 !!]
 * * 移除了单一的全局事务。
 * * 每个文件导入任务现在被单独执行和捕获错误，
 * * 以防止单个文件（如 User_Like_Review.csv）中的数据完整性错误（外键）
 * * 导致整个导入过程失败。
 * * [!! 新增 !!]
 * * 添加了总运行时间计时器。
 */
public class CSVImporter {

    // --- [!!!] 请在此处配置您的数据库信息 [!!!] ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "20060524Jay"; // 您的密码已保留

    // --- [!!!] 请在此处配置您的 CSV 文件路径 [!!!] ---
    private static final String FILE_PATH_PREFIX ="D:/RecipeImporter/CS307/processedData/";

    // 文件路径已保留
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

    // CSV 解析器格式
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    // 批处理大小
    private static final int BATCH_SIZE = 1000;

    public static void main(String[] args) {
        CSVImporter importer = new CSVImporter();
        importer.runImport();
    }

    // [!! 新增 !!] 定义一个功能接口，用于传递导入方法
    @FunctionalInterface
    interface ImportTask {
        void run(Connection conn) throws IOException, SQLException;
    }

    /**
     * [!! 新增辅助方法 !!]
     * 运行单个导入任务，并捕获其特定的错误，防止中止整个流程
     */
    private void runSingleImport(Connection conn, String taskName, ImportTask task) {
        try {
            System.out.println("\n-----------------------------------------");
            System.out.println("开始导入 " + taskName + "...");
            long taskStartTime = System.currentTimeMillis(); // [新增] 单个任务计时器

            task.run(conn);

            long taskEndTime = System.currentTimeMillis();
            double taskDuration = (taskEndTime - taskStartTime) / 1000.0;
            System.out.printf("... %s 导入任务结束。耗时: %.2f 秒。\n", taskName, taskDuration);

        } catch (Exception e) {
            // 捕获此任务期间发生的任何错误 (IO 或 SQL)
            System.err.println("\n[!! 严重错误 !!] 导入 " + taskName + " 时失败: " + e.getMessage());
            // 我们只打印错误堆栈，但不回滚或停止，以便其他任务可以继续
            e.printStackTrace();
        }
    }


    /**
     * [!! 已修改 !!] 主运行方法
     * 按正确的顺序执行所有导入，但现在单独处理每个任务。
     */
    public void runImport() {

        // [!! 新增 !!] 记录总开始时间
        long totalStartTime = System.currentTimeMillis();
        System.out.println("导入程序已启动...");

        // 1. 加载驱动程序
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("未找到 PostgreSQL JDBC 驱动程序！");
            e.printStackTrace();
            return;
        }

        // 2. 使用 try-with-resources 建立连接
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            // [!! 更改 !!] 开启自动提交。我们不再使用单一的巨大事务。
            // 每个 executeBatch() 现在都是它自己的事务。
            conn.setAutoCommit(true);
            System.out.println("数据库连接成功，已开启自动提交。");

            // [!! 更改 !!] 按顺序执行导入，
            // 使用 runSingleImport 包装器来捕获每个任务的错误。

            // 阶段 1: 基础实体表
            runSingleImport(conn, "Users", this::importUsers);
            runSingleImport(conn, "Categories", this::importCategories);
            runSingleImport(conn, "Keywords", this::importKeywords);
            runSingleImport(conn, "Ingredients", this::importIngredients);

            // 阶段 2: 核心实体表 (依赖阶段1)
            runSingleImport(conn, "Recipes", this::importRecipes);

            // 阶段 3: 评论 (依赖阶段1和2)
            runSingleImport(conn, "Reviews", this::importReviews);

            // 阶段 4: 关联表 (依赖阶段1、2、3)
            runSingleImport(conn, "Nutrition", this::importNutrition);
            runSingleImport(conn, "RecipeCategories", this::importRecipeCategories);
            runSingleImport(conn, "RecipeKeywords", this::importRecipeKeywords);

            // 修正了 RecipeIngredients 冲突
            runSingleImport(conn, "RecipeIngredients", this::importRecipeIngredients);

            runSingleImport(conn, "UserFavorites", this::importUserFavorites);
            runSingleImport(conn, "UserFollows", this::importUserFollows);

            // 这是您之前失败的地方
            runSingleImport(conn, "ReviewLikes", this::importReviewLikes);

            System.out.println("\n-----------------------------------------");
            System.out.println("🎉 所有 13 个导入任务已尝试执行。");

        } catch (SQLException e) {
            System.err.println("数据库连接失败！");
            e.printStackTrace();
        }

        // [!! 新增 !!] 计算并打印总耗时
        long totalEndTime = System.currentTimeMillis();
        long totalDurationMs = totalEndTime - totalStartTime;

        // 转换为更易读的格式 (例如: 1 分 15.34 秒)
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
    //  导入方法 (此处的方法保持不变)
    // -----------------------------------------------------------------

    // 1. Users
    private void importUsers(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Users (UserID, UserName, Gender, Age, Followers, Following) VALUES (?, ?, ?, ?, ?, ?)";
        importFromCSV(conn, USERS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("UserIdentifier")));
            pstmt.setString(2, record.get("UserName"));
            setNullOrString(pstmt, 3, record.get("Gender"));
            setNullOrInt(pstmt, 4, parseInteger(record.get("Age")));
            pstmt.setInt(5, parseInteger(record.get("Followers")));
            pstmt.setInt(6, parseInteger(record.get("Following")));
        });
    }

    // 2. Categories
    private void importCategories(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Categories (CategoryID, CategoryName) VALUES (?, ?)";
        importFromCSV(conn, CATEGORIES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("CategoryIdentifier")));
            pstmt.setString(2, record.get("CategoryName"));
        });
    }

    // 3. Keywords
    private void importKeywords(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Keywords (KeywordID, KeywordName) VALUES (?, ?)";
        importFromCSV(conn, KEYWORDS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("KeywordIdentifier")));
            pstmt.setString(2, record.get("KeywordName"));
        });
    }

    // 4. Ingredients
    private void importIngredients(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Ingredients (IngredientID, IngredientName) VALUES (?, ?)";
        importFromCSV(conn, INGREDIENTS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("IngredientIdentifier")));
            pstmt.setString(2, record.get("IngredientName"));
        });
    }

    // 5. Recipes
    private void importRecipes(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Recipes (RecipeID, AuthorUserID, Name, CookingTime, PreparationTime, TotalTime, DatePublished, Description, AggregateRating, ReviewCount, RecipeServings, RecipeYield, RecipeInstructions) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        importFromCSV(conn, RECIPES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("AuthorUserIdentifier")));
            pstmt.setString(3, record.get("Name"));
            setNullOrInt(pstmt, 4, parseInteger(record.get("CookingTime")));
            setNullOrInt(pstmt, 5, parseInteger(record.get("PreparationTime")));
            setNullOrInt(pstmt, 6, parseInteger(record.get("TotalTime")));
            setNullOrTimestamp(pstmt, 7, parseTimestamp(record.get("DatePublished")));
            setNullOrString(pstmt, 8, record.get("Description"));
            setNullOrDecimal(pstmt, 9, parseDecimal(record.get("AggregateRating")));
            setNullOrInt(pstmt, 10, parseInteger(record.get("ReviewCount")));
            setNullOrInt(pstmt, 11, parseInteger(record.get("RecipeServings")));
            setNullOrString(pstmt, 12, record.get("RecipeYield"));
            setNullOrString(pstmt, 13, record.get("RecipeInstructions"));
        });
    }

    // 6. Reviews
    private void importReviews(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Reviews (ReviewID, RecipeID, UserID, Rating, ReviewText, DateSubmitted, DateModified, Likes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        importFromCSV(conn, REVIEWS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("ReviewIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(3, parseInteger(record.get("UserIdentifier")));
            pstmt.setInt(4, parseInteger(record.get("Rating")));
            setNullOrString(pstmt, 5, record.get("ReviewText"));
            pstmt.setTimestamp(6, parseTimestamp(record.get("DateSubmitted")));
            pstmt.setTimestamp(7, parseTimestamp(record.get("DateModified")));
            pstmt.setInt(8, parseInteger(record.get("Likes")));
        });
    }

    // 7. Nutrition
    private void importNutrition(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Nutrition (RecipeID, Calories, FatContent, ProteinContent) VALUES (?, ?, ?, ?)";
        importFromCSV(conn, NUTRITION_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            setNullOrDecimal(pstmt, 2, parseDecimal(record.get("Calories")));
            setNullOrDecimal(pstmt, 3, parseDecimal(record.get("FatContent")));
            setNullOrDecimal(pstmt, 4, parseDecimal(record.get("ProteinContent")));
        });
    }

    // 8. RecipeCategories
    private void importRecipeCategories(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeCategories (RecipeID, CategoryID) VALUES (?, ?)";
        importFromCSV(conn, RECIPE_CATEGORIES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("CategoryIdentifier")));
        });
    }

    // 9. RecipeKeywords
    private void importRecipeKeywords(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeKeywords (RecipeID, KeywordID) VALUES (?, ?)";
        importFromCSV(conn, RECIPE_KEYWORDS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("KeywordIdentifier")));
        });
    }

    // 10. RecipeIngredients
    // 这个方法假定您已经应用了 DDL 修复
    // (即 RecipeIngredients 表使用 SERIAL PRIMARY KEY 而不是复合键)
    private void importRecipeIngredients(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeIngredients (RecipeID, IngredientID, Quantity, Unit, Notes) VALUES (?, ?, ?, ?, ?)";
        importFromCSV(conn, RECIPE_INGREDIENTS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("IngredientIdentifier")));
            setNullOrDecimal(pstmt, 3, parseDecimal(record.get("Quantity")));
            setNullOrString(pstmt, 4, record.get("Unit"));
            setNullOrString(pstmt, 5, record.get("Notes"));
        });
    }

    // 11. UserFavorites
    private void importUserFavorites(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO UserFavorites (UserID, RecipeID, DateFavorited) VALUES (?, ?, ?)";
        importFromCSV(conn, USER_FAVORITES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("UserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("RecipeIdentifier")));
            setNullOrTimestamp(pstmt, 3, parseTimestamp(record.get("DateFavorited")));
        });
    }

    // 12. UserFollows
    private void importUserFollows(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO UserFollows (FollowerUserID, FollowingUserID) VALUES (?, ?)";
        importFromCSV(conn, USER_FOLLOWS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("FollowerUserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("FollowingUserIdentifier")));
        });
    }

    // 13. ReviewLikes
    private void importReviewLikes(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO ReviewLikes (UserID, ReviewID, DateLiked) VALUES (?, ?, ?)";
        importFromCSV(conn, REVIEW_LIKES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("UserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("ReviewIdentifier")));
            setNullOrTimestamp(pstmt, 3, parseTimestamp(record.get("DateLiked")));
        });
    }


    // -----------------------------------------------------------------
    //  通用导入器和辅助方法
    // -----------------------------------------------------------------

    /**
     * 定义一个功能接口，用于处理 CSV 记录到 PreparedStatement 的映射
     */
    @FunctionalInterface
    interface CsvRecordProcessor {
        void process(PreparedStatement pstmt, CSVRecord record) throws SQLException;
    }

    /**
     * [!! 已修改 !!] 通用的 CSV 导入方法
     * @param conn 数据库连接
     * @param filePath CSV 文件路径
     * @param sql 插入语句 (INSERT SQL)
     * @param processor Lambda 表达式，用于将 CSVRecord 映射到 PreparedStatement
     */
    private void importFromCSV(Connection conn, String filePath, String sql, CsvRecordProcessor processor)
            throws IOException, SQLException { // 它仍然可以抛出 IOException

        long count = 0;
        long skippedBatches = 0;
        long skippedRecords = 0; // [新增] 记录单个跳过的记录

        try (
                Reader reader = new FileReader(filePath);
                CSVParser parser = new CSVParser(reader, CSV_FORMAT);
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            for (CSVRecord record : parser) {
                try {
                    // 应用映射逻辑
                    processor.process(pstmt, record);
                    // 添加到批处理
                    pstmt.addBatch();
                    count++;

                    // 达到批处理大小，执行
                    if (count % BATCH_SIZE == 0) {
                        try {
                            pstmt.executeBatch();
                            System.out.println("  > 已插入 " + count + " 条记录...");
                        } catch (SQLException batchEx) {
                            // [!! 关键更改 !!] 捕获批处理执行失败
                            System.err.println("  > [!! 批处理失败 !!] 跳过 " + BATCH_SIZE + " 条记录 (在总数 " + count + " 附近)。");
                            System.err.println("  > 错误详情: " + batchEx.getMessage());
                            skippedBatches++;

                            // 打印更详细的错误链 (如果有的话)
                            if (batchEx.getNextException() != null) {
                                System.err.println("  > 根本原因: " + batchEx.getNextException().getMessage());
                            }
                            // 不再向上抛出，清空批处理，然后继续下一轮
                            pstmt.clearBatch();
                        }
                    }
                } catch (Exception e) {
                    // 记录解析单行时的错误，但继续处理下一行
                    System.err.println("  > 跳过无效记录 (CSV 行号 " + record.getRecordNumber() + "): " + e.getMessage());
                    skippedRecords++;
                }
            }

            // 执行剩余的批处理
            long remaining = count % BATCH_SIZE;
            if (remaining > 0) { // [新增] 检查是否有剩余
                try {
                    pstmt.executeBatch();
                    System.out.println("  > 已插入最后 " + remaining + " 条记录。");
                } catch (SQLException batchEx) {
                    // [!! 关键更改 !!] 捕获 *最后* 的批处理执行失败
                    System.err.println("  > [!! 最后的批处理失败 !!] 跳过了最后 " + remaining + " 条记录。");
                    System.err.println("  > 错误详情: " + batchEx.getMessage());
                    skippedBatches++;
                    if (batchEx.getNextException() != null) {
                        System.err.println("  > 根本原因: " + batchEx.getNextException().getMessage());
                    }
                }
            }

            System.out.println("  > 导入完成。总共处理 " + count + " 条记录。");
            if (skippedRecords > 0) {
                System.out.println("  > [!! 警告 !!] 总共有 " + skippedRecords + " 条 *单行* 因解析错误被跳过。");
            }
            if (skippedBatches > 0) {
                System.out.println("  > [!! 警告 !!] 总共有 " + skippedBatches + " 个 *批次* (约 " + (skippedBatches * BATCH_SIZE) + " 条记录) 因数据库错误被跳过。");
            }


        } catch (IOException e) {
            System.err.println("文件读取失败: " + filePath);
            throw e; // 向上抛出以触发 runSingleImport 捕获
        } catch (SQLException e) {
            // 这个捕获现在主要用于 PreparedStatement 创建失败等更严重的问题
            System.err.println("SQL 严重执行失败 (文件: " + filePath + ")");
            // [!! 更改 !!] 不再向上抛出 SQL 异常，让 runSingleImport 来处理
            e.printStackTrace();
        }
    }

    // --- CSV 数据解析辅助方法 (处理 null 和空字符串) ---
    // (这些方法保持不变)

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            // 处理 "1.0", "4.0" 这样的浮点数格式的整数
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // System.err.println("无效的整数格式: " + value); // 暂时关闭日志，避免刷屏
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            // System.err.println("无效的 Decimal 格式: " + value); // 暂时关闭日志，避免刷屏
            return null;
        }
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            // 解析 ISO 8601 格式，如 "2000-01-25T21:44:00Z"
            OffsetDateTime odt = OffsetDateTime.parse(value);
            return Timestamp.from(odt.toInstant());
        } catch (DateTimeParseException e) {
            // System.err.println("无效的时间戳格式: " + value); // 暂时关闭日志，避免刷屏
            return null;
        }
    }

    // --- PreparedStatement 辅助方法 (处理 null 值) ---
    // (这些方法保持不变)

    private void setNullOrString(PreparedStatement pstmt, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            pstmt.setNull(index, Types.VARCHAR);
        } else {
            pstmt.setString(index, value);
        }
    }

    private void setNullOrInt(PreparedStatement pstmt, int index, Integer value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.INTEGER);
        } else {
            pstmt.setInt(index, value);
        }
    }

    private void setNullOrDecimal(PreparedStatement pstmt, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.DECIMAL);
        } else {
            pstmt.setBigDecimal(index, value);
        }
    }

    private void setNullOrTimestamp(PreparedStatement pstmt, int index, Timestamp value) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            pstmt.setTimestamp(index, value);
        }
    }
}
