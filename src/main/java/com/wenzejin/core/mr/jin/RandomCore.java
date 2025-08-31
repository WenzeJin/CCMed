package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.jin.rules.TagCreation;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class RandomCore implements MTCore {
    private static final Logger logger = LogManager.getLogger("RandomCore");
    private static final double binaryRate = 0.8;


    List<TestCaseCandidate> candidates;
    List<TestCaseCandidate> tested;

    final int threadNum;
    final boolean enableUndetermined;

    public RandomCore(int threadNum, boolean enableUndetermined) {
        tested = new ArrayList<>();
        candidates = new ArrayList<>();
        this.threadNum = threadNum;
        this.enableUndetermined = enableUndetermined;
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
                wholeTag.addSource(tc.getId());
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
        Collections.shuffle(candidates);
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
                    var candidate = new BinaryCandidate(left, right, op);
                    if (!enableUndetermined && candidate.isUndetermined()) {
                        continue;
                    }
                    candidates.add(candidate);
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

        while (thisBatch.size() < size && !candidates.isEmpty()) {
            TestCaseCandidate candidate = candidates.remove(0);
            TestCase testCase = candidate.genTestCase();
            if (testCase != null) {
                thisBatch.add(testCase);
                tested.add(candidate);
            }
        }

        logger.info("Mini batch generated.");
        int binaryCount = 0;
        for (TestCase testCase : thisBatch) {
            if (testCase.getRule().formula instanceof OpConnector) {
                binaryCount++;
            }
        }

        logger.info("Binary test cases: {}, quantifier test cases: {}", binaryCount, thisBatch.size() - binaryCount);

        Collections.shuffle(thisBatch);
        return thisBatch;
    }

    public boolean hasMore() {
        return !candidates.isEmpty();
    }

    public void logStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (TestCaseCandidate candidate : tested) {
            if (candidate instanceof BinaryCandidate binaryCandidate) {
                String ruleName = TagApplication.getRuleName((BinaryTag) binaryCandidate.getLeft().tag,
                        (BinaryTag) binaryCandidate.getRight().tag, binaryCandidate.getNewOp());
                stats.put(ruleName, stats.getOrDefault(ruleName, 0) + 1);
            } else if (candidate instanceof QuantifierCandidate quantifierCandidate) {
                String ruleName = "";
                if (quantifierCandidate.getNewQuant().equals("forall")) {
                    ruleName = quantifierCandidate.getExpectTruth() ? "TA-forall-1" : "TA-forall-2";
                } else {
                    ruleName = quantifierCandidate.getExpectTruth() ? "TA-exists-1" : "TA-exists-2";
                }
                stats.put(ruleName, stats.getOrDefault(ruleName, 0) + 1);
            }
        }
        logger.info("Total {} test cases generated.", tested.size());
        stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> logger.info("Rule {}: {}", entry.getKey(), entry.getValue()));
    }
}
