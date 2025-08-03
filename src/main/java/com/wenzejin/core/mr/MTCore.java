package com.wenzejin.core.mr;

import com.wenzejin.core.TestCase;
import java.util.List;

public interface MTCore {
    void initCandidates(List<TestCase> tcs);

    List<TestCase> getNextBatch(int size);

    boolean hasMore();

    void logStats();
}
