-- 1.1 Category (分类)
CREATE TABLE Category (
    CategoryId SERIAL PRIMARY KEY,
    CategoryName VARCHAR(100) NOT NULL UNIQUE  -- 满足 NOT NULL 要求
);

-- 1.2 Keyword (关键词)
CREATE TABLE Keyword (
    KeywordId SERIAL PRIMARY KEY,
    KeywordName VARCHAR(100) NOT NULL UNIQUE  -- 满足 NOT NULL 要求
);

-- 1.3 Ingredient (食材/配料)
CREATE TABLE Ingredient (
    IngredientId SERIAL PRIMARY KEY,
    IngredientName VARCHAR(255) NOT NULL UNIQUE -- 满足 NOT NULL 要求
);

-- =========================================================
-- 2. 创建核心实体表 (User)
-- =========================================================

-- 2.1 User (用户)
CREATE TABLE "User" (
    AuthId SERIAL PRIMARY KEY,
    AuthName VARCHAR(100) NOT NULL, -- 满足 NOT NULL 要求
    -- 扩展性考虑：将性别和年龄存储为简单属性，符合 3NF。
    Gender VARCHAR(10),
    Age SMALLINT CHECK (Age >= 0 AND Age <= 150), -- 限制年龄范围，使用 SMALLINT 节省空间
    Followers INT DEFAULT 0 NOT NULL,
    Following INT DEFAULT 0 NOT NULL
);

-- =========================================================
-- 3. 创建 Recipe (食谱) 表
-- Recipe 依赖于 User (AuthorId)
-- =========================================================

-- 3.1 Recipe (食谱)
CREATE TABLE Recipe (
    RecipeId BIGSERIAL PRIMARY KEY,
    AuthorId INT NOT NULL,  -- FK to User (用户 AuthId)
    Name VARCHAR(255) NOT NULL, -- 满足 NOT NULL 要求

    CookTime SMALLINT CHECK (CookTime >= 0),
    PrepTime SMALLINT CHECK (PrepTime >= 0),
    TotalTime SMALLINT CHECK (TotalTime >= 0),
    DatePublished TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    Description TEXT,
    AggregateRating NUMERIC(2, 1) CHECK (AggregateRating >= 0 AND AggregateRating <= 5),
    ReviewCount INT DEFAULT 0 NOT NULL,
    RecipeServings NUMERIC(5, 2),
    RecipeYield VARCHAR(100),
    RecipeInstructions TEXT NOT NULL, -- 步骤是食谱的核心，设为 NOT NULL

    FOREIGN KEY (AuthorId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE RESTRICT  -- 限制删除用户，除非先删除其所有食谱
);

-- =========================================================
-- 4. 创建 Recipe 的附属信息表 (Nutrition) 和评论表 (Review)
-- =========================================================

-- 4.1 Nutrition (营养信息)
-- 1:1 关系 (HasNutrition) - 将 RecipeId 作为主键和外键，保证 3NF 和 1:1 约束。
CREATE TABLE Nutrition (
    RecipeId BIGINT PRIMARY KEY, -- PK, FK to Recipe
    Calories INT CHECK (Calories >= 0) NOT NULL, -- 满足 NOT NULL 要求
    -- 所有营养成分使用 NUMERIC(6, 2) 保证精度和范围
    FatContent NUMERIC(6, 2) CHECK (FatContent >= 0),
    SaturatedFatContent NUMERIC(6, 2) CHECK (SaturatedFatContent >= 0),
    CholesterolContent NUMERIC(6, 2) CHECK (CholesterolContent >= 0),
    SodiumContent NUMERIC(6, 2) CHECK (SodiumContent >= 0),
    CarbohydrateContent NUMERIC(6, 2) CHECK (CarbohydrateContent >= 0),
    FiberContent NUMERIC(6, 2) CHECK (FiberContent >= 0),
    SugarContent NUMERIC(6, 2) CHECK (SugarContent >= 0),
    ProteinContent NUMERIC(6, 2) CHECK (ProteinContent >= 0),

    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- 4.2 Review (评论/评分)
-- 1:N 关系 (Submits)
CREATE TABLE Review (
    ReviewId BIGSERIAL PRIMARY KEY,
    RecipeId BIGINT NOT NULL,  -- FK1 to Recipe
    AuthId INT NOT NULL,       -- FK2 to User
    Rating SMALLINT NOT NULL CHECK (Rating >= 1 AND Rating <= 5), -- 满足 NOT NULL 要求
    Review TEXT,
    DateSubmitted TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    DateModified TIMESTAMP WITH TIME ZONE,
    Likes INT DEFAULT 0 NOT NULL,

    -- 确保一个用户只能对一个食谱提交一次评论 (业务逻辑约束)
    UNIQUE (RecipeId, AuthId),

    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (AuthId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE RESTRICT
);


-- =========================================================
-- 5. 创建所有 M:N 关系的关联表 (Junction Tables)
-- 这些表用于连接核心实体，保证 3NF。
-- =========================================================

-- 5.1 User_Follow (用户 M:N 用户)
-- 描述用户间的 'Follow' 关系
CREATE TABLE User_Follow (
    FollowerId INT NOT NULL,  -- 追随者 (FK to User)
    FollowingId INT NOT NULL, -- 被追随者 (FK to User)

    PRIMARY KEY (FollowerId, FollowingId),
    CHECK (FollowerId <> FollowingId), -- 自我关注约束

    FOREIGN KEY (FollowerId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (FollowingId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- 5.2 Recipe_Category (食谱 M:N 分类)
CREATE TABLE Recipe_Category (
    RecipeId BIGINT NOT NULL, -- FK to Recipe
    CategoryId INT NOT NULL,  -- FK to Category

    PRIMARY KEY (RecipeId, CategoryId),

    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (CategoryId) REFERENCES Category(CategoryId)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 5.3 Recipe_Keyword (食谱 M:N 关键词)
CREATE TABLE Recipe_Keyword (
    RecipeId BIGINT NOT NULL, -- FK to Recipe
    KeywordId INT NOT NULL,   -- FK to Keyword

    PRIMARY KEY (RecipeId, KeywordId),

    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (KeywordId) REFERENCES Keyword(KeywordId)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 5.4 Recipe_Ingredient (食谱 M:N 食材/配料)
-- 关系带属性 (Quantity, Unit, Notes)，全部存储在关联表，满足 3NF。
CREATE TABLE Recipe_Ingredient (
    RecipeId BIGINT NOT NULL,     -- FK to Recipe
    IngredientId INT NOT NULL,    -- FK to Ingredient

    Quantity NUMERIC(10, 2) NOT NULL, -- 满足 NOT NULL 要求
    Unit VARCHAR(50),
    Notes VARCHAR(255),

    PRIMARY KEY (RecipeId, IngredientId),

    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (IngredientId) REFERENCES Ingredient(IngredientId)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 5.5 User_Favorite_Recipe (用户 M:N 食谱 - 收藏)
CREATE TABLE User_Favorite_Recipe (
    AuthId INT NOT NULL,       -- FK to User
    RecipeId BIGINT NOT NULL,  -- FK to Recipe

    DateFavorited TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 满足 NOT NULL 要求

    PRIMARY KEY (AuthId, RecipeId),

    FOREIGN KEY (AuthId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (RecipeId) REFERENCES Recipe(RecipeId)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- 5.6 User_Like_Review (用户 M:N 评论 - 点赞)
CREATE TABLE User_Like_Review (
    AuthId INT NOT NULL,     -- FK to User
    ReviewId BIGINT NOT NULL, -- FK to Review

    DateLiked TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 满足 NOT NULL 要求

    PRIMARY KEY (AuthId, ReviewId),

    FOREIGN KEY (AuthId) REFERENCES "User"(AuthId)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (ReviewId) REFERENCES Review(ReviewId)
        ON UPDATE CASCADE ON DELETE CASCADE
);
