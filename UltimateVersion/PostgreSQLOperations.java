package dbms; 

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PostgreSQLOperations {

    private static final String SELECT_RECIPE_SQL =
            "SELECT * FROM recipes WHERE recipeid = ?";

    private static final String INSERT_REVIEW_SQL =
            "INSERT INTO reviews (reviewid, recipeid, userid, rating, datesubmitted, datemodified) " +
                    "VALUES (?, ?, ?, ?, NOW(), NOW())";

    private static final String UPDATE_REVIEW_SQL =
            "UPDATE reviews SET reviewtext = ? WHERE reviewid = ?";

    private static final String DELETE_REVIEW_SQL =
            "DELETE FROM reviews WHERE reviewid = ?";


    public static void queryRecipeById(String recipeId) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_RECIPE_SQL)) {

            stmt.setInt(1, Integer.parseInt(recipeId));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {} 
            }
        } catch (SQLException e) {
            System.err.println("DBMS 查询出错: " + e.getMessage());
        } catch (NumberFormatException e) {}
    }

    public static void insertReview(String... data) {
        try (Connection conn = PostgreSQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_REVIEW_SQL)) {

            stmt.setInt(1, Integer.parseInt(data[0])); 
            stmt.setInt(2, Integer.parseInt(data[1])); 
            stmt.setInt(3, Integer.parseInt(data[2])); 
            stmt.setInt(4, Integer.parseInt(data[3])); 

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DBMS 插入出错: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("DBMS 插入出错: 数据格式错误 - " + e.getMessage());
        }
    }

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
