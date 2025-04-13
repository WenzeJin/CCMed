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
        return syntaxDistance(another) * 0.5 + semanticDistance(another) * 0.5;
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
        if (another instanceof QuantifierCandidate) {
            QuantifierCandidate anotherQuant = (QuantifierCandidate) another;
            double childDist = left.formula.distance(anotherQuant.left.formula) * 0.5
                    + right.formula.distance(anotherQuant.right.formula) * 0.5;
            return childDist;
        } else {
            return 100;
        }
    }

    /**
     * Calculates the semantic distance between this QuantifierCandidate and another TestCaseCandidate.
     * The semantic distance is determined based on the equality of specific attributes:
     * - `newQuant` (quantifier)
     * - `newConn` (connector)
     * - `expectTruth` (expected truth value)
     * 
     * If the other TestCaseCandidate is not an instance of QuantifierCandidate, a default distance of 100 is returned.
     * Otherwise, the distance is calculated as follows:
     * - 0.0 if the attributes are equal
     * - 20.0 for each attribute that is not equal
     * 
     * @param another The TestCaseCandidate to compare against.
     * @return The semantic distance as a double value. A lower value indicates higher similarity.
     */
    private double semanticDistance(TestCaseCandidate another) {
        if (another instanceof QuantifierCandidate) {
            QuantifierCandidate anotherQuant = (QuantifierCandidate) another;
            boolean qEqual = newQuant.equals(anotherQuant.newQuant);
            boolean cEqual = newConn.equals(anotherQuant.newConn);
            boolean eEqual = expectTruth == anotherQuant.expectTruth;
            return (qEqual ? 0.0 : 50.0) + (cEqual ? 0.0 : 25.0) + (eEqual ? 0.0 : 25.0);
        } else {
            return 100;
        }
    }
}
