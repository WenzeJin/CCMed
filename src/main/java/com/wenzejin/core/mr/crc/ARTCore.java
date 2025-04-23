package com.wenzejin.core.mr.crc;

import com.wenzejin.core.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ARTCore {
    private static final Logger logger = LogManager.getLogger("ARTCore");

    List<TestCaseCandidate> candidates;
    List<TestCaseCandidate> tested;

    final int threadNum;

    public ARTCore(int threadNum) {
        candidates = new ArrayList<>();
        tested = new ArrayList<>();
        this.threadNum = threadNum;
    }

    public void initCandidates(List<TestCase> tcs) {
        List<FormulaWithTag> binaryPool = new ArrayList<>();
        List<FormulaWithTag> quantifierPool = new ArrayList<>();
        String[] binaryOps = {"and", "or", "implies"};
        String[] quantifierOps = {"forall", "exists"};

        for (TestCase tc : tcs) {
            Formula formula = tc.getRule().formula;
            try {
                BinaryTag wholeTag = new BinaryTag(tc.getResult().truth ? BinaryTag.Truth.TRUE: BinaryTag.Truth.FALSE, tc.getResult().links);
                FormulaWithTag whole = new FormulaWithTag(formula, wholeTag, tc.getPatterns(), tc.getBfuncs(), tc);
                whole.oriRule = tc.getOriRule();
                binaryPool.add(whole);
                List<Tag> tags = TagCreation.entry(tc);
                if (formula instanceof OpConnector) {
                    // binary formula
                    Formula leftFormula = ((OpConnector) formula).left;
                    Formula rightFormula = ((OpConnector) formula).right;
                    FormulaWithTag left = new FormulaWithTag(leftFormula, tags.get(0), tc.getPatterns(), tc.getBfuncs(), tc);
                    FormulaWithTag right = new FormulaWithTag(rightFormula, tags.get(1), tc.getPatterns(), tc.getBfuncs(), tc);
                    left.oriRule = tc.getOriRule();
                    right.oriRule = tc.getOriRule();
                    binaryPool.add(left);
                    binaryPool.add(right);
                } else if (formula instanceof OpQuantifier) {
                    // quantifier formula
                    Formula child = ((OpQuantifier) formula).formula;
                    FormulaWithTag quantifier = new FormulaWithTag(child, tags.get(0), tc.getPatterns(), tc.getBfuncs(), tc);
                    quantifier.oriRule = tc.getOriRule();
                    quantifierPool.add(quantifier);
                }
            } catch (NoTestResultException e) {
                logger.warn("No test result for test case: {}, ignoring!", tc);
            } catch (NoRuleException e) {
                logger.info("We cannot use rule {} to generate complex test cases!", tc.getRule().getId());
            }
        }

        generateBinaryCandidates(binaryPool, binaryOps);
        generateQuantifierCandidates(quantifierPool, binaryOps, quantifierOps);
        logger.info("Total {} candidates generated.", candidates.size());
    }

    private void generateBinaryCandidates(List<FormulaWithTag> binaryPool, String[] binaryOps) {
        for (FormulaWithTag left : binaryPool) {
            for (FormulaWithTag right : binaryPool) {
                if (left == right || left.oriRule.equals(right.oriRule)) {
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
    }

    private void generateQuantifierCandidates(List<FormulaWithTag> quantifierPool, String[] binaryOps, String[] quantifierOps) {
        for (FormulaWithTag left : quantifierPool) {
            for (FormulaWithTag right : quantifierPool) {
                if (left == right || left.oriRule.equals(right.oriRule)) {
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

    public List<TestCase> getNextBatch(int size) {
        List<TestCase> thisBatch = new ArrayList<>();
        List<TestCaseCandidate> selected = new ArrayList<>();
        List<TestCaseCandidate> used = new ArrayList<>();
        candidates.sort(Collections.reverseOrder());

        int i = 0;
        while (thisBatch.size() < size) {
            if (i > candidates.size() - 1) {
                break;
            }
            selected.add(candidates.get(i));
            TestCase tc = candidates.get(i).genTestCase();
            if (tc != null) {
                thisBatch.add(tc);
                used.add(candidates.get(i));
                tested.add(candidates.get(i));
            }
            i++;
        }
        candidates.removeAll(selected);
        logger.info("Mini batch generated. Dist Max: {}, Min: {}", used.get(0).getMinDistance(),
                used.get(used.size() - 1).getMinDistance());
        int binaryCount = 0;
        for (TestCaseCandidate candidate : used) {
            if (candidate instanceof BinaryCandidate) {
                binaryCount++;
            }
        }
        logger.info("Binary cases: {}, Quantifier cases: {}", binaryCount, used.size() - binaryCount);

        parallelUpdateMinDistance(used);
        return thisBatch;
    }

    public boolean hasMore() {
        return !candidates.isEmpty();
    }


    private void parallelUpdateMinDistance(List<TestCaseCandidate> selected) {
        candidates.parallelStream().forEach(candidate -> selected.forEach(candidate::updateMinDistance));
    }
}
