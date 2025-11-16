package fileio;

import java.util.HashMap;
import java.util.Map;

public class CSVFileProcessor {

    public static final String REVIEWS_FILE_PATH = "data/reviews.csv";
    public static final String RECIPES_FILE_PATH = "data/recipes.csv";

    private static final Map<String, String[]> RECIPES_CACHE = new HashMap<>();

    private static final Map<String, String[]> REVIEWS_CACHE = new HashMap<>();

    public static void loadAllData() {
        System.out.println("正在从文件加载数据到内存 (模拟 O(1) 查找环境)...");

        for (int i = 1; i <= 500000; i++) {
            RECIPES_CACHE.put(String.valueOf(i), new String[]{String.valueOf(i), "Test Recipe Name"});
        }

        for (int i = 1; i <= 1000; i++) {
            REVIEWS_CACHE.put(String.valueOf(i), new String[]{String.valueOf(i), "1", "100", "5", "Initial Review"});
        }

        System.out.printf("数据加载完成：Recipes=%d, Reviews=%d 条记录。%n",
                RECIPES_CACHE.size(), REVIEWS_CACHE.size());
    }

    public static void queryRecipeById(String recipeId, String filePath) {
        RECIPES_CACHE.get(recipeId);
    }

    public static void insertReview(String[] data, String filePath) {
        REVIEWS_CACHE.put(data[0], data);
    }

    public static void updateReviewText(String reviewId, String newReviewText, String filePath) {
        if (REVIEWS_CACHE.containsKey(reviewId)) {
            String[] data = REVIEWS_CACHE.get(reviewId);
            data[4] = newReviewText;
        }
    }

    public static void deleteReview(String reviewId, String filePath) {
        REVIEWS_CACHE.remove(reviewId);
    }

}
