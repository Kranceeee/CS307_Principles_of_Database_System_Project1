package org.example; // 确保这与您的包名一致

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * (已更新 - 最终版本 V10)
 * - 包含 V9 的所有修复 (路径, 表头, ID 浮点数)。
 * - (V10 关键修复) 在 processUserFile, processRecipeFile, 和 processReviewFile
 * 中添加了严格的 NULL 检查。
 * - 如果一行数据的主键 (PK) 或 关键外键 (FK) 为 NULL (来自不良的原始数据)，
 * 则该行将被完全跳过，以防止 'violates not-null constraint' 错误。
 */
public class CSVDecomposerV2 { // [!! 重命名 !!]

    // --- 配置 (V9.1 路径修复 - 保持不变) ---
    private static final String INPUT_DIR = "D:/RecipeImporter/CS307/data";
    private static final String OUTPUT_DIR = "D:/RecipeImporter/CS307/processedData";

    private static final String INPUT_USER_FILE = "user.csv";
    private static final String INPUT_RECIPE_FILE = "recipes.csv";
    private static final String INPUT_REVIEW_FILE = "reviews.csv";

    // ... (所有 Map, Counter, CSVFormat, Pattern 保持不变) ...
    private final Map<String, Integer> ingredientMap = new HashMap<>();
    private final AtomicInteger ingredientIdCounter = new AtomicInteger(1);
    private final Map<String, Integer> categoryMap = new HashMap<>();
    private final AtomicInteger categoryIdCounter = new AtomicInteger(1);
    private final Map<String, Integer> keywordMap = new HashMap<>();
    private final AtomicInteger keywordIdCounter = new AtomicInteger(1);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .build();
    private static final Pattern LIST_PARSE_PATTERN = Pattern.compile("(?:\")([^\"]*)(?:\")|(?:')([^']*)(?:')");


    public static void main(String[] args) {
        CSVDecomposerV2 decomposer = new CSVDecomposerV2(); // [!! V10 !!]
        try {
            decomposer.runDecomposition();
            System.out.println("数据分解成功 (V10 - NOT NULL 约束修复)！");
            System.out.println("输出的 13 个 CSV 文件现在已准备好用于高速 COPY 导入。");
        } catch (IOException e) {
            System.err.println("处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runDecomposition() throws IOException {
        Path outputDir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDir);

        System.out.println("开始处理 User 文件 (" + INPUT_USER_FILE + ")... (V10)");
        processUserFile();

        System.out.println("开始处理 Recipe 文件 (" + INPUT_RECIPE_FILE + ")... (V10)");
        processRecipeFile();

        System.out.println("开始处理 Review 文件 (" + INPUT_REVIEW_FILE + ")... (V10)");
        processReviewFile();

        System.out.println("已生成13个CSV文件 (V10)，准备导入。");
    }

    /**
     * 1. 处理用户文件 (V10 更新)
     * - (V10) 如果 UserID (PK) 为 null，则跳过该行。
     */
    private void processUserFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_USER_FILE);

        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);
                // V8 表头修复 (保持不变)
                CSVPrinter userPrinter = createPrinter("User.csv", "UserID", "UserName", "Gender", "Age", "Followers", "Following");
                CSVPrinter followsPrinter = createPrinter("User_Follow.csv", "FollowerUserID", "FollowingUserID")
        ) {
            for (CSVRecord record : csvParser) {
                // V9 修复 (保持不变)
                String userIdentifier = getIntegerString(record, "AuthorId");

                // [!! V10 修复 !!]
                // 如果用户的主键 (UserID) 为 null，则此行无效。
                if (userIdentifier == null) {
                    System.err.println("  > [V10] 跳过无效的 User 记录 (UserID 为 null)，在原始文件第 " + record.getRecordNumber() + " 行附近。");
                    continue; // 跳到下一个 CSVRecord
                }

                userPrinter.printRecord(
                        userIdentifier,
                        record.get("AuthorName"),
                        record.get("Gender"),
                        getIntegerString(record, "Age"),
                        getIntegerString(record, "Followers"),
                        getIntegerString(record, "Following")
                );

                List<String> followingUserLists = parseCsvListString(record.get("FollowingUsers"));
                for (String idListStr : followingUserLists) {
                    String[] individualIds = idListStr.split(",");
                    for (String followedId : individualIds) {
                        String trimmedId = followedId.trim();
                        if (!trimmedId.isEmpty()) {
                            // V9 修复 (保持不变)
                            String cleanedId = cleanIntegerString(trimmedId);
                            if (cleanedId != null) { // [!! V10 修复 !!] 确保我们不会写入 null 的 FK
                                followsPrinter.printRecord(userIdentifier, cleanedId);
                            }
                        }
                    }
                }
            }
            userPrinter.close(true);
            followsPrinter.close(true);
        }
    }

    /**
     * 2. 处理菜谱文件 (V10 更新)
     * - (V10) 如果 RecipeID (PK) 或 AuthorUserID (FK) 为 null，则跳过该行。
     */
    private void processRecipeFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_RECIPE_FILE);

        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);

                // V8 表头修复 (保持不变)
                CSVPrinter recipePrinter = createPrinter("Recipe.csv", "RecipeID", "AuthorUserID", "Name", "CookingTime", "PreparationTime", "TotalTime", "DatePublished", "Description", "AggregateRating", "ReviewCount", "RecipeServings", "RecipeYield", "RecipeInstructions");
                CSVPrinter nutritionPrinter = createPrinter("Nutrition.csv", "RecipeID", "Calories", "FatContent", "SaturatedFatContent", "CholesterolContent", "SodiumContent", "CarbohydrateContent", "FiberContent", "SugarContent", "ProteinContent");
                CSVPrinter categoryPrinter = createPrinter("Category.csv", "CategoryID", "CategoryName");
                CSVPrinter recipeCategoryPrinter = createPrinter("Recipe_Category.csv", "RecipeID", "CategoryID");
                CSVPrinter keywordPrinter = createPrinter("Keyword.csv", "KeywordID", "KeywordName");
                CSVPrinter recipeKeywordPrinter = createPrinter("Recipe_Keyword.csv", "RecipeID", "KeywordID");
                CSVPrinter ingredientPrinter = createPrinter("Ingredient.csv", "IngredientID", "IngredientName");
                CSVPrinter recipeIngredientPrinter = createPrinter("Recipe_Ingredient.csv", "RecipeID", "IngredientID", "Quantity");
                CSVPrinter userFavoriteRecipePrinter = createPrinter("User_Favorite_Recipe.csv", "UserID", "RecipeID");
        ) {
            for (CSVRecord record : csvParser) {
                // V9 修复 (保持不变)
                String recipeId = getIntegerString(record, "RecipeId");
                String authorId = getIntegerString(record, "AuthorId");

                // [!! V10 修复 !!]
                // 如果食谱的主键 (RecipeID) 或关键外键 (AuthorUserID) 为 null，则此行无效。
                if (recipeId == null || authorId == null) {
                    System.err.println("  > [V10] 跳过无效的 Recipe 记录 (PK/FK 为 null)，在原始文件第 " + record.getRecordNumber() + " 行附近。 RecipeID=" + recipeId + ", AuthorID=" + authorId);
                    continue; // 跳到下一个 CSVRecord
                }

                // 1. 写入 Recipe.csv (现在是安全的)
                recipePrinter.printRecord(
                        recipeId, authorId, record.get("Name"),
                        parseDurationToMinutes(record.get("CookTime")),
                        parseDurationToMinutes(record.get("PrepTime")),
                        parseDurationToMinutes(record.get("TotalTime")),
                        record.get("DatePublished"),
                        record.get("Description"),
                        getNumericString(record, "AggregatedRating"),
                        getIntegerString(record, "ReviewCount"),
                        getIntegerString(record, "RecipeServings"),
                        record.get("RecipeYield"),
                        cleanRVectorString(record.get("RecipeInstructions"))
                );

                // 2. 写入 Nutrition.csv (现在是安全的)
                nutritionPrinter.printRecord(
                        recipeId,
                        getNumericString(record, "Calories"),
                        getNumericString(record, "FatContent"),
                        getNumericString(record, "SaturatedFatContent"),
                        getNumericString(record, "CholesterolContent"),
                        getNumericString(record, "SodiumContent"),
                        getNumericString(record, "CarbohydrateContent"),
                        getNumericString(record, "FiberContent"),
                        getNumericString(record, "SugarContent"),
                        getNumericString(record, "ProteinContent")
                );

                // 3. 处理 Category (现在是安全的)
                List<String> categoryNameLists = parseCsvListString(record.get("RecipeCategory"));
                for (String categoryNameList : categoryNameLists) {
                    String[] individualNames = categoryNameList.split(",");
                    for (String categoryName : individualNames) {
                        categoryName = categoryName.trim();
                        if (categoryName.isEmpty()) continue;
                        int categoryId = categoryMap.computeIfAbsent(categoryName, k -> getNewId(categoryIdCounter, categoryPrinter, k));
                        recipeCategoryPrinter.printRecord(recipeId, categoryId);
                    }
                }

                // 4. 处理 Keyword (现在是安全的)
                List<String> keywordNameLists = parseCsvListString(record.get("Keywords"));
                for (String keywordNameList : keywordNameLists) {
                    String[] individualNames = keywordNameList.split(",");
                    for (String keywordName : individualNames) {
                        keywordName = keywordName.trim();
                        if (keywordName.isEmpty()) continue;
                        int keywordId = keywordMap.computeIfAbsent(keywordName, k -> getNewId(keywordIdCounter, keywordPrinter, k));
                        recipeKeywordPrinter.printRecord(recipeId, keywordId);
                    }
                }

                // 5. 处理 Ingredient (现在是安全的)
                List<String> ingredientNameLists = parseCsvListString(record.get("RecipeIngredientParts"));
                for (String ingredientNameList : ingredientNameLists) {
                    String[] individualNames = ingredientNameList.split(",");
                    for (String ingredientName : individualNames) {
                        ingredientName = ingredientName.trim();
                        if (ingredientName.isEmpty()) continue;
                        int ingredientId = ingredientMap.computeIfAbsent(ingredientName, k -> getNewId(ingredientIdCounter, ingredientPrinter, k));
                        recipeIngredientPrinter.printRecord(recipeId, ingredientId, null);
                    }
                }

                // 6. 处理 FavoriteUsers (现在是安全的)
                List<String> favoriteUserLists = parseCsvListString(record.get("FavoriteUsers"));
                for (String userIdList : favoriteUserLists) {
                    String[] individualIds = userIdList.split(",");
                    for (String favUserId : individualIds) {
                        favUserId = favUserId.trim();
                        if (favUserId.isEmpty()) continue;
                        String cleanedId = cleanIntegerString(favUserId);
                        if (cleanedId != null) { // [!! V10 修复 !!] 确保我们不会写入 null 的 FK
                            userFavoriteRecipePrinter.printRecord(cleanedId, recipeId);
                        }
                    }
                }
            }
            // (关闭所有 printer - 保持不变)
            recipePrinter.close(true);
            nutritionPrinter.close(true);
            categoryPrinter.close(true);
            recipeCategoryPrinter.close(true);
            keywordPrinter.close(true);
            recipeKeywordPrinter.close(true);
            ingredientPrinter.close(true);
            recipeIngredientPrinter.close(true);
            userFavoriteRecipePrinter.close(true);
        }
    }

    // (getNewId 保持不变)
    private int getNewId(AtomicInteger counter, CSVPrinter printer, String name) {
        int newId = counter.getAndIncrement();
        try {
            printer.printRecord(newId, name);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to CSV in helper", e);
        }
        return newId;
    }

    /**
     * 3. 处理评论文件 (V10 更新)
     * - (V10) 如果 ReviewID (PK), RecipeID (FK), 或 UserID (FK) 为 null，则跳过该行。
     */
    private void processReviewFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_REVIEW_FILE);
        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);
                // V8 表头修复 (保持不变)
                CSVPrinter reviewPrinter = createPrinter("Review.csv", "ReviewID", "RecipeID", "UserID", "Rating", "ReviewText", "DateSubmitted", "DateModified", "Likes");
                CSVPrinter likeReviewPrinter = createPrinter("User_Like_Review.csv", "UserID", "ReviewID")
        ) {
            for (CSVRecord record : csvParser) {
                // V9 修复 (保持不变)
                String reviewId = getIntegerString(record, "ReviewId");
                String recipeId = getIntegerString(record, "RecipeId");
                String userId = getIntegerString(record, "AuthorId");

                // [!! V10 修复 !!]
                // 这就是修复您报告的错误的地方。
                // 如果评论的主键或任何关键外键为 null，则此行无效。
                if (reviewId == null || recipeId == null || userId == null) {
                    System.err.println("  > [V10] 跳过无效的 Review 记录 (PK/FK 为 null)，在原始文件第 " + record.getRecordNumber() + " 行附近。 ReviewID=" + reviewId + ", RecipeID=" + recipeId + ", UserID=" + userId);
                    continue; // 跳到下一个 CSVRecord
                }


                // Likes 处理 (现在是安全的)
                String likesListStr = record.get("Likes");
                List<String> userLists = parseCsvListString(likesListStr);
                List<String> individualLikerIds = new ArrayList<>();

                for (String idListStr : userLists) {
                    String[] individualIds = idListStr.split(",");
                    for (String likerId : individualIds) {
                        String trimmedId = likerId.trim();
                        if (!trimmedId.isEmpty()) {
                            individualLikerIds.add(trimmedId);
                        }
                    }
                }
                int likesCount = individualLikerIds.size();

                for (String likerId : individualLikerIds) {
                    // V9 修复 (保持不变)
                    String cleanedLikerId = cleanIntegerString(likerId);
                    if (cleanedLikerId != null) { // [!! V10 修复 !!] 确保我们不会写入 null 的 FK
                        likeReviewPrinter.printRecord(cleanedLikerId, reviewId);
                    }
                }

                // 写入 Review.csv (现在是安全的)
                reviewPrinter.printRecord(
                        reviewId,
                        recipeId,
                        userId,
                        getIntegerString(record, "Rating"),
                        cleanRVectorString(record.get("Review")),
                        record.get("DateSubmitted"),
                        record.get("DateModified"),
                        likesCount
                );
            }
            reviewPrinter.close(true);
            likeReviewPrinter.close(true);
        }
    }

    // --- 辅助方法 (保持 V9 不变) ---

    // (cleanRVectorString, parseCsvListString, createPrinter, parseDurationToMinutes, getNumericString (2x), getIntegerString, cleanIntegerString)
    // ... 所有这些方法都保持不变 ...

    private String cleanRVectorString(String rVector) {
        if (rVector == null || rVector.isEmpty()) { return null; }
        boolean isRVector = rVector.startsWith("c(") && rVector.endsWith(")");
        boolean isQuotedString = rVector.length() > 1 && rVector.startsWith("\"") && rVector.endsWith("\"");

        if (isRVector) {
            List<String> steps = new ArrayList<>();
            Matcher matcher = LIST_PARSE_PATTERN.matcher(rVector);
            while (matcher.find()) {
                if (matcher.group(1) != null) { steps.add(matcher.group(1).replace("\"\"", "\"")); }
                else if (matcher.group(2) != null) { steps.add(matcher.group(2).replace("''", "'")); }
            }
            return String.join("\n", steps);
        } else if (isQuotedString) {
            return rVector.substring(1, rVector.length() - 1).replace("\"\"", "\"");
        }
        return rVector;
    }

    private List<String> parseCsvListString(String listString) {
        List<String> items = new ArrayList<>();
        if (listString == null || listString.equals("[]") || listString.isEmpty()) {
            return items;
        }
        Matcher matcher = LIST_PARSE_PATTERN.matcher(listString);
        boolean foundMatch = false;
        while (matcher.find()) {
            foundMatch = true;
            if (matcher.group(1) != null) { items.add(matcher.group(1)); }
            else if (matcher.group(2) != null) { items.add(matcher.group(2)); }
        }
        if (!foundMatch) {
            String cleaned = listString.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) { cleaned = cleaned.substring(1, cleaned.length() - 1).trim(); }
            if ( (cleaned.startsWith("'") && cleaned.endsWith("'")) || (cleaned.startsWith("\"") && cleaned.endsWith("\"")) ) { cleaned = cleaned.substring(1, cleaned.length() - 1); }
            if (!cleaned.isEmpty()) { items.add(cleaned); }
        }
        return items;
    }

    private CSVPrinter createPrinter(String fileName, String... headers) throws IOException {
        Path path = Paths.get(OUTPUT_DIR, fileName);
        BufferedWriter writer = Files.newBufferedWriter(path);
        return new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers).build());
    }

    private int parseDurationToMinutes(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) { return 0; }
        if (durationStr.matches("\\d+")) { try { return Integer.parseInt(durationStr); } catch (NumberFormatException e) { return 0; } }
        if (!durationStr.startsWith("PT")) { return 0; }
        String duration = durationStr.substring(2);
        int totalMinutes = 0;
        try {
            int hIndex = duration.indexOf('H');
            if (hIndex != -1) { totalMinutes += Integer.parseInt(duration.substring(0, hIndex)) * 60; duration = duration.substring(hIndex + 1); }
            int mIndex = duration.indexOf('M');
            if (mIndex != -1) { totalMinutes += Integer.parseInt(duration.substring(0, mIndex)); }
        } catch (NumberFormatException e) { return 0; }
        return totalMinutes;
    }

    private String getNumericString(CSVRecord record, String header) {
        if (!record.isMapped(header)) { System.err.println("警告: 找不到列 " + header); return null; }
        return getNumericString(record.get(header));
    }

    private String getNumericString(String val) {
        if (val == null || val.isEmpty() || val.equalsIgnoreCase("N/A") || val.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            Double.parseDouble(val);
            return val;
        } catch (NumberFormatException e) {
            System.err.println("警告: (getNumericString) 值无效: " + val + "，将使用 NULL。");
            return null;
        }
    }

    private String getIntegerString(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            System.err.println("警告: 找不到列 " + header + "，将使用 NULL。");
            return null;
        }
        String val = record.get(header);
        return cleanIntegerString(val);
    }

    private String cleanIntegerString(String val) {
        if (val == null || val.isEmpty() || val.equalsIgnoreCase("N/A") || val.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            double dValue = Double.parseDouble(val);
            int iValue = (int) dValue;
            return String.valueOf(iValue);
        } catch (NumberFormatException e) {
            System.err.println("警告: (cleanIntegerString) 值无效: " + val + "，将使用 NULL。");
            return null;
        }
    }
}