"""Generate deterministic, synthetic CSV files for Recipe Data Lab."""

import argparse
import csv
from pathlib import Path


DATE = "2025-01-01T12:00:00Z"
DISHES = (
    ("番茄鸡蛋面", (1, 2, 3), 1, "煮开水，下面条，最后放入番茄和鸡蛋。"),
    ("菠菜鸡蛋汤", (2, 4), 2, "将菠菜焯水，倒入蛋液，煮至汤再次沸腾。"),
    ("西兰花拌面", (3, 5), 1, "西兰花和面条分别煮熟，沥干后拌匀。"),
    ("番茄菠菜沙拉", (1, 4), 3, "洗净食材，将番茄和菠菜切好，按口味调味。"),
)


def write(directory, file, header, rows):
    with (directory / file).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(header.split(","))
        writer.writerows(rows)


def recipe_rows(count):
    for recipe_id in range(1, count + 1):
        dish, _, _, instructions = DISHES[(recipe_id - 1) % len(DISHES)]
        preparation = 5 + recipe_id % 7
        cooking = 10 + recipe_id % 25
        name = dish if recipe_id <= len(DISHES) else f"{dish} · 样例 {recipe_id}"
        yield (recipe_id, 1 + (recipe_id - 1) % 3, name, cooking, preparation, cooking + preparation,
               DATE, "供本地查询与性能实验使用的合成样本。", "4.0", 1, 2, "2 人份", instructions)


def generate(directory, count):
    directory.mkdir(parents=True, exist_ok=False)
    ids = range(1, count + 1)
    write(directory, "User.csv", "UserID,UserName,Gender,Age,Followers,Following",
          ((1, "编辑甲", None, None, 0, 1), (2, "编辑乙", None, None, 1, 0), (3, "编辑丙", None, None, 0, 0)))
    write(directory, "Category.csv", "CategoryID,CategoryName", enumerate(("面食", "汤羹", "凉菜"), 1))
    write(directory, "Keyword.csv", "KeywordID,KeywordName", [(1, "家常")])
    write(directory, "Ingredient.csv", "IngredientID,IngredientName", enumerate(("番茄", "鸡蛋", "面条", "菠菜", "西兰花"), 1))
    write(directory, "Recipe.csv", "RecipeID,AuthorUserID,Name,CookingTime,PreparationTime,TotalTime,DatePublished,"
          "Description,AggregateRating,ReviewCount,RecipeServings,RecipeYield,RecipeInstructions", recipe_rows(count))
    write(directory, "Nutrition.csv", "RecipeID,Calories,FatContent,SaturatedFatContent,CholesterolContent,"
          "SodiumContent,CarbohydrateContent,FiberContent,SugarContent,ProteinContent",
          ((i, 200 + i % 100, 8, 2, None, 300, 25, 3, 4, 12) for i in ids))
    write(directory, "Review.csv", "ReviewID,RecipeID,UserID,Rating,ReviewText,DateSubmitted,DateModified,Likes",
          ((i, i, 2, 4, '说明清晰，有 "家常" 风味。', DATE, DATE, int(i <= min(count, 5))) for i in ids))
    write(directory, "Recipe_Category.csv", "RecipeID,CategoryID", ((i, DISHES[(i - 1) % 4][2]) for i in ids))
    write(directory, "Recipe_Keyword.csv", "RecipeID,KeywordID", ((i, 1) for i in ids))
    write(directory, "Recipe_Ingredient.csv", "RecipeID,IngredientID,Quantity",
          ((i, ingredient, None) for i in ids for ingredient in DISHES[(i - 1) % 4][1]))
    write(directory, "User_Favorite_Recipe.csv", "UserID,RecipeID", ((1, i) for i in range(1, min(count, 5) + 1)))
    write(directory, "User_Follow.csv", "FollowerUserID,FollowingUserID", [(1, 2)])
    write(directory, "User_Like_Review.csv", "UserID,ReviewID", ((1, i) for i in range(1, min(count, 5) + 1)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--recipes", type=int, default=1000)
    args = parser.parse_args()
    if not 1 <= args.recipes <= 1_000_000:
        parser.error("--recipes must be between 1 and 1000000")
    try:
        generate(args.directory, args.recipes)
    except OSError as error:
        parser.exit(1, f"{error}\n")
    print(f"Wrote 13 CSV files for {args.recipes} recipes to {args.directory}")


if __name__ == "__main__":
    main()
