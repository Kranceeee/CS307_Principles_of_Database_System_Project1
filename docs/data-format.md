# CSV 数据约定

导入读取一个目录中的以下 13 个文件，保持文件名和表头列顺序。列名忽略大小写，允许 UTF-8 BOM 和双引号包裹的表头。规范化 CSV 使用逗号分隔，字段内的逗号、双引号和换行遵循 PostgreSQL CSV 规则。未加引号的空字段表示 `NULL`，`""` 表示空字符串。

| 文件 | CSV 表头 |
|---|---|
| `User.csv` | `UserID,UserName,Gender,Age,Followers,Following` |
| `Category.csv` | `CategoryID,CategoryName` |
| `Keyword.csv` | `KeywordID,KeywordName` |
| `Ingredient.csv` | `IngredientID,IngredientName` |
| `Recipe.csv` | `RecipeID,AuthorUserID,Name,CookingTime,PreparationTime,TotalTime,DatePublished,Description,AggregateRating,ReviewCount,RecipeServings,RecipeYield,RecipeInstructions` |
| `Nutrition.csv` | `RecipeID,Calories,FatContent,SaturatedFatContent,CholesterolContent,SodiumContent,CarbohydrateContent,FiberContent,SugarContent,ProteinContent` |
| `Review.csv` | `ReviewID,RecipeID,UserID,Rating,ReviewText,DateSubmitted,DateModified,Likes` |
| `Recipe_Category.csv` | `RecipeID,CategoryID` |
| `Recipe_Keyword.csv` | `RecipeID,KeywordID` |
| `Recipe_Ingredient.csv` | `RecipeID,IngredientID,Quantity` |
| `User_Favorite_Recipe.csv` | `UserID,RecipeID` |
| `User_Follow.csv` | `FollowerUserID,FollowingUserID` |
| `User_Like_Review.csv` | `UserID,ReviewID` |

菜谱时间字段以分钟为单位。时间戳建议包含时区，例如 `2025-01-01T12:00:00Z`。`Recipe.csv` 至少要有一条数据行。其他数据文件允许只有表头，但所有已提供的外键仍须能在对应文件中找到父记录。

同一道菜的同一种食材允许出现多次，以保留原始明细。食材明细的行 ID 由数据库生成。评价、收藏、点赞等关系受原有的主键和外键约束保护，关注关系禁止自关注。

载入命令只接受空的 `recipe_lab` schema。任何文件失败都会回滚这次事务。错误会给出文件名和可用的 SQLSTATE。例如 `23503` 是外键问题，`23505` 是唯一键冲突，`22P02` 是类型转换失败。修正对应文件后，可用原命令再次导入。 [表结构](../src/main/resources/schema.sql) · [导入实现](../src/main/java/io/recipelab/RecipeImport.java) · [PostgreSQL COPY 文档](https://www.postgresql.org/docs/16/sql-copy.html)
