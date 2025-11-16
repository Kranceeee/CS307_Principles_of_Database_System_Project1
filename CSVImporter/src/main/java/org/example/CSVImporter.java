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
import java.util.concurrent.TimeUnit;


public class CSVImporter {

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

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private static final int BATCH_SIZE = 1000;

    public static void main(String[] args) {
        CSVImporter importer = new CSVImporter();
        importer.runImport();
    }

    @FunctionalInterface
    interface ImportTask {
        void run(Connection conn) throws IOException, SQLException;
    }


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
            e.printStackTrace();
        }
    }

    public void runImport() {

        long totalStartTime = System.currentTimeMillis();
        System.out.println("导入程序已启动...");

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("未找到 PostgreSQL JDBC 驱动程序！");
            e.printStackTrace();
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(true);
            System.out.println("数据库连接成功，已开启自动提交。");
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

    private void importCategories(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Categories (CategoryID, CategoryName) VALUES (?, ?)";
        importFromCSV(conn, CATEGORIES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("CategoryIdentifier")));
            pstmt.setString(2, record.get("CategoryName"));
        });
    }

    private void importKeywords(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Keywords (KeywordID, KeywordName) VALUES (?, ?)";
        importFromCSV(conn, KEYWORDS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("KeywordIdentifier")));
            pstmt.setString(2, record.get("KeywordName"));
        });
    }

    private void importIngredients(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Ingredients (IngredientID, IngredientName) VALUES (?, ?)";
        importFromCSV(conn, INGREDIENTS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("IngredientIdentifier")));
            pstmt.setString(2, record.get("IngredientName"));
        });
    }

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

    private void importNutrition(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO Nutrition (RecipeID, Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, CarbohydrateContent, FiberContent, SugarContent, ProteinContent) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        importFromCSV(conn, NUTRITION_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            setNullOrDecimal(pstmt, 2, parseDecimal(record.get("Calories")));
            setNullOrDecimal(pstmt, 3, parseDecimal(record.get("FatContent")));
            setNullOrDecimal(pstmt, 4, parseDecimal(record.get("SaturatedFatContent")));
            setNullOrDecimal(pstmt, 5, parseDecimal(record.get("CholesterolContent")));
            setNullOrDecimal(pstmt, 6, parseDecimal(record.get("SodiumContent")));
            setNullOrDecimal(pstmt, 7, parseDecimal(record.get("CarbohydrateContent")));
            setNullOrDecimal(pstmt, 8, parseDecimal(record.get("FiberContent")));
            setNullOrDecimal(pstmt, 9, parseDecimal(record.get("SugarContent")));
            setNullOrDecimal(pstmt, 10, parseDecimal(record.get("ProteinContent")));
        });
    }

    private void importRecipeCategories(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeCategories (RecipeID, CategoryID) VALUES (?, ?)";
        importFromCSV(conn, RECIPE_CATEGORIES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("CategoryIdentifier")));
        });
    }

    private void importRecipeKeywords(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeKeywords (RecipeID, KeywordID) VALUES (?, ?)";
        importFromCSV(conn, RECIPE_KEYWORDS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("KeywordIdentifier")));
        });
    }

    private void importRecipeIngredients(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO RecipeIngredients (RecipeID, IngredientID, Quantity) VALUES (?, ?, ?)";
        importFromCSV(conn, RECIPE_INGREDIENTS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("RecipeIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("IngredientIdentifier")));
            setNullOrDecimal(pstmt, 3, parseDecimal(record.get("Quantity")));
        });
    }

    private void importUserFavorites(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO UserFavorites (UserID, RecipeID) VALUES (?, ?)";
        importFromCSV(conn, USER_FAVORITES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("UserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("RecipeIdentifier")));
        });
    }

    private void importUserFollows(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO UserFollows (FollowerUserID, FollowingUserID) VALUES (?, ?)";
        importFromCSV(conn, USER_FOLLOWS_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("FollowerUserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("FollowingUserIdentifier")));
        });
    }

    private void importReviewLikes(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO ReviewLikes (UserID, ReviewID) VALUES (?, ?)";
        importFromCSV(conn, REVIEW_LIKES_FILE, sql, (pstmt, record) -> {
            pstmt.setInt(1, parseInteger(record.get("UserIdentifier")));
            pstmt.setInt(2, parseInteger(record.get("ReviewIdentifier")));
        });
    }



    @FunctionalInterface
    interface CsvRecordProcessor {
        void process(PreparedStatement pstmt, CSVRecord record) throws SQLException;
    }


    private void importFromCSV(Connection conn, String filePath, String sql, CsvRecordProcessor processor)
            throws IOException, SQLException {

        long count = 0;
        long skippedBatches = 0;
        long skippedRecords = 0;

        try (
                Reader reader = new FileReader(filePath);
                CSVParser parser = new CSVParser(reader, CSV_FORMAT);
                PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            for (CSVRecord record : parser) {
                try {
                    processor.process(pstmt, record);
                    pstmt.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        try {
                            pstmt.executeBatch();
                            System.out.println("  > 已插入 " + count + " 条记录...");
                        } catch (SQLException batchEx) {
                            System.err.println("  > [!! 批处理失败 !!] 跳过 " + BATCH_SIZE + " 条记录 (在总数 " + count + " 附近)。");
                            System.err.println("  > 错误详情: " + batchEx.getMessage());
                            skippedBatches++;

                            if (batchEx.getNextException() != null) {
                                System.err.println("  > 根本原因: " + batchEx.getNextException().getMessage());
                            }
                            pstmt.clearBatch();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("  > 跳过无效记录 (CSV 行号 " + record.getRecordNumber() + "): " + e.getMessage());
                    skippedRecords++;
                }
            }

            long remaining = count % BATCH_SIZE;
            if (remaining > 0) {
                try {
                    pstmt.executeBatch();
                    System.out.println("  > 已插入最后 " + remaining + " 条记录。");
                } catch (SQLException batchEx) {
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
            throw e;
        } catch (SQLException e) {
            System.err.println("SQL 严重执行失败 (文件: " + filePath + ")");
            e.printStackTrace();
        }
    }


    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("无效的整数格式: \"" + value + "\"");
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
            System.err.println("无效的 Decimal 格式: \"" + value + "\"");
            return null;
        }
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(value);
            return Timestamp.from(odt.toInstant());
        } catch (DateTimeParseException e) {
            System.err.println("无效的时间戳格式: \"" + value + "\"");
            return null;
        }
    }


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