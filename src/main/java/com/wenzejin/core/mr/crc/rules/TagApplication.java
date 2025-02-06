package com.wenzejin.core.mr.crc.rules;

import com.wenzejin.core.mr.TestAssertion;
import com.wenzejin.core.mr.assertion.LinksSubsetAssertion;
import com.wenzejin.core.mr.assertion.TruthAssertion;
import com.wenzejin.core.mr.crc.BinaryTag;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.mr.crc.FormulaWithTag;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.util.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TagApplication {

    public static TestCase binaryEntry(FormulaWithTag left, FormulaWithTag right) {
        return new TestCase();
    }


    private static List<TestAssertion> and(BinaryTag left, BinaryTag right) {
        List<TestAssertion> assertions = new ArrayList<>();
        if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-and-1
            assertions.add(new TruthAssertion(true));
            Set<Link> cartesian = CRCFunc.cartesian(left.links, right.links);
            if (!cartesian.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(cartesian));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-and-2-a
            assertions.add(new TruthAssertion(false));
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-and-2-b
            assertions.add(new TruthAssertion(false));
            Set<Link> union = CRCFunc.union(left.links, right.links);
            if (!union.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(union));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.FALSE) {
            assertions.add(new TruthAssertion(false));
            Set<Link> union = CRCFunc.union(left.links, right.links);
            if (!union.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(union));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.UNKNOWN) {

            TestAssertion pre = new TruthAssertion(true);
            TestAssertion after = new LinksSubsetAssertion()
        }

    }

}
