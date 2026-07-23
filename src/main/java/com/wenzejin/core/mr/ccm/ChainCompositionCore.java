package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MTCore;
import com.wenzejin.core.mr.jin.rules.*;
import com.wenzejin.core.result.TestOutcome;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChainCompositionCore implements MTCore {
    private static final Logger logger = LogManager.getLogger("ChainCompCore");

    PrimitiveUnitRepository puRepo;
    private final int chainLength;
    private final String strategy;

    // statistics
    private long totalAttempts = 0;
    private long totalGenerated = 0;
    private long totalFailed = 0;

    public ChainCompositionCore(int chainLength, String strategy) {
        puRepo = new PrimitiveUnitRepository();

        if (chainLength < 2) {
            chainLength = 2;
            logger.warn("chainLength < 2, reset to 2");
        }

        if (chainLength > 6) {
            chainLength = 6;
            logger.warn("chainLength > 6, reset to 6");
        }

        this.chainLength = chainLength;
        this.strategy = strategy;
    }

    @Override
    public void initCandidates(List<TestOutcome> outcomes) {
        puRepo = new PrimitiveUnitRepository();


        for (TestOutcome outcome : outcomes) {
            if (outcome.sutResult == null) continue;
            TestCase tc = outcome.testCase;
            Formula formula = tc.getRule().formula;
            try {
                ClosedTag wholeTag = new ClosedTag(outcome.sutResult.truth ? ClosedTag.Truth.TRUE: ClosedTag.Truth.FALSE, outcome.sutResult.links);
                wholeTag.addSource(tc.getId());
                PrimitiveUnit whole = new PrimitiveUnit(formula, wholeTag, tc.getPatterns(), tc.getBfuncs());
                whole.oriRule = tc.getOriRule();
                puRepo.addClosedUnit(whole);

                if (Math.random() < 0.05) {
                    OpenTag wholeTagOpen = new OpenTag(outcome.sutResult.truth);
                    wholeTagOpen.addSource(tc.getId());
                    PrimitiveUnit wholeOpen = new PrimitiveUnit(formula, wholeTagOpen, tc.getPatterns(), tc.getBfuncs());
                    wholeOpen.oriRule = tc.getOriRule();
                    puRepo.addOpenUnit(wholeOpen);
                }

                List<Tag> tags = PUExtraction.entry(outcome);
                if (formula instanceof OpConnector) {
                    // binary formula
                    ClosedTag lt = (ClosedTag) tags.get(0);
                    if (lt.truth != ClosedTag.Truth.UNKNOWN) {
                        Formula leftFormula = ((OpConnector) formula).left;
                        PrimitiveUnit left = new PrimitiveUnit(leftFormula, lt, tc.getPatterns(), tc.getBfuncs());
                        left.oriRule = tc.getOriRule();
                        puRepo.addClosedUnit(left);
                    }
                    
                    ClosedTag rt = (ClosedTag) tags.get(1);
                    if (rt.truth != ClosedTag.Truth.UNKNOWN) {
                        Formula rightFormula = ((OpConnector) formula).right;
                        PrimitiveUnit right = new PrimitiveUnit(rightFormula, rt, tc.getPatterns(), tc.getBfuncs());
                        right.oriRule = tc.getOriRule();
                        puRepo.addClosedUnit(right);
                    }
                } else if (formula instanceof OpQuantifier) {
                    // quantifier formula
                    Formula child = ((OpQuantifier) formula).formula;
                    PrimitiveUnit quantifier = new PrimitiveUnit(child, tags.get(0), tc.getPatterns(), tc.getBfuncs());
                    quantifier.oriRule = tc.getOriRule();
                    puRepo.addOpenUnit(quantifier);
                }
            } catch (NoTestResultException e) {
                logger.warn("No test result for test case: {}, ignoring!", tc);
            } catch (NoRuleException e) {
                logger.info("We cannot use tc {} for decomposition-based extraction!", tc.getId());
            }
        }

        logger.info("PU extracted: {} total ({} closed + {} open), chainLength={}",
                puRepo.getSize(), puRepo.getClosedSize(), puRepo.getOpenSize(), chainLength);
    }

    @Override
    public List<TestCase> getNextBatch(int size) {
        List<TestCase> batch = new ArrayList<>();
        int attempts = 0;
        for (int i = 0; i < 4 * size && batch.size() < size; i++) {
            String[] chain = getRandomChain();
            TestCase tc = generateTestCaseByChain(chain);
            attempts++;
            if (tc != null) {
                batch.add(tc);
            }
        }
        double successRate = attempts > 0 ? 100.0 * batch.size() / attempts : 0;
        logger.info("getNextBatch: {}/{} cases generated ({} attempts, {}% success)",
                batch.size(), size, attempts, String.format("%.1f", successRate));
        return batch;
    }

    @Override 
    public boolean hasMore() {
        if (strategy.equals("random")) {
            return true;
        } else {
            logger.warn("Strategy {} not implemented yet, returning false for hasMore()", strategy);
            return false;
        }
    }

    @Override
    public void logStats() {
        logger.info("ChainCompositionCore stats: totalAttempts={}, totalGenerated={}, totalFailed={}, "
                + "successRate={}%, avgAttempts={}",
                totalAttempts, totalGenerated, totalFailed,
                totalAttempts > 0 ? String.format("%.1f", 100.0 * totalGenerated / (totalGenerated + totalFailed)) : "N/A",
                totalGenerated > 0 ? String.format("%.1f", (double) totalAttempts / totalGenerated) : "N/A");
    }


    private PrimitiveUnit compositeBinaryStep(PrimitiveUnit current, String op,
                                               boolean currentIsLeft, boolean isOpen) {
        // Determine which original rules to exclude to avoid self-composition
        List<String> excludedRules;
        if (current.oriRules != null) {
            excludedRules = current.oriRules;
        } else if (current.oriRule != null && !current.oriRule.isEmpty()) {
            excludedRules = List.of(current.oriRule);
        } else {
            excludedRules = List.of();
        }

        // Pick a random candidate unit of the appropriate type
        PrimitiveUnit other;
        if (isOpen) {
            other = puRepo.getRandomOpenUnitExcept(excludedRules);
        } else {
            other = puRepo.getRandomClosedUnitExcept(excludedRules);
        }
        if (other == null) {
            return null; // no suitable unit found, triggers retry
        }

        // Merge with the correct argument order
        if (isOpen) {
            return currentIsLeft
                ? PUComposition.unifyForQuant(current, other, op)
                : PUComposition.unifyForQuant(other, current, op);
        } else {
            return currentIsLeft
                ? PUComposition.unifyForBin(current, other, op)
                : PUComposition.unifyForBin(other, current, op);
        }
    }

    public TestCase generateTestCaseByChain(String[] chain) {
        boolean isOpen = false;
        String lastAction = chain[chain.length - 1];
        if (lastAction.equals("FORALLt") || lastAction.equals("FORALLf")
            || lastAction.equals("EXISTSt") || lastAction.equals("EXISTSf")) {
            isOpen = true;
        }

        for (int attempt = 1; attempt <= 4; attempt++) {
            PrimitiveUnit current;

            // Phase 1: Initialize a random starting unit from repository
            if (isOpen) {
                current = puRepo.getRandomOpenUnit();
            } else {
                current = puRepo.getRandomClosedUnit();
            }
            if (current == null) {
                continue; // no available unit, retry
            }
            current.initializeForComposition(); // Initialize oriRules for composition tracking

            // Phase 2: Walk the chain, applying each action in sequence
            boolean failed = false;
            for (String action : chain) {
                switch (action) {
                    case "NOT":
                        current = PUComposition.negation(current);
                        break;
                    case "ANDl":
                        current = compositeBinaryStep(current, "and", true, isOpen);
                        break;
                    case "ANDr":
                        current = compositeBinaryStep(current, "and", false, isOpen);
                        break;
                    case "ORl":
                        current = compositeBinaryStep(current, "or", true, isOpen);
                        break;
                    case "ORr":
                        current = compositeBinaryStep(current, "or", false, isOpen);
                        break;
                    case "IMPl":
                        current = compositeBinaryStep(current, "implies", true, isOpen);
                        break;
                    case "IMPr":
                        current = compositeBinaryStep(current, "implies", false, isOpen);
                        break;
                    default:
                        // Quantifier actions are the last action, handled after the loop
                        break;
                }
                if (current == null) {
                    failed = true;
                    break;
                }
            }
            if (failed) {
                continue;
            }

            // Phase 3: Finalize — produce a TestCase from the accumulated PrimitiveUnit
            TestCase tc = null;
            if (isOpen) {
                String newOp;
                boolean expectTruth;
                switch (lastAction) {
                    case "FORALLt": newOp = "forall"; expectTruth = true;  break;
                    case "FORALLf": newOp = "forall"; expectTruth = false; break;
                    case "EXISTSt": newOp = "exists"; expectTruth = true;  break;
                    case "EXISTSf": newOp = "exists"; expectTruth = false; break;
                    default:
                        return null; // should not happen
                }
                tc = TestCaseSetup.quantifierEntry(current, newOp, expectTruth);
            } else {
                tc = TestCaseSetup.binaryFinalize(current);
            }

            if (tc != null) {
                totalGenerated++;
                totalAttempts += attempt;
                logger.debug("chain: {} -> success on attempt {}", Arrays.toString(chain), attempt);
                return tc;
            }
        }

        // All attempts exhausted
        totalFailed++;
        totalAttempts += 4;
        logger.warn("chain: {}, failed to generate test case after 4 attempts",
            Arrays.toString(chain));
        return null;
    }





    // Chain Generation Strategies
    // Chain is a sequence of primitive composition actions, including NOT, ANDl/r, ORl/r, IMPl/r, FORALLt/f, EXISTSt/f.
    // FORALLt/f and EXISTSt/f can only be applied to the last action in the chain.

    private String[] getRandomChain() {
        String[] chain = new String[chainLength];
        String[] binActions = {"NOT", "ANDl", "ANDr", "ORl", "ORr", "IMPl", "IMPr"};
        String[] allActions = {"NOT", "ANDl", "ANDr", "ORl", "ORr", "IMPl", "IMPr",
                               "FORALLt", "FORALLf", "EXISTSt", "EXISTSf"};

        // non-terminal positions: only non-quantifier actions
        for (int i = 0; i < chainLength - 1; i++) {
            chain[i] = binActions[(int) (Math.random() * binActions.length)];
        }
        // last position: any action including quantifiers
        chain[chainLength - 1] = allActions[(int) (Math.random() * allActions.length)];

        return chain;
    }
}
