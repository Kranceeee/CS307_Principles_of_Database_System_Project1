import dbms.PostgreSQLOperations;
import fileio.CSVFileProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

/**
 * Task 4 性能对比测试主类 (协调器)
 */
public class PerformanceTester {

    private static final int TEST_ITERATIONS = 1000;
    private static final Random RANDOM = new Random();

    // 存储测试数据
    private static final List<String> EXIST_IDS = new ArrayList<>(); // 用于 Query/Select
    private static final List<String[]> INSERT_DATA = new ArrayList<>(); // 用于 Insert

    // 用于 Update/Delete 的特定 ID
    private static String ID_FOR_UPDATE = "90000000";
    private static String ID_FOR_DELETE = "90000001";

    public static void main(String[] args) {
        System.out.println("开始 Task 4 性能对比测试...");

        try {
            // 1. 准备测试数据 (包括加载 CSV 到内存)
            setupTestData();

            // 2. 运行所有测试
            runQueryTest();   // SELECT
            runInsertTest();  // INSERT
            runUpdateTest();  // UPDATE
            runDeleteTest();  // DELETE

        } catch (Exception e) {
            System.err.println("致命错误，测试中止: " + e.getMessage());
            // e.printStackTrace(); // 调试完成后可以注释掉
        }

        System.out.println("\n--- 性能对比测试完成 ---");
    }

    private static void setupTestData() {
        System.out.println("--- 准备测试数据 ---");

        // 关键步骤：加载 File I/O 模拟数据
        CSVFileProcessor.loadAllData();

        // **修正外键问题逻辑：将 MAX_EXISTING_RECIPE_ID 缩小到 10，以确保 ID 存在**

        // 假设您的数据库 Recipes 表中至少存在 ID 1 到 ID 10 的记录。
        int MAX_EXISTING_RECIPE_ID = 10;

        // 1. 填充少量真实 ID (用于外键约束)
        EXIST_IDS.clear(); // 清空，确保重新填充
        for (int i = 1; i <= MAX_EXISTING_RECIPE_ID; i++) {
            EXIST_IDS.add(String.valueOf(i));
        }

        // 2. 扩展 EXIST_IDS 到 TEST_ITERATIONS 的规模
        // 注意：这里使用随机选取，以保证测试能够覆盖到 1000 次查询
        while (EXIST_IDS.size() < TEST_ITERATIONS) {
            // 重复使用已存在的 ID 填充列表
            EXIST_IDS.add(EXIST_IDS.get(RANDOM.nextInt(MAX_EXISTING_RECIPE_ID)));
        }

        // 准备 INSERT 数据：[ReviewID, RecipeID, UserID, Rating, ReviewText]
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String newReviewId = String.valueOf(90000000 + i);

            // 关键修正：从 EXIST_IDS 中随机抽取一个确保存在的 RecipeID
            String existRecipeId = EXIST_IDS.get(RANDOM.nextInt(TEST_ITERATIONS));

            String existUserId = "1000"; // 假设一个存在的 UserID

            INSERT_DATA.add(new String[]{
                    newReviewId,
                    existRecipeId, // 使用了 EXIST_IDS 中的 ID，保证外键约束通过
                    existUserId,
                    String.valueOf(RANDOM.nextInt(5) + 1),
                    "Test Review Text " + UUID.randomUUID().toString().substring(0, 8)
            });
        }

        // 确定用于 UPDATE 和 DELETE 的特定 ID
        if (INSERT_DATA.size() >= 2) {
            ID_FOR_UPDATE = INSERT_DATA.get(0)[0];
            ID_FOR_DELETE = INSERT_DATA.get(1)[0];
        }

        System.out.println("数据准备完成。");
    }


    /**
     * 测试 1: 查询 (Query) 性能 - Recipes 表 (主键 O(1))
     */
    private static void runQueryTest() {
        System.out.println("\n--- 1. 开始查询 (Query) 测试 ---");

        // --- DBMS 查询测试 ---
        long startDb = System.nanoTime();
        for (String id : EXIST_IDS) {
            PostgreSQLOperations.queryRecipeById(id);
        }
        long endDb = System.nanoTime();
        double timeDbMs = (endDb - startDb) / 1_000_000.0;
        System.out.printf("[DBMS]   查询 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeDbMs);

        // --- File I/O 查询测试 ---
        long startFile = System.nanoTime();
        for (String id : EXIST_IDS) {
            CSVFileProcessor.queryRecipeById(id, CSVFileProcessor.RECIPES_FILE_PATH);
        }
        long endFile = System.nanoTime();
        double timeFileMs = (endFile - startFile) / 1_000_000.0;
        System.out.printf("[File I/O] 查询 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeFileMs);
    }

    /**
     * 测试 2: 插入 (Insert) 性能 - Reviews 表
     */
    private static void runInsertTest() {
        System.out.println("\n--- 2. 开始插入 (Insert) 测试 ---");

        // --- DBMS 插入测试 ---
        long startDb = System.nanoTime();
        for (String[] data : INSERT_DATA) {
            PostgreSQLOperations.insertReview(data[0], data[1], data[2], data[3]);
        }
        long endDb = System.nanoTime();
        double timeDbMs = (endDb - startDb) / 1_000_000.0;
        System.out.printf("[DBMS]   插入 %d 条耗时: %.4f ms%n", TEST_ITERATIONS, timeDbMs);

        // --- File I/O 插入测试 ---
        long startFile = System.nanoTime();
        for (String[] data : INSERT_DATA) {
            CSVFileProcessor.insertReview(data, CSVFileProcessor.REVIEWS_FILE_PATH);
        }
        long endFile = System.nanoTime();
        double timeFileMs = (endFile - startFile) / 1_000_000.0;
        System.out.printf("[File I/O] 插入 %d 条耗时: %.4f ms%n", TEST_ITERATIONS, timeFileMs);
    }

    /**
     * 测试 3: 更新 (Update) 性能 - Reviews 表
     */
    private static void runUpdateTest() {
        System.out.println("\n--- 3. 开始更新 (Update) 测试 ---");

        // --- DBMS 更新测试 ---
        long startDb = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String newReviewText = "Updated Text @ " + i;
            PostgreSQLOperations.updateReviewText(ID_FOR_UPDATE, newReviewText);
        }
        long endDb = System.nanoTime();
        double timeDbMs = (endDb - startDb) / 1_000_000.0;
        System.out.printf("[DBMS]   更新 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeDbMs);

        // --- File I/O 更新测试 ---
        long startFile = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String newReviewText = "Updated Text @ " + i;
            CSVFileProcessor.updateReviewText(ID_FOR_UPDATE, newReviewText, CSVFileProcessor.REVIEWS_FILE_PATH);
        }
        long endFile = System.nanoTime();
        double timeFileMs = (endFile - startFile) / 1_000_000.0;
        System.out.printf("[File I/O] 更新 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeFileMs);
    }

    /**
     * 测试 4: 删除 (Delete) 性能 - Reviews 表
     */
    private static void runDeleteTest() {
        System.out.println("\n--- 4. 开始删除 (Delete) 测试 ---");

        // --- DBMS 删除测试 ---
        long startDb = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            PostgreSQLOperations.deleteReview(ID_FOR_DELETE);
        }
        long endDb = System.nanoTime();
        double timeDbMs = (endDb - startDb) / 1_000_000.0;
        System.out.printf("[DBMS]   删除 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeDbMs);

        // --- File I/O 删除测试 ---
        long startFile = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            CSVFileProcessor.deleteReview(ID_FOR_DELETE, CSVFileProcessor.REVIEWS_FILE_PATH);
        }
        long endFile = System.nanoTime();
        double timeFileMs = (endFile - startFile) / 1_000_000.0;
        System.out.printf("[File I/O] 删除 %d 次耗时: %.4f ms%n", TEST_ITERATIONS, timeFileMs);
    }
}