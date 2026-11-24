CREATE SCHEMA IF NOT EXISTS recipe_lab;

CREATE TABLE IF NOT EXISTS recipe_lab.users (
    userid INTEGER PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    gender VARCHAR(50),
    age INTEGER,
    followers INTEGER NOT NULL DEFAULT 0,
    following INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_lab.categories (
    categoryid INTEGER PRIMARY KEY,
    categoryname VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS recipe_lab.keywords (
    keywordid INTEGER PRIMARY KEY,
    keywordname VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS recipe_lab.ingredients (
    ingredientid INTEGER PRIMARY KEY,
    ingredientname VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS recipe_lab.recipes (
    recipeid INTEGER PRIMARY KEY,
    authoruserid INTEGER NOT NULL REFERENCES recipe_lab.users(userid),
    name VARCHAR(255) NOT NULL,
    cookingtime INTEGER,
    preparationtime INTEGER,
    totaltime INTEGER,
    datepublished TIMESTAMPTZ,
    description TEXT,
    aggregaterating NUMERIC(3,1),
    reviewcount INTEGER,
    recipeservings INTEGER,
    recipeyield VARCHAR(100),
    recipeinstructions TEXT
);

CREATE TABLE IF NOT EXISTS recipe_lab.nutrition (
    recipeid INTEGER PRIMARY KEY REFERENCES recipe_lab.recipes(recipeid),
    calories NUMERIC(10,2),
    fatcontent NUMERIC(10,2),
    saturatedfatcontent NUMERIC(10,2),
    cholesterolcontent NUMERIC(10,2),
    sodiumcontent NUMERIC(10,2),
    carbohydratecontent NUMERIC(10,2),
    fibercontent NUMERIC(10,2),
    sugarcontent NUMERIC(10,2),
    proteincontent NUMERIC(10,2)
);

CREATE TABLE IF NOT EXISTS recipe_lab.reviews (
    reviewid INTEGER PRIMARY KEY,
    recipeid INTEGER NOT NULL REFERENCES recipe_lab.recipes(recipeid),
    userid INTEGER NOT NULL REFERENCES recipe_lab.users(userid),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 0 AND 5),
    reviewtext TEXT,
    datesubmitted TIMESTAMPTZ NOT NULL,
    datemodified TIMESTAMPTZ NOT NULL,
    likes INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recipe_lab.recipecategories (
    recipeid INTEGER REFERENCES recipe_lab.recipes(recipeid),
    categoryid INTEGER REFERENCES recipe_lab.categories(categoryid),
    PRIMARY KEY (recipeid, categoryid)
);

CREATE TABLE IF NOT EXISTS recipe_lab.recipekeywords (
    recipeid INTEGER REFERENCES recipe_lab.recipes(recipeid),
    keywordid INTEGER REFERENCES recipe_lab.keywords(keywordid),
    PRIMARY KEY (recipeid, keywordid)
);

CREATE TABLE IF NOT EXISTS recipe_lab.recipeingredients (
    recipeingredientid BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipeid INTEGER NOT NULL REFERENCES recipe_lab.recipes(recipeid),
    ingredientid INTEGER NOT NULL REFERENCES recipe_lab.ingredients(ingredientid),
    quantity NUMERIC(10,2)
);

CREATE TABLE IF NOT EXISTS recipe_lab.userfavorites (
    userid INTEGER REFERENCES recipe_lab.users(userid),
    recipeid INTEGER REFERENCES recipe_lab.recipes(recipeid),
    PRIMARY KEY (userid, recipeid)
);

CREATE TABLE IF NOT EXISTS recipe_lab.userfollows (
    followeruserid INTEGER REFERENCES recipe_lab.users(userid),
    followinguserid INTEGER REFERENCES recipe_lab.users(userid),
    PRIMARY KEY (followeruserid, followinguserid),
    CHECK (followeruserid <> followinguserid)
);

CREATE TABLE IF NOT EXISTS recipe_lab.reviewlikes (
    userid INTEGER REFERENCES recipe_lab.users(userid),
    reviewid INTEGER REFERENCES recipe_lab.reviews(reviewid),
    PRIMARY KEY (userid, reviewid)
);

CREATE INDEX IF NOT EXISTS ingredient_name_lookup ON recipe_lab.ingredients (lower(ingredientname));
CREATE INDEX IF NOT EXISTS category_name_lookup ON recipe_lab.categories (lower(categoryname));
CREATE INDEX IF NOT EXISTS recipe_ingredient_lookup ON recipe_lab.recipeingredients (ingredientid, recipeid);
CREATE INDEX IF NOT EXISTS recipe_ingredient_detail ON recipe_lab.recipeingredients (recipeid, recipeingredientid);
CREATE INDEX IF NOT EXISTS recipe_category_lookup ON recipe_lab.recipecategories (categoryid, recipeid);
CREATE INDEX IF NOT EXISTS review_recipe_order ON recipe_lab.reviews (recipeid, reviewid DESC);
