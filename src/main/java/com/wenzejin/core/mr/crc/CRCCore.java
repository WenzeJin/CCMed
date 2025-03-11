package com.wenzejin.core.mr.crc;

import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.MRCore;
import com.wenzejin.core.mr.crc.rules.TagApplication;
import com.wenzejin.core.mr.crc.rules.TagCreation;
import com.wenzejin.core.mr.crc.rules.TagUnification;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.OpQuantifier;
import com.wenzejin.core.util.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class CRCCore implements MRCore {

    private List<FormulaWithTag> binaryPool;
    private List<FormulaWithTag> quantifierPool;

    private final int limit;
    private final boolean enableBoost;
    private final Map<String, Integer> categoricalCount;

    private static final Logger logger = LogManager.getLogger("CRCCore");

    public CRCCore(int limit, boolean enableBoost) {
        this.limit = limit;
        this.enableBoost = enableBoost;
        categoricalCount = new HashMap<>();
    }

    public int getCategoricalLimit(String type, int limit) {
        if (!enableBoost) {
            return Integer.MAX_VALUE;
        }
        int typeLimit = limit / 5; // and, or, implies, forall, exists
        return switch (type) {
            case "and", "or" ->     typeLimit / 6;
            case "implies" ->       typeLimit / 8;
            case "forall", "exists" -> typeLimit / 4;
            default ->              Integer.MAX_VALUE;
        };
    }

    @Override
    public void generateTestCases(List<Integer> ids, String newBatchName) throws SQLException {
        init();
        initPools(ids);
        var binaryPairs = shuffleBinaryPool();

        var quantifierPairs = shuffleQuantifierPool();

        generateBinaryCase(binaryPairs, newBatchName + "temp");
        generateQuantifierCase(quantifierPairs, newBatchName + "temp");

        // 先平衡地从每个类别中选取一定数量的测试用例
        for (String type: categoricalCount.keySet()) {
            String[] parts = type.split("-");
            String op = parts[1];
            int count = getCategoricalLimit(op, limit);
            TestCaseDB.randomSubsetWithCond(count, newBatchName + "temp", newBatchName, "ta_rule = '" + type + "'");
        }

        // 再从剩余的测试用例中随机选取一定数量的测试用例补齐
        int currSize = TestCaseDB.getSizeByCondition("batch_name = '" + newBatchName + "'");
        int left = limit - currSize;
        if (left > 0) {
            TestCaseDB.randomSubset(left, newBatchName + "temp", newBatchName);
        }

        try {
            var parentSet = TestCaseDB.getParentSet(newBatchName);
            double rate = (double) parentSet.size() / ids.size();
            logger.info("Utilization rate analysis: {} %", rate * 100);
        } catch (SQLException e) {
            logger.warn("Utilization rate analysis failed, check SQL");
        }

        // 删除没有选中的测试用例
        TestCaseDB.deleteBatch(newBatchName + "temp");

        destroy();
    }


    /**
     * Init the CRC core
     */
    public void init() {
        binaryPool = new ArrayList<>();
        quantifierPool = new ArrayList<>();
    }

    public void initPools(List<Integer> ids) {
        logger.info("Initializing CRC core pools from DB, total test cases: {}", ids.size());
        for (int id: ids) {
            try {
                TestCase tc = TestCaseDB.getTestCaseById(id);
                if (tc != null) {
                    initPoolSingle(tc);
                }
            } catch (SQLException e) {
                logger.warn("Failed to get test case with id: {}, check SQL", id);
            } catch (IOException e) {
                logger.warn("Failed to get test case with id: {}, check IO", id);
            }
        }
        logger.info("Done, binary pool size: {}, quantifier pool size {}.", binaryPool.size(), quantifierPool.size());
    }

    private void initPoolSingle(TestCase tc) {
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

    private List<Map.Entry<FormulaWithTag, FormulaWithTag>> shuffleBinaryPool() {
        logger.info("Shuffling binary pool, size: {}", binaryPool.size());

        int nnCount = 0;

        List<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs = new ArrayList<>();

        for (FormulaWithTag first: binaryPool) {
            for (FormulaWithTag second: binaryPool) {
                BinaryTag firstTag = (BinaryTag) first.tag;
                BinaryTag secondTag = (BinaryTag) second.tag;
                if (firstTag == secondTag) {
                    continue;
                }

                if (firstTag.truth == BinaryTag.Truth.UNKNOWN && secondTag.truth == BinaryTag.Truth.UNKNOWN) {
                    nnCount++;
                    continue;
                }
                pairs.add(Map.entry(first, second));
            }
        }
        Collections.shuffle(pairs);
        logger.info("Done, total pairs: {}, ignored NN pairs: {}", pairs.size(), nnCount);

        return pairs;
    }

    private List<Map.Entry<FormulaWithTag, FormulaWithTag>> shuffleQuantifierPool() {
        logger.info("Shuffling quantifier pool, size: {}", quantifierPool.size());

        List<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs = new ArrayList<>();

        for (FormulaWithTag first: quantifierPool) {
            for (FormulaWithTag second: quantifierPool) {
                if (first == second) {
                    continue;
                }
                pairs.add(Map.entry(first, second));
            }
        }
        Collections.shuffle(pairs);
        logger.info("Done, total pairs: {}", pairs.size());

        return pairs;
    }


    private void generateBinaryCase(List<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs, String newBatchName) {
        String[] ops = {"and", "or", "implies"};
        int count = 0;
        int progress = 0;
        final int total = pairs.size() * ops.length;

        for (var pair: pairs) {
            for (String op: ops) {
                TestCase genTC = TagApplication.binaryEntry(pair.getKey(), pair.getValue(), op);
                progress++;
                if (progress % (total / 10) == 0) {
                    logger.info("BinaryOp progress: {} %", progress * 100 / total);
                }
                if (genTC != null) {
                    String taRule = "";
                    for(String rule: genTC.getRuleUsed()) {
                        if(rule.startsWith("TA")) {
                            taRule = rule;
                            break;
                        }
                    }
                    if (categoricalCount.getOrDefault(taRule, 0) >= getCategoricalLimit(op, 5 * limit)) {
                        continue;
                    }
                    try {
                        TestCaseDB.storeTestCase(genTC, newBatchName);
                        count++;
                        categoricalCount.put(taRule, categoricalCount.getOrDefault(taRule, 0) + 1);
                    } catch (SQLException e) {
                        logger.warn("Failed to store test case: {}, check SQL", genTC);
                    } catch (IOException e) {
                        logger.warn("Failed to store test case: {}, check IO", genTC);
                    }
                }

            }
        }
        logger.info("Generated {} test cases from binary pool, and stored in DB.", count);
    }

    private void generateQuantifierCase(List<Map.Entry<FormulaWithTag, FormulaWithTag>> pairs, String newBatchName) {
        String[] bops = {"and", "or", "implies"};
        String[] qops = {"forall", "exists"};
        int count = 0;
        final int total = pairs.size() * bops.length;
        int progress = 0;

        for (var pair: pairs) {
            for (String op: bops) {
                FormulaWithTag gen = TagUnification.unify(pair.getKey(), pair.getValue(), op);
                progress++;
                if (progress % (total / 10) == 0) {
                    logger.info("Quantifier progress: {} %", progress * 100 / total);
                }
                if (gen == null) {
                    continue;
                }
                for (String qop: qops) {
                    TestCase genTC = TagApplication.quantifierEntry(gen, qop, true);
                    count += postProcessQuantifier(genTC, newBatchName, qop);
                    genTC = TagApplication.quantifierEntry(gen, qop, false);
                    count += postProcessQuantifier(genTC, newBatchName, qop);
                }
            }
        }

        logger.info("Generated {} test cases from quantifier pool, and stored in DB.", count);
    }

    private int postProcessQuantifier(TestCase genTC, String newBatchName, String op) {
        if (genTC != null) {
            String taRule = "";
            for(String rule: genTC.getRuleUsed()) {
                if(rule.startsWith("TA")) {
                    taRule = rule;
                    break;
                }
            }
            if (categoricalCount.getOrDefault(taRule, 0) >= getCategoricalLimit(op, 5 * limit)) {
                return 0;
            }
            try {
                TestCaseDB.storeTestCase(genTC, newBatchName);
                categoricalCount.put(taRule, categoricalCount.getOrDefault(taRule, 0) + 1);
                return 1;
            } catch (SQLException e) {
                logger.warn("Failed to store test case: {}, check SQL", genTC);
            } catch (IOException e) {
                logger.warn("Failed to store test case: {}, check IO", genTC);
            }
        }
        return 0;
    }

    /**
     * Destroy the CRC core
     */
    public void destroy() {
        binaryPool = null;
        quantifierPool = null;
    }
}
