package com.wenzejin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.assertion.TestAssertionTypeAdapter;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.FormulaTypeAdapter;

import java.io.*;
import java.sql.*;



/**
 * Database driver for test cases.
 * Notice: checked and passed fields in TestCase will not be used. They will be substituted by fields in DB.
 */
public class TestCaseDataBaseDriver {
    private static String DATABASE_URL = "./data/testcase.db";  // 设置数据库路径为 ./data/testcase.db
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Formula.class, new FormulaTypeAdapter())
            .registerTypeAdapter(TestAssertion.class, new TestAssertionTypeAdapter())
            .create();

    public static void setDatabaseUrl(String url) {
        DATABASE_URL = url;
    }

    /**
     * Initializes the database by creating the testcases table if it does not exist.
     * @throws SQLException If a database access error occurs or the table cannot be created.
     */
    public static void initializeDB() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             Statement stmt = conn.createStatement()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS testcases ("
                    + "id INTEGER PRIMARY KEY, "
                    + "compressed_data TEXT,"
                    + "batch_name TEXT DEFAULT \"default\"," // 添加 batch_name 字段
                    + "tu_rule TEXT DEFAULT NULL,"
                    + "ta_rule TEXT DEFAULT NULL)";
            stmt.execute(createTableSQL);
        }
    }

    /**
     * Clears the database by deleting the database file.
     * @throws SQLException If the database file cannot be deleted.
     */
    public static void clearDB() throws SQLException {
        File databaseFile = new File(DATABASE_URL);
        if (databaseFile.exists()) {
            if (!databaseFile.delete()) {
                throw new SQLException("Failed to delete the database file: " + DATABASE_URL);
            }
        }
    }

    /**
     * Stores a collection of test cases into the database. Each test case is serialized
     * into a JSON string and stored along with its associated metadata.
     *
     * @param testCases  An iterable collection of {@link TestCase} objects to be stored.
     *                   Null test cases in the collection are ignored.
     * @param batchName  The name of the batch to associate with the test cases. If null
     *                   or blank, the default batch name "default" is used.
     * @throws SQLException If a database access error occurs or the operation fails.
     *
     * <p>Each test case is stored with the following fields:
     * <ul>
     *   <li><b>id</b>: The unique identifier of the test case.</li>
     *   <li><b>compressed_data</b>: The JSON representation of the test case.</li>
     *   <li><b>batch_name</b>: The name of the batch the test case belongs to.</li>
     *   <li><b>tu_rule</b>: The first rule used by the test case that starts with "TU".</li>
     *   <li><b>ta_rule</b>: The first rule used by the test case that starts with "TA".</li>
     * </ul>
     *
     * <p>The method uses batch processing to improve performance, committing every 100
     * test cases. If an error occurs during the operation, the transaction is rolled back.
     */
    public static void storeTestCases(Iterable<TestCase> testCases, String batchName) throws SQLException {
        String batch = (batchName == null || batchName.isBlank()) ? "default" : batchName.trim();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL)) {
            conn.setAutoCommit(false);
            String insertSQL = "INSERT OR REPLACE INTO testcases (id, compressed_data, batch_name, tu_rule, ta_rule) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                int count = 0;
                for (TestCase testCase : testCases) {
                    if (testCase == null) continue;

                    String tu_rule = null, ta_rule = null;
                    for (String rule : testCase.getRuleUsed()) {
                        if (rule.startsWith("TU")) {
                            tu_rule = rule;
                        } else if (rule.startsWith("TA")) {
                            ta_rule = rule;
                        }
                    }

                    int id = testCase.getId();
                    String json = gson.toJson(testCase); // Convert TestCase to JSON string

                    stmt.setInt(1, id);
                    stmt.setString(2, json);
                    stmt.setString(3, batch);
                    stmt.setString(4, tu_rule);
                    stmt.setString(5, ta_rule);
                    stmt.addBatch();
                    count++;

                    if (count % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
                if (count > 0) {
                    stmt.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            throw new SQLException("Failed to store test cases: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a TestCase object from the database by its ID.
     *
     * @param id The unique identifier of the test case to retrieve.
     * @return The TestCase object corresponding to the given ID, or {@code null} if no such test case is found.
     * @throws SQLException If a database access error occurs.
     * @throws IOException If an I/O error occurs during JSON deserialization.
     */
    public static TestCase getTestCaseById(int id) throws SQLException, IOException {
        String selectSQL = "SELECT compressed_data FROM testcases WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String json = rs.getString("compressed_data");
                return gson.fromJson(json, TestCase.class);  // 将 JSON 转回 TestCase 对象
            }
        }
        return null;  // 如果没有找到，则返回 null
    }

}
