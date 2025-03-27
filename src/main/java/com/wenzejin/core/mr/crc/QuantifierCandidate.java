package com.wenzejin.core.mr.crc;

import com.wenzejin.core.TestCase;
import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.mr.crc.rules.TagUnification;

public class QuantifierCandidate implements TestCaseCandidate {
    final FormulaWithTag left;
    final FormulaWithTag right;
    final String newConn;
    final String newQuant;
    final boolean expectTruth;

    double minDistance = 0.0;

    public QuantifierCandidate(FormulaWithTag left, FormulaWithTag right, String newConn, String newQuant, boolean expectTruth) {
        this.left = left;
        this.right = right;
        this.newConn = newConn;
        this.newQuant = newQuant;
        this.expectTruth = expectTruth;
        minDistance = Math.random() * 1000 + 1000;
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

    public double getMinDistance() {
        return minDistance;
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

    @Override
    public int compareTo(TestCaseCandidate o) {
        // reverse order
        return Double.compare(getMinDistance(), o.getMinDistance());
    }

    @Override
    public void updateMinDistance(TestCaseCandidate another) {
        double dist = distance(another);
        if (dist < minDistance) {
            minDistance = dist;
        }
    }

    private double distance(TestCaseCandidate another) {
        return Math.random() * 1000;
    }
}
