package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.jin.rules.TagApplication;
import com.wenzejin.core.mr.jin.rules.TagCreation;
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

public class ARTCore implements MTCore {
    private static final Logger logger = LogManager.getLogger("ARTCore");
    private static double binaryRate = 0.8;
    private static final double randomRate = 0.3;

    List<TestCaseCandidate> tested;

    List<TestCaseCandidate> binaryCandidates;
    List<TestCaseCandidate> quantifierCandidates;

    final int threadNum;
    boolean enableUndetermined;

    public ARTCore(int threadNum, boolean enableUndetermined) {
        tested = new ArrayList<>();
        binaryCandidates = new ArrayList<>();
        quantifierCandidates = new ArrayList<>();
        this.threadNum = threadNum;
        this.enableUndetermined = enableUndetermined;
        if (enableUndetermined) {
            binaryRate = 0.8;
        } else {
            binaryRate = 0.7;
        }
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
        logger.info("Total {} candidates generated.", binaryCandidates.size(), quantifierCandidates.size());
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
                    binaryCandidates.add(candidate);
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
                        quantifierCandidates.add(new QuantifierCandidate(left, right, op, quantifierOp, true));
                        quantifierCandidates.add(new QuantifierCandidate(left, right, op, quantifierOp, false));
                    }
                }
            }
        }
    }

    public List<TestCase> getNextBatch(int size) {
        List<TestCase> thisBatch = new ArrayList<>();
        List<TestCaseCandidate> used = new ArrayList<>();

        List<TestCase> bin = new ArrayList<>();
        List<TestCaseCandidate> binUsed = new ArrayList<>();
        List<TestCase> quant = new ArrayList<>();
        List<TestCaseCandidate> quantUsed = new ArrayList<>();

        Collections.shuffle(binaryCandidates);
        Collections.shuffle(quantifierCandidates);

        int binaryBudget = (int) (size * binaryRate);
        int quantifierBudget = size - binaryBudget;

        // first, we add some random candidates into this batch (by random rate)
        int randomSize = (int) (size * randomRate);
        for (int i = 0; i < randomSize; i++) {
            if (binaryCandidates.isEmpty() && quantifierCandidates.isEmpty()) {
                break;
            }
            TestCaseCandidate candidate;
            if (binaryCandidates.isEmpty() || bin.size() >= binaryBudget) {
                candidate = quantifierCandidates.remove(0);
            } else if (quantifierCandidates.isEmpty() || quant.size() >= quantifierBudget) {
                candidate = binaryCandidates.remove(0);
            } else {
                candidate = Math.random() < binaryRate ? binaryCandidates.remove(0) : quantifierCandidates.remove(0);
            }
            TestCase tc = candidate.genTestCase();
            if (tc != null) {
                if (candidate instanceof BinaryCandidate) {
                    bin.add(tc);
                    binUsed.add(candidate);
                } else if (candidate instanceof QuantifierCandidate) {
                    quant.add(tc);
                    quantUsed.add(candidate);
                }
            }
        }

        binaryCandidates.sort(Collections.reverseOrder());
        quantifierCandidates.sort(Collections.reverseOrder());


        while (bin.size() < binaryBudget) {
            if (binaryCandidates.isEmpty()) {
                break;
            }
            TestCaseCandidate candidate = binaryCandidates.remove(0);
            TestCase tc = candidate.genTestCase();
            if (tc != null) {
                bin.add(tc);
                binUsed.add(candidate);
            }
        }
        while (quant.size() < quantifierBudget) {
            if (quantifierCandidates.isEmpty()) {
                break;
            }
            TestCaseCandidate candidate = quantifierCandidates.remove(0);
            TestCase tc = candidate.genTestCase();
            if (tc != null) {
                quant.add(tc);
                quantUsed.add(candidate);
            }
        }
        int remaining = size - bin.size() - quant.size();
        if (remaining > 0) {
            if (!binaryCandidates.isEmpty()) {
                for (int i = 0; i < remaining; i++) {
                    if (binaryCandidates.isEmpty()) {
                        break;
                    }
                    TestCaseCandidate candidate = binaryCandidates.remove(0);
                    TestCase tc = candidate.genTestCase();
                    if (tc != null) {
                        bin.add(tc);
                        binUsed.add(candidate);
                    }
                }
            } else {
                for (int i = 0; i < remaining; i++) {
                    if (quantifierCandidates.isEmpty()) {
                        break;
                    }
                    TestCaseCandidate candidate = quantifierCandidates.remove(0);
                    TestCase tc = candidate.genTestCase();
                    if (tc != null) {
                        quant.add(tc);
                        quantUsed.add(candidate);
                    }
                }
            }
        }

        // calc average distance of bin and quant
        Collections.sort(binUsed);
        Collections.sort(quantUsed);

        logger.info("Mini batch generated. Binary distance: Min {} Max {} Avg {}; Quantifier distance: Min {} Max {} Avg {}",
                binUsed.get(0).getMinDistance(), binUsed.get(binUsed.size() - 1).getMinDistance(),
                binUsed.stream().mapToDouble(TestCaseCandidate::getMinDistance).average().orElse(0),
                quantUsed.get(0).getMinDistance(), quantUsed.get(quantUsed.size() - 1).getMinDistance(),
                quantUsed.stream().mapToDouble(TestCaseCandidate::getMinDistance).average().orElse(0));
                        
        thisBatch.addAll(bin);
        thisBatch.addAll(quant);
        used.addAll(binUsed);
        used.addAll(quantUsed);
        tested.addAll(used);

        logger.info("Binary cases: {}, Quantifier cases: {}", binUsed.size(), quantUsed.size());

        parallelUpdateMinDistance(used);
        return thisBatch;
    }

    public boolean hasMore() {
        return binaryCandidates.size() + quantifierCandidates.size() > 0;
    }


    private void parallelUpdateMinDistance(List<TestCaseCandidate> selected) {
        List<TestCaseCandidate> binary = new ArrayList<>();
        List<TestCaseCandidate> quantifier = new ArrayList<>();
        for (TestCaseCandidate candidate : selected) {
            if (candidate instanceof BinaryCandidate) {
                binary.add(candidate);
            } else if (candidate instanceof QuantifierCandidate) {
                quantifier.add(candidate);
            }
        }
        binaryCandidates.parallelStream().forEach(candidate -> binary.forEach(candidate::updateMinDistance));
        quantifierCandidates.parallelStream().forEach(candidate -> quantifier.forEach(candidate::updateMinDistance));
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
        logger.info("Tested candidates: {}", tested.size());
        // sort and output
        stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> logger.info("Rule {}: {}", entry.getKey(), entry.getValue()));
    }
}
