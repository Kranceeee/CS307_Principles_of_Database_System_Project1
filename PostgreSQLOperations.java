package dbms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 封装所有与 PostgreSQL 数据库交互的逻辑。
 * SQL 语句已修正为：表名和列名都为全小写 (PostgreSQL 默认行为)，并在 INSERT 中加入了 NOW() 填充必填时间戳。
 */
public class PostgreSQLOperations {

    // 修正：表名和列名都使用全小写 (recipes, recipeid)
    private static final String SELECT_RECIPE_SQL =
            "SELECT * FROM recipes WHERE recipeid = ?";

    // 修正 INSERT SQL：显式使用 NOW() 填充 datesubmitted 和 datemodified
    private static final String INSERT_REVIEW_SQL =
            "INSERT INTO reviews (reviewid, recipeid, userid, rating, datesubmitted, datemodified) " +
                    "VALUES (?, ?, ?, ?, NOW(), NOW())";

    // 修正：UPDATE 语句 (reviews, reviewtext, reviewid)
    private static final String UPDATE_REVIEW_SQL =
            "UPDATE reviews SET reviewtext = ? WHERE reviewid = ?";

    // 修正：DELETE 语句 (reviews, reviewid)
    private static final String DELETE_REVIEW_SQL =
            "DELETE FROM reviews WHERE reviewid = ?";

    // =======================================================================================
    // CRUD 方法实现
    // =======================================================================================

    /**
     * 查询 (SELECT) 操作：根据 RecipeID 查询 Recipes 表
     */
    public static void queryRecipeById(String recipeId) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_RECIPE_SQL)) {

            stmt.setInt(1, Integer.parseInt(recipeId));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {} // 忽略结果处理
            }
        } catch (SQLException e) {
            System.err.println("DBMS 查询出错: " + e.getMessage());
        } catch (NumberFormatException e) {}
    }

    /**
     * 插入 (INSERT) 操作：插入一条评论到 Reviews 表
     * @param data [ReviewID, RecipeID, UserID, Rating]
     */
    public static void insertReview(String... data) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REVIEW_SQL)) {

            stmt.setInt(1, Integer.parseInt(data[0])); // ReviewID
            stmt.setInt(2, Integer.parseInt(data[1])); // RecipeID (外键)
            stmt.setInt(3, Integer.parseInt(data[2])); // UserID
            stmt.setInt(4, Integer.parseInt(data[3])); // Rating

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DBMS 插入出错: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("DBMS 插入出错: 数据格式错误 - " + e.getMessage());
        }
    }

    /**
     * 更新 (UPDATE) 操作：根据 ReviewID 更新评论内容
     */
    public static void updateReviewText(String reviewId, String newReviewText) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_REVIEW_SQL)) {

            stmt.setString(1, newReviewText);
            stmt.setInt(2, Integer.parseInt(reviewId));

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DBMS 更新出错: " + e.getMessage());
        } catch (NumberFormatException e) {}
    }

    /**
     * 删除 (DELETE) 操作：根据 ReviewID 删除一条评论
     */
    public static void deleteReview(String reviewId) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_REVIEW_SQL)) {

            stmt.setInt(1, Integer.parseInt(reviewId));

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DBMS 删除出错: " + e.getMessage());
        } catch (NumberFormatException e) {}
    }
}