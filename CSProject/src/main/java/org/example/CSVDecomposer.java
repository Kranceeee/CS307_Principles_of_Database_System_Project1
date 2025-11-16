package org.example;

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


public class CSVDecomposer {

    private static final String INPUT_DIR = "D:/RecipeImporter/CS307/data";
    private static final String OUTPUT_DIR = "D:/RecipeImporter/CS307/processedData";

    private static final String INPUT_USER_FILE = "user.csv";
    private static final String INPUT_RECIPE_FILE = "recipes.csv";
    private static final String INPUT_REVIEW_FILE = "reviews.csv";

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
        CSVDecomposer decomposer = new CSVDecomposer();
        try {
            decomposer.runDecomposition();
            System.out.println("数据分解成功 (V6 - R Vector Fix)！");
        } catch (IOException e) {
            System.err.println("处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runDecomposition() throws IOException {
        Path outputDir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDir);

        System.out.println("开始处理 User 文件 (" + INPUT_USER_FILE + ")...");
        processUserFile();

        System.out.println("开始处理 Recipe 文件 (" + INPUT_RECIPE_FILE + ")... (V6)");
        processRecipeFile();

        System.out.println("开始处理 Review 文件 (" + INPUT_REVIEW_FILE + ")... (V5)");
        processReviewFile();

        System.out.println("已生成13个CSV文件 (V6)，准备导入。");
    }


    private void processUserFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_USER_FILE);

        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);
                CSVPrinter userPrinter = createPrinter("User.csv", "UserIdentifier", "UserName", "Gender", "Age", "Followers", "Following");
                CSVPrinter followsPrinter = createPrinter("User_Follow.csv", "FollowerUserIdentifier", "FollowingUserIdentifier")
        ) {
            for (CSVRecord record : csvParser) {
                String userIdentifier = record.get("AuthorId");
                userPrinter.printRecord(
                        userIdentifier, record.get("AuthorName"), record.get("Gender"),
                        record.get("Age"), record.get("Followers"), record.get("Following")
                );
                List<String> followingUserLists = parseCsvListString(record.get("FollowingUsers"));
                for (String idListStr : followingUserLists) {
                    String[] individualIds = idListStr.split(",");
                    for (String followedId : individualIds) {
                        String trimmedId = followedId.trim();
                        if (!trimmedId.isEmpty()) {
                            followsPrinter.printRecord(userIdentifier, trimmedId);
                        }
                    }
                }
            }
            userPrinter.close(true);
            followsPrinter.close(true);
        }
    }


    private void processRecipeFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_RECIPE_FILE);

        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);

                CSVPrinter recipePrinter = createPrinter("Recipe.csv", "RecipeIdentifier", "AuthorUserIdentifier", "Name", "CookingTime", "PreparationTime", "TotalTime", "DatePublished", "Description", "AggregateRating", "ReviewCount", "RecipeServings", "RecipeYield", "RecipeInstructions");
                CSVPrinter nutritionPrinter = createPrinter("Nutrition.csv", "RecipeIdentifier", "Calories", "FatContent", "SaturatedFatContent", "CholesterolContent", "SodiumContent", "CarbohydrateContent", "FiberContent", "SugarContent", "ProteinContent");
                CSVPrinter categoryPrinter = createPrinter("Category.csv", "CategoryIdentifier", "CategoryName");
                CSVPrinter recipeCategoryPrinter = createPrinter("Recipe_Category.csv", "RecipeIdentifier", "CategoryIdentifier");
                CSVPrinter keywordPrinter = createPrinter("Keyword.csv", "KeywordIdentifier", "KeywordName");
                CSVPrinter recipeKeywordPrinter = createPrinter("Recipe_Keyword.csv", "RecipeIdentifier", "KeywordIdentifier");
                CSVPrinter ingredientPrinter = createPrinter("Ingredient.csv", "IngredientIdentifier", "IngredientName");
                CSVPrinter recipeIngredientPrinter = createPrinter("Recipe_Ingredient.csv", "RecipeIdentifier", "IngredientIdentifier", "Quantity");
                CSVPrinter userFavoriteRecipePrinter = createPrinter("User_Favorite_Recipe.csv", "UserIdentifier", "RecipeIdentifier");
        ) {
            for (CSVRecord record : csvParser) {
                String recipeId = record.get("RecipeId");

                recipePrinter.printRecord(
                        recipeId, record.get("AuthorId"), record.get("Name"),
                        parseDurationToMinutes(record.get("CookTime")),
                        parseDurationToMinutes(record.get("PrepTime")),
                        parseDurationToMinutes(record.get("TotalTime")),
                        record.get("DatePublished"), record.get("Description"),
                        getNumericString(record, "AggregatedRating"),
                        getNumericString(record, "ReviewCount"),
                        getNumericString(record, "RecipeServings"),
                        record.get("RecipeYield"),
                        cleanRVectorString(record.get("RecipeInstructions"))
                );

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

                List<String> ingredientNameLists = parseCsvListString(record.get("RecipeIngredientParts"));
                for (String ingredientNameList : ingredientNameLists) {
                    String[] individualNames = ingredientNameList.split(",");
                    for (String ingredientName : individualNames) {
                        ingredientName = ingredientName.trim();
                        if (ingredientName.isEmpty()) continue;
                        int ingredientId = ingredientMap.computeIfAbsent(ingredientName, k -> getNewId(ingredientIdCounter, ingredientPrinter, k));
                        recipeIngredientPrinter.printRecord(recipeId, ingredientId, "0", "", "");
                    }
                }

                List<String> favoriteUserLists = parseCsvListString(record.get("FavoriteUsers"));
                for (String userIdList : favoriteUserLists) {
                    String[] individualIds = userIdList.split(",");
                    for (String userId : individualIds) {
                        userId = userId.trim();
                        if (userId.isEmpty()) continue;
                        userFavoriteRecipePrinter.printRecord(userId, recipeId);
                    }
                }
            }

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

    private int getNewId(AtomicInteger counter, CSVPrinter printer, String name) {
        int newId = counter.getAndIncrement();
        try {
            printer.printRecord(newId, name);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to CSV in helper", e);
        }
        return newId;
    }


    private void processReviewFile() throws IOException {
        Path inputFile = Paths.get(INPUT_DIR, INPUT_REVIEW_FILE);
        try (
                BufferedReader reader = Files.newBufferedReader(inputFile);
                CSVParser csvParser = CSV_FORMAT.parse(reader);
                CSVPrinter reviewPrinter = createPrinter("Review.csv", "ReviewIdentifier", "RecipeIdentifier", "UserIdentifier", "Rating", "ReviewText", "DateSubmitted", "DateModified", "Likes");
                CSVPrinter likeReviewPrinter = createPrinter("User_Like_Review.csv", "UserIdentifier", "ReviewIdentifier")
        ) {
            for (CSVRecord record : csvParser) {
                String reviewId = record.get("ReviewId");

                String likesListStr = record.get("Likes");
                List<String> userLists = parseCsvListString(likesListStr);
                List<String> individualLikerIds = new ArrayList<>();

                for (String idListStr : userLists) {
                    String[] individualIds = idListStr.split(",");
                    for (String userId : individualIds) {
                        String trimmedId = userId.trim();
                        if (!trimmedId.isEmpty()) {
                            individualLikerIds.add(trimmedId);
                        }
                    }
                }
                int likesCount = individualLikerIds.size();

                for (String userId : individualLikerIds) {
                    likeReviewPrinter.printRecord(userId, reviewId, "");
                }


                reviewPrinter.printRecord(
                        reviewId, record.get("RecipeId"), record.get("AuthorId"),
                        record.get("Rating"),
                        cleanRVectorString(record.get("Review")),

                        record.get("DateSubmitted"), record.get("DateModified"),
                        likesCount
                );
            }
            reviewPrinter.close(true);
            likeReviewPrinter.close(true);
        }
    }

    private String cleanRVectorString(String rVector) {
        if (rVector == null || rVector.isEmpty()) {
            return null;
        }

        boolean isRVector = rVector.startsWith("c(") && rVector.endsWith(")");

        boolean isQuotedString = rVector.length() > 1 && rVector.startsWith("\"") && rVector.endsWith("\"");

        if (isRVector) {
            List<String> steps = new ArrayList<>();
            Matcher matcher = LIST_PARSE_PATTERN.matcher(rVector);

            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    steps.add(matcher.group(1).replace("\"\"", "\""));
                } else if (matcher.group(2) != null) {
                    steps.add(matcher.group(2).replace("''", "'"));
                }
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
            if (matcher.group(1) != null) {
                items.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                items.add(matcher.group(2));
            }
        }

        if (!foundMatch) {
            String cleaned = listString.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            if ( (cleaned.startsWith("'") && cleaned.endsWith("'")) ||
                    (cleaned.startsWith("\"") && cleaned.endsWith("\"")) ) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
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
        if (durationStr.matches("\\d+")) {
            try { return Integer.parseInt(durationStr); } catch (NumberFormatException e) { return 0; }
        }
        if (!durationStr.startsWith("PT")) { return 0; }

        String duration = durationStr.substring(2);
        int totalMinutes = 0;
        try {
            int hIndex = duration.indexOf('H');
            if (hIndex != -1) {
                totalMinutes += Integer.parseInt(duration.substring(0, hIndex)) * 60;
                duration = duration.substring(hIndex + 1);
            }
            int mIndex = duration.indexOf('M');
            if (mIndex != -1) {
                totalMinutes += Integer.parseInt(duration.substring(0, mIndex));
            }
        } catch (NumberFormatException e) {
            System.err.println("警告: 无法解析持续时间: " + durationStr + "，将使用 0。");
            return 0;
        }
        return totalMinutes;
    }

    private String getNumericString(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            System.err.println("警告: 找不到列 " + header + "，将使用 NULL。");
            return null;
        }
        String val = record.get(header);
        if (val == null || val.isEmpty() || val.equalsIgnoreCase("N/A")) {
            return null;
        }
        try {
            Double.parseDouble(val);
            return val;
        } catch (NumberFormatException e) {
            System.err.println("警告: (getNumericString) " + header + " 的值无效: " + val + "，将使用 NULL。");
            return null;
        }
    }
}