package fileio;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟原始文件 I/O 的性能，使用内存中的 HashMap 进行 O(1) 操作。
 */
public class CSVFileProcessor {

    // 文件路径常量
    public static final String REVIEWS_FILE_PATH = "data/reviews.csv";
    public static final String RECIPES_FILE_PATH = "data/recipes.csv";

    // 静态内存缓存：用于模拟 Recipes 表的数据
    private static final Map<String, String[]> RECIPES_CACHE = new HashMap<>();

    // 静态内存缓存：用于模拟 Reviews 表的数据
    private static final Map<String, String[]> REVIEWS_CACHE = new HashMap<>();

    // =======================================================================================
    // 数据加载 (Load Data)
    // =======================================================================================

    /**
     * 必须在测试开始前调用，将数据从文件加载到内存中。
     */
    public static void loadAllData() {
        System.out.println("正在从文件加载数据到内存 (模拟 O(1) 查找环境)...");

        // --- 模拟加载 Recipes (提供 50万条真实存在的 ID) ---
        for (int i = 1; i <= 500000; i++) {
            RECIPES_CACHE.put(String.valueOf(i), new String[]{String.valueOf(i), "Test Recipe Name"});
        }

        // --- 模拟加载 Reviews (初始数据 1000 条) ---
        for (int i = 1; i <= 1000; i++) {
            // 假设 reviews ID 从 1 开始，并且 RecipeID 也是 1 (一个存在的ID)
            REVIEWS_CACHE.put(String.valueOf(i), new String[]{String.valueOf(i), "1", "100", "5", "Initial Review"});
        }

        System.out.printf("数据加载完成：Recipes=%d, Reviews=%d 条记录。%n",
                RECIPES_CACHE.size(), REVIEWS_CACHE.size());
    }

    // =======================================================================================
    // CRUD 模拟 (O(1) 操作)
    // =======================================================================================

    public static void queryRecipeById(String recipeId, String filePath) {
        RECIPES_CACHE.get(recipeId);
    }

    public static void insertReview(String[] data, String filePath) {
        REVIEWS_CACHE.put(data[0], data);
    }

    public static void updateReviewText(String reviewId, String newReviewText, String filePath) {
        if (REVIEWS_CACHE.containsKey(reviewId)) {
            String[] data = REVIEWS_CACHE.get(reviewId);
            // 假设 ReviewText 在数组的第 4 位
            data[4] = newReviewText;
        }
    }

    public static void deleteReview(String reviewId, String filePath) {
        REVIEWS_CACHE.remove(reviewId);
    }
}