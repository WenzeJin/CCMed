package com.wenzejin.core.preprocess;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.rule.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.TestCase;
import com.wenzejin.core.util.XmlToRuleParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocessor class.
 * Read rules from all xml files under rulesInputDirectory.
 * Generate random data for each rule.
 * Enumerate bfuncs for rules and bind them.
 */
public class Preprocessor {

    private List<Rule> rulesInput;
    private List<TestCase> testCases;

    // configurable arguments
    private String rulesInputDirectory;
    private int enumMax;
    private int threadMax;

    // default configurations
    private static final int DEFAULT_ENUM_MAX = Integer.MAX_VALUE;
    private static final int DEFAULT_THREAD_MAX = Runtime.getRuntime().availableProcessors();

    private static final Logger logger = LogManager.getLogger("Preprocessor");

    /**
     * Create a new Preprocessor with arguments. (String, Object)
     * Arguments Available:
     * preprocessor.rules-input-directory: string (must)
     * preprocessor.enum-max: int
     * preprocessor.thread-max: int
     */
    public Preprocessor(Map<String, Object> args) {
        rulesInputDirectory = (String) args.get("preprocessor.rules-input-directory");
        if (rulesInputDirectory == null || rulesInputDirectory.isEmpty()) {
            logger.fatal("No directory specified for rules input");
            throw new IllegalArgumentException("No directory specified for rules input.");
        }

        enumMax = (int) args.getOrDefault("preprocessor.enum-max", DEFAULT_ENUM_MAX);
        if (enumMax <= 0) {
            logger.warn("enumMax <= 0, using default value.");
            enumMax = DEFAULT_ENUM_MAX;
        }

        threadMax = (int) args.getOrDefault("preprocessor.thread-max", DEFAULT_THREAD_MAX);
        if (threadMax <= 0) {
            logger.warn("threadMax <= 0, using default value");
            threadMax = DEFAULT_THREAD_MAX;
        }

        testCases = new ArrayList<>();

        logger.info("Preprocessor initialized. enumMax={} threadMax={}", enumMax, threadMax);
    }

    /**
     * Read rules from rulesInputDirectory
     * @throws XmlParseException xml parsing error
     */
    public void readRules() throws XmlParseException {
        XmlToRuleParser xmlToRuleParser = new XmlToRuleParser();
        rulesInput = xmlToRuleParser.parseRulesInDirectory(rulesInputDirectory);
        logger.info("Read {} rules from \"{}\".", rulesInput.size(), rulesInputDirectory);
    }

    /**
     * Generate testcases for each rule.
     */
    public void generateTestCases() {
        ExecutorService executorService = Executors.newFixedThreadPool(threadMax);

        for (Rule rule : rulesInput) {
            executorService.submit(() -> generateTestCasesForRule(rule));
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                logger.fatal("Timeout: Some tasks did not finish within the time limit.");
            }
        } catch (InterruptedException e) {
            logger.fatal("Task execution interrupted.", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Generated {} test cases.", testCases.size());
    }

    /**
     * Prepare testcase.
     */
    private void generateTestCasesForRule(Rule rule) {
        String ruleId = rule.getId();

        // Rebind variables to make sure they are unique
        var variables = rule.collectVariables();
        Map<String, String> variableMap = new HashMap<>();
        for (var variable : variables) {
            String newVariable = ruleId + "_" + variable;
            variableMap.put(variable, newVariable);
        }
        rule.rebindVariables(variableMap);

        // Generate random data for each pattern and bind them
        var patterns = rule.collectPatterns();
        Map<String, String> patternMap = new HashMap<>();
        List<Pattern> newPatterns = new ArrayList<>();
        for (var pattern : patterns) {
            String newPattern = ruleId + "_" + pattern;
            patternMap.put(pattern, newPattern);
            Pattern patternObj = new Pattern(newPattern);
            patternObj.setBinaryData();
            newPatterns.add(patternObj);
        }
        rule.rebindPatterns(patternMap);

        // Enumerate bfuncs for rules and bind them
        var bfuncs = rule.collectBfuncs();
        Map<String, List<Bfunc>> bfuncPool = new HashMap<>();
        List<String> bfuncNames = new ArrayList<>();
        for (LiteralBfunc bfunc : bfuncs) {
            int paramNum = bfunc.params.size();
            bfuncNames.add(bfunc.name);
            String newNamePrefix = ruleId + "_" + bfunc.name;
            var bfuncList = Bfunc.enumerateBinaryBfunc(newNamePrefix, paramNum);

            // get param list and add
            List<LiteralBfunc.Param> originalParamList = new ArrayList<>(bfunc.params);
            originalParamList.sort(LiteralBfunc.Param::compareTo);
            List<String> paramList = new ArrayList<>();
            for (var param : originalParamList) {
                paramList.add(param.var);
            }
            for (Bfunc bf : bfuncList) {
                bf.setParamList(paramList);
            }

            bfuncPool.put(bfunc.name, bfuncList);
        }

        // Generate bfunc combinations
        List<List<Bfunc>> combinations = new ArrayList<>();
        enumerateBfuncs(0, bfuncNames, bfuncPool, new ArrayList<>(), combinations);

        combinations = RandomSubset.getRandom(combinations, enumMax);

        // Generate testcases
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
            testCase.setPatterns(newPatterns);
            testCase.setBfuncs(combination);
            synchronized (testCases) {
                testCases.add(testCase);
            }
            count++;
        }
    }

    static private void enumerateBfuncs(int index, List<String> bfuncNames,
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

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public void reduceSize(int size) {
        logger.info("We will use {} / {} testCases.", size, testCases.size());
        testCases = RandomSubset.getRandom(testCases, size);
    }

    public List<TestCase> getBatch(int size) {
        var batch = RandomSubset.getRandom(testCases, size);
        testCases.removeAll(batch);
        return batch;
    }

    public void hasMore() {
        if (testCases.isEmpty()) {
            logger.info("No more testcases.");
        }
    }
}
