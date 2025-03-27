package com.wenzejin.core.mr.crc;

import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.util.TestCase;

public class BinaryCandidate implements TestCaseCandidate {
    final FormulaWithTag left;
    final FormulaWithTag right;
    final String newOp;
    double minDistance = 0.0;

    public BinaryCandidate(FormulaWithTag left, FormulaWithTag right, String newOp) {
        this.left = left;
        this.right = right;
        this.newOp = newOp;
        // generate a random double above 1000
        minDistance = Math.random() * 1000 + 1000;
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

    public double getMinDistance() {
        return minDistance;
    }

    @Override
    public TestCase genTestCase() {
        return TagApplication.binaryEntry(left, right, newOp);
    }

    @Override
    public int compareTo(TestCaseCandidate o) {
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
