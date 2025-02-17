package com.wenzejin.core.mr.crc;

import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MRCore;
import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.mr.crc.rules.TagUnification;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CRCCore implements MRCore {

    private List<FormulaWithTag> binaryPool;
    private List<FormulaWithTag> quantifierPool;

    private int limit;

    private double binaryRate;

    private static final Logger logger = LogManager.getLogger("CRCCore");

    public CRCCore() {
        limit = Integer.MAX_VALUE;
        binaryRate = 0.7;
    }

    public CRCCore(int limit) {
        this.limit = limit;
        binaryRate = 0.7;
    }

    /**
     * @param sourceTestCases
     * @return
     */
    @Override
    public List<TestCase> generateTestCases(List<TestCase> sourceTestCases) {
        init();
        initPools(sourceTestCases);
        List<TestCase> genBinTCs = new ArrayList<>();
        var binaryPairs = shuffleBinaryPool();
        genBinTCs.addAll(generateBinaryCase(binaryPairs));
        List<TestCase> genQuantTCs = new ArrayList<>();
        shuffleQuantifierPool();
        quantifierUnification();
        genQuantTCs.addAll(generateQuantifierCase());

        int binNum = (int)(limit * binaryRate);
        int quantNum = limit - binNum;

        int beforeSize = genBinTCs.size() + genQuantTCs.size();

        genBinTCs = RandomSubset.getRandom(genBinTCs, binNum);
        genQuantTCs = RandomSubset.getRandom(genQuantTCs, quantNum);
        List<TestCase> genTCs = new ArrayList<>(genQuantTCs);
        genTCs.addAll(genBinTCs);
        Set<Integer> utilized = new HashSet<>();
        for (TestCase tc: genTCs) {
            utilized.addAll(tc.getSource());
        }
        logger.info("Sampled {} test cases from all {} cases, source utilization rate: {}%.",
                genTCs.size(), beforeSize, (float)utilized.size() * 100 / sourceTestCases.size() );
        destroy();
        return genTCs;
    }


    /**
     * Init the CRC core
     */
    public void init() {
        binaryPool = new ArrayList<>();
        quantifierPool = new ArrayList<>();
    }

    public void initPools(List<TestCase> tcs) {
        logger.info("Initializing CRC core pools, total test cases: {}", tcs.size());
        for (TestCase tc: tcs) {
            Formula formula = tc.getRule().formula;
            if (formula instanceof OpConnector) {
                // binary formula
                Formula leftFormula = ((OpConnector) formula).left;
                Formula rightFormula = ((OpConnector) formula).right;
                List<Tag> tags;
                try {
                    tags = TagCreation.entry(tc);
                } catch (NoTestResultException e) {
                    logger.warn("No test result for test case: {}, ignore!", tc);
                    continue;
                }
                FormulaWithTag left = new FormulaWithTag(leftFormula, tags.get(0), tc.getPatterns(), tc.getBfuncs(), tc);
                FormulaWithTag right = new FormulaWithTag(rightFormula, tags.get(1), tc.getPatterns(), tc.getBfuncs(), tc);
                binaryPool.add(left);
                binaryPool.add(right);
            } else if (formula instanceof OpQuantifier) {
                // quantifier formula
                List<Tag> tags;
                Formula child = ((OpQuantifier) formula).formula;
                try {
                    tags = TagCreation.entry(tc);
                } catch (NoTestResultException e) {
                    logger.warn("No test result for test case: {}, ignore!", tc);
                    continue;
                }
                FormulaWithTag quantifier = new FormulaWithTag(child, tags.getFirst(), tc.getPatterns(), tc.getBfuncs(), tc);
                quantifierPool.add(quantifier);
            }
        }
        logger.info("Done, binary pool size: {}, quantifier pool size {}.", binaryPool.size(), quantifierPool.size());
    }

    public Set<Map.Entry<FormulaWithTag, FormulaWithTag>> shuffleBinaryPool() {
        logger.info("Shuffling binary pool, size: {}", binaryPool.size());

        int nnCount = 0;

        Set<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs = new HashSet<>();

        for (FormulaWithTag first: binaryPool) {
            for (FormulaWithTag second: binaryPool) {
                BinaryTag firstTag = (BinaryTag) first.tag;
                BinaryTag secondTag = (BinaryTag) second.tag;
                if (firstTag.truth == BinaryTag.Truth.UNKNOWN && secondTag.truth == BinaryTag.Truth.UNKNOWN) {
                    nnCount++;
                    continue;
                }
                pairs.add(Map.entry(first, second));
            }
        }

        logger.info("Done, total pairs: {}, ignored NN pairs: {}", pairs.size(), nnCount);

        return pairs;
    }

    public Set<Map.Entry<FormulaWithTag, FormulaWithTag>> shuffleQuantifierPool() {
        logger.info("Shuffling quantifier pool, size: {}", quantifierPool.size());

        Set<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs = new HashSet<>();

        for (FormulaWithTag first: quantifierPool) {
            for (FormulaWithTag second: quantifierPool) {
                pairs.add(Map.entry(first, second));
            }
        }

        logger.info("Done, total pairs: {}", pairs.size());

        return pairs;
    }

    public void quantifierUnification() {
        Set<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs = shuffleQuantifierPool();
        quantifierPool = new ArrayList<>();
        List<String> ops = List.of("and", "or", "implies");
        for (var pair: pairs) {
            for (String op: ops) {
                FormulaWithTag gen = TagUnification.unify(pair.getKey(), pair.getValue(), op);
                if (gen != null) {
                    quantifierPool.add(gen);
                }
            }
        }

    }

    public List<TestCase> generateBinaryCase(Set<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs) {
        List<TestCase> genTCs = new ArrayList<>();
        String[] ops = {"and", "or", "implies"};
        for (var pair: pairs) {
            for (String op: ops) {
                TestCase genTC = TagApplication.binaryEntry(pair.getKey(), pair.getValue(), op);
                if (genTC != null) {
                    genTCs.add(genTC);
                }
            }
        }
        logger.info("Generated {} test cases from binary pool.", genTCs.size());
        return genTCs;
    }

    public List<TestCase> generateQuantifierCase() {
        List<TestCase> genTCs = new ArrayList<>();
        String[] ops = {"forall", "exists"};
        for (var item: quantifierPool) {
            for (String op: ops) {
                TestCase genTC = TagApplication.quantifierEntry(item, op, true);
                if (genTC != null) {
                    genTCs.add(genTC);
                }
                genTC = TagApplication.quantifierEntry(item, op, false);
                if (genTC != null) {
                    genTCs.add(genTC);
                }
            }
        }
        logger.info("Generated {} test cases from quantifier pool.", genTCs.size());
        return genTCs;
    }

    /**
     * Destroy the CRC core
     */
    public void destroy() {
        binaryPool = null;
        quantifierPool = null;
    }


}
