package com.wenzejin.core.mr.crc;

import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.util.TestCase;

public class BinaryCandidate implements TestCaseCandidate {
    final FormulaWithTag left;
    final FormulaWithTag right;
    final String newOp;

    public BinaryCandidate(FormulaWithTag left, FormulaWithTag right, String newOp) {
        this.left = left;
        this.right = right;
        this.newOp = newOp;
    }

    public FormulaWithTag getLeft() {
        return left;
    }

    public FormulaWithTag getRight() {
        return right;
    }

    public String getNewOp() {
        return newOp;
    }

    @Override
    public TestCase genTestCase() {
        return TagApplication.binaryEntry(left, right, newOp);
    }

}
