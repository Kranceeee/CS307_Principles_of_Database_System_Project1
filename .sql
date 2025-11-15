/*
 * 表：Users
 * 存储食谱作者和评论者的信息
 */
CREATE TABLE Users (
    UserID INT PRIMARY KEY NOT NULL,
    UserName VARCHAR(255) NOT NULL,
    Gender VARCHAR(50) NULL,
    Age INT NULL,
    Followers INT NOT NULL DEFAULT 0,
    Following INT NOT NULL DEFAULT 0
);

/*
 * 表：Categories
 * 存储所有唯一的食谱分类
 */
CREATE TABLE Categories (
    CategoryID INT PRIMARY KEY NOT NULL,
    CategoryName VARCHAR(255) NOT NULL
);

/*
 * 表：Keywords
 * 存储所有唯一的食谱关键词
 */
CREATE TABLE Keywords (
    KeywordID INT PRIMARY KEY NOT NULL,
    KeywordName VARCHAR(255) NOT NULL
);

/*
 * 表：Ingredients
 * 存储所有唯一的配料
 */
CREATE TABLE Ingredients (
    IngredientID INT PRIMARY KEY NOT NULL,
    IngredientName VARCHAR(255) NOT NULL
);

/*
 * 表：Recipes
 * 存储核心的食谱信息
 */
CREATE TABLE Recipes (
    RecipeID INT PRIMARY KEY NOT NULL,
    AuthorUserID INT NOT NULL,
    Name VARCHAR(255) NOT NULL,
    CookingTime INT NULL,
    PreparationTime INT NULL,
    TotalTime INT NULL,
    DatePublished TIMESTAMPTZ NULL,  -- [修正] DATETIME -> TIMESTAMPTZ
    Description TEXT NULL,
    AggregateRating DECIMAL(3, 1) NULL,
    ReviewCount INT NULL,
    RecipeServings INT NULL,
    RecipeYield VARCHAR(100) NULL,
    RecipeInstructions TEXT NULL,

    FOREIGN KEY (AuthorUserID) REFERENCES Users(UserID)
);

/*
 * 表：Nutrition
 * 存储食谱的营养信息 (一对一关系)
 */
CREATE TABLE Nutrition (
    RecipeID INT PRIMARY KEY NOT NULL,
    Calories DECIMAL(10, 2) NULL,
    FatContent DECIMAL(10, 2) NULL,
    ProteinContent DECIMAL(10, 2) NULL,

    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID)
);

/*
 * 表：RecipeCategories
 * 关联表 (多对多): Recipes <-> Categories
 */
CREATE TABLE RecipeCategories (
    RecipeID INT NOT NULL,
    CategoryID INT NOT NULL,

    PRIMARY KEY (RecipeID, CategoryID),
    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID),
    FOREIGN KEY (CategoryID) REFERENCES Categories(CategoryID)
);

/*
 * 表：RecipeKeywords
 * 关联表 (多对多): Recipes <-> Keywords
 */
CREATE TABLE RecipeKeywords (
    RecipeID INT NOT NULL,
    KeywordID INT NOT NULL,

    PRIMARY KEY (RecipeID, KeywordID),
    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID),
    FOREIGN KEY (KeywordID) REFERENCES Keywords(KeywordID)
);

/*
 * 表：RecipeIngredients
 * 关联表 (多对多): Recipes <-> Ingredients (包含用量)
 */
CREATE TABLE RecipeIngredients (
    RecipeID INT NOT NULL,
    IngredientID INT NOT NULL,
    Quantity DECIMAL(10, 2) NULL,
    Unit VARCHAR(50) NULL,
    Notes VARCHAR(255) NULL,

    PRIMARY KEY (RecipeID, IngredientID),
    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID),
    FOREIGN KEY (IngredientID) REFERENCES Ingredients(IngredientID)
);

/*
 * 表：Reviews
 * 存储用户对食谱的评论和评分
 */
CREATE TABLE Reviews (
    ReviewID INT PRIMARY KEY NOT NULL,
    RecipeID INT NOT NULL,
    UserID INT NOT NULL,
    Rating INT NOT NULL,
    ReviewText TEXT NULL,
    DateSubmitted TIMESTAMPTZ NOT NULL,  -- [修正] DATETIME -> TIMESTAMPTZ
    DateModified TIMESTAMPTZ NOT NULL,   -- [修正] DATETIME -> TIMESTAMPTZ
    Likes INT NOT NULL DEFAULT 0,

    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID),
    FOREIGN KEY (UserID) REFERENCES Users(UserID),

    CHECK (Rating >= 0 AND Rating <= 5)
);

/* * 11. 表：UserFavorites (来自 User_Favorite_Recipe.csv)
 * 关联表 (多对多): Users <-> Recipes
 */
CREATE TABLE UserFavorites (
    UserID INT NOT NULL,
    RecipeID INT NOT NULL,
    DateFavorited TIMESTAMPTZ NULL, -- CSV数据显示此列可为空

    PRIMARY KEY (UserID, RecipeID),
    FOREIGN KEY (UserID) REFERENCES Users(UserID),
    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID)
);



/* * 12. 表：UserFollows (来自 User_Follow.csv)
 * 关联表 (自引用): Users <-> Users
 */
CREATE TABLE UserFollows (
    FollowerUserID INT NOT NULL,    -- 关注者
    FollowingUserID INT NOT NULL,   -- 被关注者

    PRIMARY KEY (FollowerUserID, FollowingUserID),
    FOREIGN KEY (FollowerUserID) REFERENCES Users(UserID),
    FOREIGN KEY (FollowingUserID) REFERENCES Users(UserID),

    -- 约束：防止用户自己关注自己
    CHECK (FollowerUserID != FollowingUserID)
);

/* * 13. 表：ReviewLikes (来自 User_Like_Review.csv)
 * 关联表 (多对多): Users <-> Reviews
 */
CREATE TABLE ReviewLikes (
    UserID INT NOT NULL,
    ReviewID INT NOT NULL,
    DateLiked TIMESTAMPTZ NULL, -- CSV数据显示此列可为空

    PRIMARY KEY (UserID, ReviewID),
    FOREIGN KEY (UserID) REFERENCES Users(UserID),
    FOREIGN KEY (ReviewID) REFERENCES Reviews(ReviewID)
);

CREATE TABLE RecipeIngredients (
    RecipeIngredientID SERIAL PRIMARY KEY, -- [新增] 自增主键
    RecipeID INT NOT NULL,
    IngredientID INT NOT NULL,
    Quantity DECIMAL(10, 2) NULL,
    Unit VARCHAR(50) NULL,
    Notes VARCHAR(255) NULL,

    -- 仍然保留外键
    FOREIGN KEY (RecipeID) REFERENCES Recipes(RecipeID),
    FOREIGN KEY (IngredientID) REFERENCES Ingredients(IngredientID)

    -- [已移除] PRIMARY KEY (RecipeID, IngredientID)
);
