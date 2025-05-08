package com.wenzejin.core.mr;

import com.wenzejin.core.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.crc.*;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface MTCore {
    void initCandidates(List<TestCase> tcs);

    List<TestCase> getNextBatch(int size);

    boolean hasMore();

    void logStats();
}
