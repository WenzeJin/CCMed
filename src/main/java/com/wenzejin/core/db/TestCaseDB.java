package com.wenzejin.core.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wenzejin.core.mr.assertion.TestAssertion;
import com.wenzejin.core.mr.assertion.TestAssertionTypeAdapter;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.FormulaTypeAdapter;
import com.wenzejin.core.util.GzipCompress;
import com.wenzejin.core.util.TestCase;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Database driver for test cases.
 * Notice: checked and passed fields in TestCase will not be used. they will be substituted by fields in DB.
 */
public class TestCaseDB {
    private static String DATABASE_URL = "./data/testcase.db";  // 设置数据库路径为 ./data/testcase.db
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Formula.class, new FormulaTypeAdapter())
            .registerTypeAdapter(TestAssertion.class, new TestAssertionTypeAdapter())
            .create();

    public static void setDatabaseUrl(String url) {
        DATABASE_URL = url;
    }

    // 创建数据库和表
    public static void initializeDB() throws SQLException {
        // 确保 ./data 目录存在
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdir();  // 如果目录不存在，则创建
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             Statement stmt = conn.createStatement()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS testcases ("
                    + "id INTEGER PRIMARY KEY, "
                    + "compressed_data BLOB,"
                    + "batch_name TEXT DEFAULT \"default\"," // 添加 batch_name 字段
                    + "tu_rule TEXT DEFAULT NULL,"
                    + "ta_rule TEXT DEFAULT NULL,"
                    + "checked BOOLEAN DEFAULT FALSE,"
                    + "passed BOOLEAN DEFAULT FALSE,"
                    + "parent_id STRING DEFAULT \"\")";
            stmt.execute(createTableSQL);
        }
    }

    // 清除数据库
    public static void clearDB() throws SQLException {
        // 确保 ./data 目录存在
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdir();  // 如果目录不存在，则创建
        }

        // 删除 database 文件
        File databaseFile = new File(DATABASE_URL);
        if (databaseFile.exists()) {
            databaseFile.delete();
        }
    }

    // 将 TestCase 存储到数据库中
    public static void storeTestCase(TestCase testCase) throws SQLException, IOException {
        storeTestCase(testCase, null);
    }

    // 将 TestCase 存储到数据库中，完整的方法
    public static void storeTestCase(TestCase testCase, String batchName) throws SQLException, IOException {
        int id = testCase.getId();
        String json = gson.toJson(testCase);
        byte[] compressedData = GzipCompress.compressStringByte(json);

        String batch = batchName == null ? "default" : batchName;
        List<String> rules = testCase.getRuleUsed();
        String tu_rule = null, ta_rule = null;
        for (String rule: rules) {
            if (rule.startsWith("TU")) {
                tu_rule = rule;
            } else if (rule.startsWith("TA")) {
                ta_rule = rule;
            }
        }
        var parents = testCase.getSource();
        String parent_id = "";
        for(int i = 0; i < parents.size(); i++) {
            parent_id += parents.get(i);
            if (i < parents.size() - 1) {
                parent_id += ",";
            }
        }

        String insertSQL = "INSERT OR REPLACE INTO testcases (id, compressed_data, batch_name, tu_rule, ta_rule, checked, passed, parent_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
                PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                stmt.setInt(1, id);
                stmt.setBytes(2, compressedData);
                stmt.setString(3, batchName);
                stmt.setString(4, tu_rule);
                stmt.setString(5, ta_rule);
                stmt.setBoolean(6, false);
                stmt.setBoolean(7, false);
                stmt.setString(8, parent_id);
                stmt.execute();
            }
    }

    public static void storeTestCases(List<TestCase> testCases, String batchName) throws SQLException, IOException {
        String batch = batchName == null ? "default" : batchName;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL)) {
            conn.setAutoCommit(false);
            String insertSQL = "INSERT OR REPLACE INTO testcases (id, compressed_data, batch_name, tu_rule, ta_rule, checked, passed, parent_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
                int count = 0;
                for (TestCase testCase : testCases) {
                    List<String> rules = testCase.getRuleUsed();
                    String tu_rule = null, ta_rule = null;
                    for (String rule: rules) {
                        if (rule.startsWith("TU")) {
                            tu_rule = rule;
                        } else if (rule.startsWith("TA")) {
                            ta_rule = rule;
                        }
                    }

                    var parents = testCase.getSource();
                    String parent_id = "";
                    for(int i = 0; i < parents.size(); i++) {
                        parent_id += parents.get(i);
                        if (i < parents.size() - 1) {
                            parent_id += ",";
                        }
                    }

                    int id = testCase.getId();
                    String json = gson.toJson(testCase);  // 将 TestCase 转为 JSON 字符串

                    // 压缩 JSON 字符串
                    byte[] compressedData = GzipCompress.compressStringByte(json);

                    stmt.setInt(1, id);
                    stmt.setBytes(2, compressedData);
                    stmt.setString(3, batch);
                    stmt.setString(4, tu_rule);
                    stmt.setString(5, ta_rule);
                    stmt.setBoolean(6, false);
                    stmt.setBoolean(7, false);
                    stmt.setString(8, parent_id);
                    stmt.addBatch();
                    count++;
                    if (count % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            conn.commit();
        }

    }

    public static void storeTestCases(List<TestCase> testCases) throws SQLException, IOException {
        storeTestCases(testCases, null);
    }

    // 按 ID 提取 TestCase
    public static TestCase getTestCaseById(int id) throws SQLException, IOException {
        String selectSQL = "SELECT compressed_data FROM testcases WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                byte[] compressedData = rs.getBytes("compressed_data");
                String json = GzipCompress.decompressString(compressedData);  // 解压并获得 JSON 字符串
                return gson.fromJson(json, TestCase.class);  // 将 JSON 转回 TestCase 对象
            }
        }
        return null;  // 如果没有找到，则返回 null
    }

    public static String getBatchNameById(int id) throws SQLException {
        String selectSQL = "SELECT batch_name FROM testcases WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("batch_name");
            }
        }
        return null;
    }

    public static int getMaxId() throws SQLException {
        String selectSQL = "SELECT MAX(id) FROM testcases";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public static List<Integer> getBatchIds(String batchName) throws SQLException {
        String selectSQL = "SELECT id FROM testcases WHERE batch_name = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            stmt.setString(1, batchName);
            ResultSet rs = stmt.executeQuery();
            List<Integer> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            return ids;
        }
    }
    
    public static void randomSubsetWithCond(int size, String parentBatch, String newBatchName, String sqlCondition) {
        if (sqlCondition != null && !sqlCondition.isEmpty()) {
            sqlCondition = "(" + sqlCondition + ")";
        } else {
            sqlCondition = "1 = 1";
        }

        String updateSQL = "UPDATE testcases SET batch_name = ? WHERE id IN " +
                "(SELECT id FROM testcases WHERE batch_name = ? AND " + sqlCondition + " ORDER BY RANDOM() LIMIT ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
            PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setString(1, newBatchName);
            stmt.setString(2, parentBatch);
            stmt.setInt(3, size);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    /**
     * Randomly select a subset of test cases from the parent batch and store them in a new batch.
     * @param size size of the subset
     * @param parentBatch parent batch name
     * @param newBatchName new batch name
     */
    public static void randomSubset(int size, String parentBatch, String newBatchName) {
        randomSubsetWithCond(size, parentBatch, newBatchName, null);
    }

    public static void setChecked(int id, boolean checked) throws SQLException {
        String updateSQL = "UPDATE testcases SET checked = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setBoolean(1, checked);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
    }

    public static void setPassed(int id, boolean passed) throws SQLException {
        String updateSQL = "UPDATE testcases SET passed = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setBoolean(1, passed);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
    }

    public static void updateCompressedData(int id, TestCase testCase) throws SQLException, IOException{
        String json = gson.toJson(testCase);
        byte[] compressedData = GzipCompress.compressStringByte(json);

        String updateSQL = "UPDATE testcases SET compressed_data = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setBytes(1, compressedData);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
    }

    public static int getSizeByCondition(String sqlCondition) {
        if (sqlCondition != null && !sqlCondition.isEmpty()) {
            sqlCondition = "(" + sqlCondition + ")";
        } else {
            sqlCondition = "1 = 1";
        }

        String selectSQL = "SELECT COUNT(*) FROM testcases WHERE " + sqlCondition;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(selectSQL)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static List<String> splitAndUpdateBatchNames(String originalBatchName, int maxSizePerBatch) throws SQLException {
        List<String> newBatchNames = new ArrayList<>();

        // 获取当前批次的数据
        List<Integer> dataIds = getBatchIds(originalBatchName);
        Collections.shuffle(dataIds);

        int dataSize = dataIds.size();

        // 如果数据量小于等于 maxSizePerBatch，则不需要划分
        if (dataSize <= maxSizePerBatch) {
            newBatchNames.add(originalBatchName);
            return newBatchNames;
        }

        // 计算需要的批次数
        int fullBatches = dataSize / maxSizePerBatch;
        int remaining = dataSize % maxSizePerBatch;

        // 先创建每个完整的批次，并更新数据库
        for (int i = 0; i < fullBatches; i++) {
            String newBatchName = originalBatchName + "_part_" + (i + 1);
            newBatchNames.add(newBatchName);

            // 更新数据库中数据项的 batch_name
            updateBatchNameInDatabase(dataIds.subList(i * maxSizePerBatch, (i + 1) * maxSizePerBatch), newBatchName);
        }

        // 如果有剩余的数据，进行随机分配
        if (remaining > 0) {
            Random random = new Random();
            for (int i = 0; i < remaining; i++) {
                // 随机选择一个批次进行分配
                int randomIndex = random.nextInt(fullBatches);
                String randomBatchName = originalBatchName + "_part_" + (randomIndex + 1);

                // 更新数据库中该条数据的 batch_name
                updateBatchNameInDatabase(Collections.singletonList(dataIds.get(fullBatches * maxSizePerBatch + i)), randomBatchName);
            }
        }

        return newBatchNames;
    }

    public static void updateBatchNameInDatabase(List<Integer> dataIds, String newBatchName) throws SQLException {
        String updateSQL = "UPDATE testcases SET batch_name = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            for (Integer id : dataIds) {
                stmt.setString(1, newBatchName);
                stmt.setInt(2, id);
                stmt.executeUpdate();
            }
        }
    }

    public static void updateBatchNameFromOld(String oldName, String newName) throws SQLException {
        String updateSQL = "UPDATE testcases SET batch_name = ? WHERE batch_name = ? ";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
             PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
            stmt.setString(1, newName);
            stmt.setString(2, oldName);
            stmt.executeUpdate();
        }
    }



}
