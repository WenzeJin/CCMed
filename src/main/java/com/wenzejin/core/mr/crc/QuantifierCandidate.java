package com.wenzejin.core.mr.crc;

import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.mr.crc.rules.TagUnification;
import com.wenzejin.core.util.TestCase;

public class QuantifierCandidate implements TestCaseCandidate {
    final FormulaWithTag left;
    final FormulaWithTag right;
    final String newConn;
    final String newQuant;
    final boolean expectTruth;

    public QuantifierCandidate(FormulaWithTag left, FormulaWithTag right, String newConn, String newQuant, boolean expectTruth) {
        this.left = left;
        this.right = right;
        this.newConn = newConn;
        this.newQuant = newQuant;
        this.expectTruth = expectTruth;
    }

    public FormulaWithTag getLeft() {
        return left;
    }

    public FormulaWithTag getRight() {
        return right;
    }

    public String getNewConn() {
        return newConn;
    }

    public String getNewQuant() {
        return newQuant;
    }

    public boolean getExpectTruth() {
        return expectTruth;
    }

    @Override
    public TestCase genTestCase() {
        FormulaWithTag unified = TagUnification.unify(left, right, newConn);
        if (unified != null) {
            return TagApplication.quantifierEntry(unified, newQuant, expectTruth);
        } else {
            return null;
        }
    }
}
