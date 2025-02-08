package com.wenzejin.core.mr.crc;

import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MRCore;
import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CRCCore implements MRCore {

    private List<FormulaWithTag> binaryPool;
    private List<FormulaWithTag> quantifierPool;

    private int limit;

    private static final Logger logger = LogManager.getLogger("CRCCore");

    public CRCCore() {
        limit = Integer.MAX_VALUE;
    }

    public CRCCore(int limit) {
        this.limit = limit;
    }

    /**
     * @param sourceTestCases
     * @return
     */
    @Override
    public List<TestCase> generateTestCases(List<TestCase> sourceTestCases) {
        init();
        initPools(sourceTestCases);
        List<TestCase> genTCs = new ArrayList<>();
        var binaryPairs = shuffleBinaryPool();
        genTCs.addAll(generateBinaryCase(binaryPairs));
        // TODO: add quantifier case

        int beforeSize = genTCs.size();

        genTCs = RandomSubset.getRandom(genTCs, limit);
        Set<TestCase> utilized = new HashSet<>();
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

    public List<TestCase> generateBinaryCase(Set<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs) {
        List<TestCase> genTCs = new ArrayList<>();
        List<String> ops = List.of("and", "or", "implies");
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

    /**
     * Destroy the CRC core
     */
    public void destroy() {
        binaryPool = null;
        quantifierPool = null;
    }


}
