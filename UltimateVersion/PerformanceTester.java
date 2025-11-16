import dbms.PostgreSQLOperations;
import fileio.CSVFileProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

public class PerformanceTester {

    private static final int TEST_ITERATIONS = 1000;
    private static final Random RANDOM = new Random();

    private static final List<String> EXIST_IDS = new ArrayList<>();
    private static final List<String[]> INSERT_DATA = new ArrayList<>();

    private static String ID_FOR_UPDATE = "90000000";
    private static String ID_FOR_DELETE = "90000001";

    public static void main(String[] args) {
        System.out.println("开始 Task 4 性能对比测试...");

        try {
            setupTestData();

            runQueryTest();
            runInsertTest();
            runUpdateTest();
            runDeleteTest();

        } catch (Exception e) {
            System.err.println("致命错误，测试中止: " + e.getMessage());
        }

        System.out.println("\n--- 性能对比测试完成 ---");
    }

    private static void setupTestData() {
        System.out.println("--- 准备测试数据 ---");

        CSVFileProcessor.loadAllData();

        int MAX_EXISTING_RECIPE_ID = 10;

        EXIST_IDS.clear();
        for (int i = 1; i <= MAX_EXISTING_RECIPE_ID; i++) {
            EXIST_IDS.add(String.valueOf(i));
        }

        while (EXIST_IDS.size() < TEST_ITERATIONS) {
            EXIST_IDS.add(EXIST_IDS.get(RANDOM.nextInt(MAX_EXISTING_RECIPE_ID)));
        }

        // Prepare INSERT data: [ReviewID, RecipeID, UserID, Rating, ReviewText]
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String newReviewId = String.valueOf(90000000 + i);

            String existRecipeId = EXIST_IDS.get(RANDOM.nextInt(TEST_ITERATIONS));

            String existUserId = "1000";

            INSERT_DATA.add(new String[]{
                    newReviewId,
                    existRecipeId,
                    existUserId,
                    String.valueOf(RANDOM.nextInt(5) + 1),
                    "Test Review Text " + UUID.randomUUID().toString().substring(0, 8)
            });
        }

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
