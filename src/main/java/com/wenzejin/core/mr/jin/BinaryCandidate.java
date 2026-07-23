package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.jin.rules.TestCaseSetup;
import com.wenzejin.core.mr.jin.rules.PUComposition;

public class BinaryCandidate implements TestCaseCandidate {
    final PrimitiveUnit left;
    final PrimitiveUnit right;
    final String newOp;
    final byte taRule;
    double minDistance;

    public BinaryCandidate(PrimitiveUnit left, PrimitiveUnit right, String newOp) {
        this.left = left;
        this.right = right;
        this.newOp = newOp;
        this.taRule = TestCaseSetup.getRealRuleCode((ClosedTag) left.tag, (ClosedTag) right.tag, newOp);
        // generate a random double above 1000
        minDistance = 100;
    }

    public PrimitiveUnit getLeft() {
        return left;
    }

    public PrimitiveUnit getRight() {
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
        PrimitiveUnit l = left;
        if (Math.random() > 0.75) {
            l = PUComposition.negation(l);
        }
        PrimitiveUnit r = right;
        if (Math.random() > 0.75) {
            r = PUComposition.negation(r);
        }
        var gen = PUComposition.unifyForBin(l, r, newOp);
        if (gen != null) {
            return TestCaseSetup.binaryFinalize(gen);
        }
        return null;
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
        if (another instanceof BinaryCandidate anotherBinary) {
            return left.formula.distance(anotherBinary.left.formula) * 0.5
                    + right.formula.distance(anotherBinary.right.formula) * 0.5;
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
        if (another instanceof BinaryCandidate anotherBinary) {
            return (newOp.equals(anotherBinary.newOp) && taRule == anotherBinary.taRule) ? 0 : 100;
        } else {
            return 100;
        }
    }

}
