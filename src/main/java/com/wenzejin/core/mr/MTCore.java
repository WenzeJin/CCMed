package com.wenzejin.core.mr;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.TestOutcome;
import java.util.List;

public interface MTCore {
    void initCandidates(List<TestOutcome> outcomes);

    List<TestCase> getNextBatch(int size);

    boolean hasMore();

    void logStats();
}
