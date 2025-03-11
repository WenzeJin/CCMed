package com.wenzejin.core.mr;

import java.sql.SQLException;
import java.util.List;

public interface MRCore {
    void generateTestCases(List<Integer> ids, String newBatchName) throws SQLException;
}

