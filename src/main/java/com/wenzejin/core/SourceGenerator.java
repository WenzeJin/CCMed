package com.wenzejin.core;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.XmlToRuleParser;
import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.rule.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocessor class.
 * Read rules from all XML files under rulesInputDirectory.
 * Generate random data for each rule.
 * List bfuncs for rules and bind them.
 */
public class SourceGenerator {

    private static final int RANDOM_BFUNC_FAIL_LIMIT = 3;

    private final List<TestCase> testCases;

    // configurable arguments
    private final String rulesInputDirectory;
    private final int enumMax;
    private final int threadMax;
    private final String bfuncPolicy;

    private static final Logger logger = LogManager.getLogger("SourceGenerator");

    public SourceGenerator(String rulesInput, int enumMax, int threadMax, String bfuncPolicy) {
        this.rulesInputDirectory = rulesInput;
        this.enumMax = enumMax;
        this.threadMax = threadMax;
        this.testCases = Collections.synchronizedList(new ArrayList<>());
        this.bfuncPolicy = bfuncPolicy;
        logger.info("Preprocessor initialized.  enumMax={} threadMax={} bfuncPolicy={}", enumMax, threadMax, bfuncPolicy);
    }

    /* Functions for size operations */

    /**
     * Retrieves the number of test cases.
     *
     * @return the size of the test cases collection.
     */
    public int getSize() {
        return testCases.size();
    }

    /**
     * Reduces the size of the test cases to the specified number by selecting a random subset.
     *
     * @param size the desired number of test cases after reduction
     *             (must be less than or equal to the current number of test cases).
     */
    public void reduceSize(int size) {
        logger.info("Reducing size from {} to {}", testCases.size(), size);
        List<TestCase> subset = RandomSubset.getRandom(testCases, size);
        testCases.clear();
        testCases.addAll(subset);
    }

    /**
     * Retrieves a random subset of test cases from the current list and removes them from the list.
     *
     * @param size the number of test cases to retrieve in the batch.
     * @return a list containing the randomly selected test cases.
     */
    public List<TestCase> getBatch(int size) {
        var batch = RandomSubset.getRandom(testCases, size);
        testCases.removeAll(batch);
        return batch;
    }


    /**
     * Generates test cases for the provided rules using a thread pool for parallel processing.
     * Each rule is processed in a separate thread, and the method waits for all tasks to complete
     * within a specified time limit.
     */
    public void generateTestCases() {
        testCases.clear();

        var rulesInput = readRules();
        if (rulesInput == null || rulesInput.isEmpty()) {
            logger.warn("No rules provided for test case generation.");
            return;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadMax);

        for (Rule rule : rulesInput) {
            executorService.submit(() -> {
                try {
                    generateTestCasesForRule(rule);
                } catch (Exception e) {
                    logger.error("Error generating test cases for rule {}: {}", rule.getId(), e.getMessage(), e);
                }
            });
        }

        executorService.shutdown();

        // Gracefully shutdown the executor service
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                logger.error("Timeout: Some tasks did not finish within the time limit.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Task execution interrupted.", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Generated {} test cases.", testCases.size());
    }

    /**
     * Reads and parses rules from XML files located in the specified directory.
     * Uses the {@link XmlToRuleParser} to convert XML files into a list of {@link Rule} objects.
     * 
     * @return A list of {@link Rule} objects parsed from the XML files, or {@code null} if an error occurs.
     */
    private List<Rule> readRules() {
        List<Rule> rulesInput = null;
        try {
            XmlToRuleParser parser = new XmlToRuleParser();
            rulesInput = parser.parseRulesInDirectory(rulesInputDirectory);
        } catch (XmlParseException e) {
            logger.error("Error parsing rules from XML files: {}", e.getMessage(), e);
        }
        return rulesInput;
    }

    
    /**
     * Generates test cases for a given rule by performing the following steps:
     * 1. Rebinds variables in the rule to ensure they are unique.
     * 2. Creates new patterns with random data and binds them to the rule.
     * 3. Generates a pool of variants for each boolean function (bfunc) in the rule.
     * 4. Lists all possible combinations of the bfunc variants.
     * 5. Select a random subset of the bfunc combinations based on a predefined limit.
     * 6. Emits the final test cases using the rule, new patterns, bfunc names, and combinations.
     *
     * @param rule The rule for which test cases are to be generated.
     */
    private void generateTestCasesForRule(Rule rule) {

        // Rebind variables to make sure they are unique
        rebindUniqueVariables(rule);

        // Generate random data for each pattern and bind them
        var newPatterns = createPatterns(rule);

        // Create variants of each bfunc.
    
        List<String> bfuncNames = getBfuncNames(rule);
        boolean log = false;

        if (log) {
            logger.info("bfuncNames: {}", bfuncNames.toString());
        }

        // Generate combinations of bfunc variants
        List<List<Bfunc>> bfuncCombinations;
        if (bfuncPolicy.equals("random")) {
            bfuncCombinations = randomBfuncs(rule, bfuncNames, enumMax);
        } else {
            // first enumerate all, then random a subset
            var bfuncPool = generateBfuncPool(rule);
            if (log) {
                logger.info("bfuncPool: {}", bfuncPool.toString());
            }
            bfuncCombinations = new ArrayList<>();
            enumerateBfuncs(0, bfuncNames, bfuncPool, new ArrayList<>(), bfuncCombinations);
            if (log) {
                logger.info("Total bfunc combinations: {}", bfuncCombinations.size());
                logger.info("bfuncCombinations: {}", bfuncCombinations.toString());
            }
            bfuncCombinations = RandomSubset.getRandom(bfuncCombinations, enumMax);
        }


        // Generate testcases
        emitTestCases(rule, newPatterns, bfuncNames, bfuncCombinations);
    }

    /**
     * Rebinds the variables in the given rule to ensure they are unique by prefixing
     * them with the rule's identifier. This helps to avoid variable name conflicts
     * when multiple rules are processed.
     *
     * <p>The method collects all variables from the rule, generates new unique
     * variable names by combining the rule's ID with the original variable names,
     * and then updates the rule to use the new variable names.
     *
     * @param rule the rule whose variables need to be rebound to unique names
     */
    private static void rebindUniqueVariables(Rule rule) {
        var variables = rule.collectVariables();
        Map<String, String> variableMap = new HashMap<>();
        for (var variable : variables) {
            String newVariable = rule.getId() + "_" + variable;
            variableMap.put(variable, newVariable);
        }
        rule.rebindVariables(variableMap);
    }

    /**
     * Creates a list of new {@link Pattern} objects based on the given {@link Rule}.
     * 
     * <p>This method processes the patterns collected from the provided rule, 
     * generates new pattern identifiers by appending the rule's ID, and creates 
     * corresponding {@link Pattern} objects. The method also updates the rule's 
     * pattern bindings with the new identifiers.
     * 
     * @param rule The {@link Rule} object containing the patterns to process.
     * @return A list of newly created {@link Pattern} objects with updated identifiers.
     */
    private static List<Pattern> createPatterns(Rule rule) {
        var patterns = rule.collectPatterns();
        Map<String, String> patternMap = new HashMap<>();
        List<Pattern> newPatterns = new ArrayList<>();
        for (var pattern : patterns) {
            String newPattern = rule.getId() + "_" + pattern;
            patternMap.put(pattern, newPattern);
            Pattern patternObj = new Pattern(newPattern);
            patternObj.setBinaryData();
            newPatterns.add(patternObj);
        }
        rule.rebindPatterns(patternMap);
        return newPatterns;
    }

    
    /**
     * Generates a pool of binary functions (Bfunc) for a given rule.
     * 
     * <p>This method collects all binary functions (Bfunc) associated with the provided
     * rule, lists new binary functions based on their parameter count, and assigns
     * parameter lists to the generated functions. The resulting pool is organized as a
     * map where the keys are the names of the original binary functions, and the values
     * are lists of newly generated binary functions.</p>
     * 
     * @param rule The rule for which the binary function pool is to be generated.
     * @return A map where the keys are binary function names and the values are lists
     *         of newly generated binary functions with assigned parameter lists.
     */
    private static Map<String, List<Bfunc>> generateBfuncPool(Rule rule) {
        var bfuncs = rule.collectBfuncs();
        Map<String, List<Bfunc>> bfuncPool = new HashMap<>();
        for (BfuncLiteral bfunc : bfuncs) {
            int paramNum = bfunc.params.size();
            String newNamePrefix = rule.getId() + "_" + bfunc.name;
            var bfuncList = Bfunc.enumerateBinaryBfunc(newNamePrefix, paramNum);

            // get a param list and add
            List<BfuncLiteral.Param> originalParamList = new ArrayList<>(bfunc.params);
            originalParamList.sort(BfuncLiteral.Param::compareTo);
            List<String> paramList = new ArrayList<>();
            for (var param : originalParamList) {
                paramList.add(param.var);
            }
            for (Bfunc bf : bfuncList) {
                bf.setParamList(paramList);
            }
            bfuncPool.put(bfunc.name, bfuncList);
        }
        return bfuncPool;
    }

    private static List<String> getBfuncNames(Rule rule) {
        var bfuncs = rule.collectBfuncs();
        List<String> bfuncNames = new ArrayList<>();
        for (BfuncLiteral bfunc : bfuncs) {
            bfuncNames.add(bfunc.name);
        }
        return bfuncNames;
    }


    /**
     * Generates and emits test cases based on the provided rule, patterns, 
     * bfunc names, and combinations of bfuncs. Each combination of bfuncs 
     * is used to create a new test case with a unique rule ID and associated 
     * patterns and bfuncs.
     *
     * @param rule         The base rule to clone and modify for each test case.
     * @param newPatterns  A list of patterns to associate with the generated test cases.
     * @param bfuncNames   A list of bfunc names used as keys for mapping bfuncs.
     * @param combinations A list of bfunc combinations, where each combination 
     *                     is a list of Bfunc objects to be mapped to the bfunc names.
     */
    private void emitTestCases(Rule rule, List<Pattern> newPatterns, List<String> bfuncNames, List<List<Bfunc>> combinations) {
        int count = 0;
        for (var combination : combinations) {
            Map<String, String> bfuncMap = new HashMap<>();
            for (int i = 0; i < bfuncNames.size(); i++) {
                bfuncMap.put(bfuncNames.get(i), combination.get(i).getName());
            }
            Rule newRule = rule.clone();
            newRule.id = newRule.id + "_" + count;
            newRule.rebindBfuncs(bfuncMap);
            TestCase testCase = new TestCase(newRule);
            testCase.setOriRule(rule.getId());
            testCase.setPatterns(newPatterns);
            testCase.setBfuncs(combination);
            testCases.add(testCase);
            count++;
        }
    }


    /**
     * Recursively lists all possible combinations of Bfunc objects based on the provided
     * bfunc names and their corresponding pools. The method generates all combinations
     * by iterating through the Bfunc objects associated with each name and adding them
     * to the current combination.
     *
     * @param index       The current index in the list of bfunc names being processed.
     * @param bfuncNames  A list of names representing the keys to access Bfunc objects
     *                    from the bfuncPool.
     * @param bfuncPool   A map where each key is a bfunc name and the value is a list
     *                    of Bfunc objects associated with that name.
     * @param current     A list representing the current combination of Bfunc objects
     *                    being constructed.
     * @param result      A list of lists where each inner list represents a complete
     *                    combination of Bfunc objects.
     */
    private static void enumerateBfuncs(int index, List<String> bfuncNames,
                                        Map<String, List<Bfunc>> bfuncPool,
                                        List<Bfunc> current, List<List<Bfunc>> result) {
        if (index == bfuncNames.size()) {
            result.add(new ArrayList<>(current));
        } else {
            var bfuncs = bfuncPool.get(bfuncNames.get(index));
            for (Bfunc bfunc : bfuncs) {
                current.add(bfunc);
                enumerateBfuncs(index + 1, bfuncNames, bfuncPool, current, result);
                current.remove(bfunc);
            }
        }
    }

    private static List<List<Bfunc>> randomBfuncs(Rule rule, List<String> bfuncNames, int enumMax) {
        Set<List<Bfunc>> bfuncCombinations = new java.util.HashSet<>();

        var bfuncs = rule.collectBfuncs();
        Map<String, BfuncLiteral> bfuncLiteralMap = new HashMap<>();
        for (BfuncLiteral bf : bfuncs) {
            bfuncLiteralMap.put(bf.name, bf);
        }

        int failCount = 0;
        int idCounter = 0;
        while (bfuncCombinations.size() < enumMax) {
            List<Bfunc> currentCombination = new ArrayList<>();
            for (String name : bfuncNames) {
                BfuncLiteral literal = bfuncLiteralMap.get(name);
                Bfunc bfunc = Bfunc.getOneRandomBfunc(rule.getId() + "_" + name, literal.params.size(), idCounter);

                List<BfuncLiteral.Param> originalParamList = new ArrayList<>(literal.params);
                originalParamList.sort(BfuncLiteral.Param::compareTo);
                List<String> paramList = new ArrayList<>();
                for (var param : originalParamList) {
                    paramList.add(param.var);
                }
                bfunc.setParamList(paramList);

                currentCombination.add(bfunc);
            }

            if (!bfuncCombinations.add(currentCombination)) {
                failCount++;
            } else {
                failCount = 0;
                idCounter++;
            }

            if (failCount >= RANDOM_BFUNC_FAIL_LIMIT) {
                break;
            }
        }

        return new ArrayList<>(bfuncCombinations);
    }
}
