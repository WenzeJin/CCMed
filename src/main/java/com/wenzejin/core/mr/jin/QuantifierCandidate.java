package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import com.wenzejin.core.mr.jin.rules.TagUnification;

public class QuantifierCandidate implements TestCaseCandidate {
    final PrimitiveUnit left;
    final PrimitiveUnit right;
    final String newConn;
    final String newQuant;
    final boolean expectTruth;

    double minDistance;

    public QuantifierCandidate(PrimitiveUnit left, PrimitiveUnit right, String newConn, String newQuant, boolean expectTruth) {
        this.left = left;
        this.right = right;
        this.newConn = newConn;
        this.newQuant = newQuant;
        this.expectTruth = expectTruth;
        minDistance = 100;
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
        PrimitiveUnit unified = TagUnification.unifyForQuant(left, right, newConn);
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
        return Math.min(syntaxDistance(another) + semanticDistance(another), 100);
    }

    /**
     * Calculates the syntax distance between this QuantifierCandidate and another TestCaseCandidate.
     * The distance is determined based on the structural and syntactic differences between the two candidates.
     * 
     * <p>If the other candidate is not an instance of QuantifierCandidate, a default distance of 100 is returned.
     * Otherwise, the distance is computed as follows:
     * <ul>
     *   <li>The child distance is calculated as the weighted average of the distances between the left and right formulas.</li>
     *   <li>The connector distance is determined based on whether the connectors are equal, with a penalty applied if they differ.</li>
     *   <li>The quantifier distance is determined based on whether the quantifiers are equal, with a penalty applied if they differ.</li>
     * </ul>
     * 
     * @param another The other TestCaseCandidate to compare against.
     * @return The calculated syntax distance. A lower value indicates greater similarity, while a higher value indicates greater difference.
     */
    private double syntaxDistance(TestCaseCandidate another) {
        if (another instanceof QuantifierCandidate anotherQuant) {
            return left.formula.distance(anotherQuant.left.formula) * 0.5
                    + right.formula.distance(anotherQuant.right.formula) * 0.5;
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
        if (another instanceof QuantifierCandidate anotherQuant) {
            boolean qEqual = newQuant.equals(anotherQuant.newQuant);
            boolean eEqual = expectTruth == anotherQuant.expectTruth;
            boolean cEqual = newConn.equals(anotherQuant.newConn);
            return (qEqual && eEqual) ? (cEqual ? 0 : 50) : 100;
        } else {
            return 100;
        }
    }
}
