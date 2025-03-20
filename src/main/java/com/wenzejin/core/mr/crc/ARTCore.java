package com.wenzejin.core.mr.crc;

import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ARTCore {
    private static final Logger logger = LogManager.getLogger("ARTCore");

    final List<TestCaseCandidate> candidates;
    final List<TestCaseCandidate> tested;

    public ARTCore() {
        candidates = new ArrayList<>();
        tested = new ArrayList<>();
    }

    public void initCandidates(List<TestCase> tcs) {
        List<FormulaWithTag> binaryPool = new ArrayList<>();
        List<FormulaWithTag> quantifierPool = new ArrayList<>();
        String[] binaryOps = {"and", "or", "implies"};
        String[] quantifierOps = {"forall", "exists"};

        for (TestCase tc : tcs) {
            Formula formula = tc.getRule().formula;
            if (formula instanceof OpConnector) {
                // binary formula
                Formula leftFormula = ((OpConnector) formula).left;
                Formula rightFormula = ((OpConnector) formula).right;
                List<Tag> tags;
                try {
                    tags = TagCreation.entry(tc);
                    FormulaWithTag left = new FormulaWithTag(leftFormula, tags.get(0), tc.getPatterns(), tc.getBfuncs(), tc);
                    FormulaWithTag right = new FormulaWithTag(rightFormula, tags.get(1), tc.getPatterns(), tc.getBfuncs(), tc);
                    binaryPool.add(left);
                    binaryPool.add(right);
                } catch (NoTestResultException e) {
                    logger.warn("No test result for test case: {}, ignore!", tc);
                }

            } else if (formula instanceof OpQuantifier) {
                // quantifier formula
                List<Tag> tags;
                Formula child = ((OpQuantifier) formula).formula;
                try {
                    tags = TagCreation.entry(tc);
                    FormulaWithTag quantifier = new FormulaWithTag(child, tags.getFirst(), tc.getPatterns(), tc.getBfuncs(), tc);
                    quantifierPool.add(quantifier);
                } catch (NoTestResultException e) {
                    logger.warn("No test result for test case: {}, ignore!", tc);
                }
            }
        }

        for (FormulaWithTag left : binaryPool) {
            for (FormulaWithTag right : binaryPool) {
                if (left == right) {
                    continue;
                }
                BinaryTag lt = (BinaryTag) left.tag;
                BinaryTag rt = (BinaryTag) right.tag;
                if (lt.truth == BinaryTag.Truth.UNKNOWN && rt.truth == BinaryTag.Truth.UNKNOWN) {
                    continue;
                }
                for (String op : binaryOps) {
                    candidates.add(new BinaryCandidate(left, right, op));
                }
            }
        }

        for (FormulaWithTag left : quantifierPool) {
            for (FormulaWithTag right : quantifierPool) {
                if (left == right) {
                    continue;
                }
                for (String op : binaryOps) {
                    for (String quantifierOp : quantifierOps) {
                        candidates.add(new QuantifierCandidate(left, right, op, quantifierOp, true));
                        candidates.add(new QuantifierCandidate(left, right, op, quantifierOp, false));
                    }
                }
            }

        }
    }

    public void shuffleCandidates() {
        Collections.shuffle(candidates);
    }

    public List<TestCase> getNextBatch(int size) {
        List<TestCase> thisBatch = new ArrayList<>();
        List<TestCaseCandidate> selected = new ArrayList<>();
        int i = 0;
        while (thisBatch.size() < size) {
            if (i > candidates.size() - 1) {
                break;
            }
            selected.add(candidates.get(i));
            TestCase tc = candidates.get(i).genTestCase();
            if (tc != null) {
                thisBatch.add(tc);
                tested.add(candidates.get(i));
            }
            i++;
        }
        candidates.removeAll(selected);

        // TODO: refresh candidates

        return thisBatch;
    }
}
