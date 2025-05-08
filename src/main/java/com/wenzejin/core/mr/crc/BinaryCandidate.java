package com.wenzejin.core.mr.crc;

import com.wenzejin.core.TestCase;
import com.wenzejin.core.mr.crc.rules.TagApplication;

public class BinaryCandidate implements TestCaseCandidate {
    final FormulaWithTag left;
    final FormulaWithTag right;
    final String newOp;
    final byte taRule;
    double minDistance = 0.0;

    public BinaryCandidate(FormulaWithTag left, FormulaWithTag right, String newOp) {
        this.left = left;
        this.right = right;
        this.newOp = newOp;
        this.taRule = TagApplication.getRealRuleCode((BinaryTag) left.tag, (BinaryTag) right.tag, newOp);
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

    public boolean isUndetermined() {
        return TagApplication.isUndetermined((BinaryTag) left.tag, (BinaryTag) right.tag, newOp);
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
        return Math.min(syntaxDistance(another) + semanticDistance(another), 100);
    }

    private double syntaxDistance(TestCaseCandidate another) {
        if (another instanceof BinaryCandidate) {
            BinaryCandidate anotherBinary = (BinaryCandidate) another;
            double childDist = left.formula.distance(anotherBinary.left.formula) * 0.5
                    + right.formula.distance(anotherBinary.right.formula) * 0.5;
            return childDist;
        } else {
            return 100;
        }
    }

    /**
     * If two candidates are in the same category, the distance is 0. Else 100.
     * @param another another candidate
     * @return the distance
     */
    private double semanticDistance(TestCaseCandidate another) {
        if (another instanceof BinaryCandidate) {
            BinaryCandidate anotherBinary = (BinaryCandidate) another;
            return (newOp.equals(anotherBinary.newOp) && taRule == anotherBinary.taRule) ? 0 : 100;
        } else {
            return 100;
        }
    }

}
