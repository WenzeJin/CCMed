package com.wenzejin.core.preprocess;

import com.wenzejin.core.exception.XmlParseException;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.LiteralBfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;
import com.wenzejin.core.util.RandomSubset;
import com.wenzejin.core.util.XmlToRuleParser;
import com.wenzejin.core.util.TestCase;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private final List<TestCase> testCases;

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
        if (rulesInputDirectory == null) {
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
            executorService.submit(() -> generateTestCases(rule));
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                logger.fatal("Timeout: Some tasks did not finish within the time limit.");
            }
        } catch (InterruptedException e) {
            logger.fatal("Task execution interrupted.");
        }

        logger.info("Generated {} test cases.", testCases.size());
    }

    /**
     * Prepare testcase.
     */
    private void generateTestCases(Rule rule) {
        String rule_id = rule.getId();

        // Rebind variables to make sure they are unique
        var variables = rule.collectVariables();
        Map<String, String> varMap = new HashMap<>();
        for (var var: variables) {
            String newVar = rule_id + "_" + var;
            varMap.put(var, newVar);
        }
        rule.rebindVariables(varMap);


        // Generate random data for each pattern and bind them
        var patterns = rule.collectPatterns();

        Map<String, String> patMap = new HashMap<>();
        List<Pattern> newPatterns = new ArrayList<>();
        for (var pat: patterns) {
            String newPat = rule_id + "_" + pat;
            patMap.put(pat, newPat);
            Pattern newPattern = new Pattern(newPat);
            newPattern.setBinaryData();
            newPatterns.add(newPattern);
        }
        rule.rebindPatterns(patMap);

        // Enumerate bfuncs for rules and bind them
        var bfuncs = rule.collectBfuncs();
        Map<String, List<Bfunc>> bfuncPool = new HashMap<>();
        List<String> bfuncNames = new ArrayList<>();
        for (LiteralBfunc bfunc : bfuncs) {
            int paramNum = bfunc.params.size();

            bfuncNames.add(bfunc.name);
            String newNamePrefix = rule_id + "_" + bfunc.name;
            var bfuncList = Bfunc.enumerateBinaryBfunc(newNamePrefix, paramNum);

            // get param list and add
            List<LiteralBfunc.Param> paramListOri = new ArrayList<>(bfunc.params);
            paramListOri.sort(LiteralBfunc.Param::compareTo);
            List<String> paramList = new ArrayList<>();
            for (var param: paramListOri) {
                paramList.add(param.var);
            }
            for (Bfunc bf: bfuncList) {
                bf.setParamList(paramList);
            }

            bfuncPool.put(bfunc.name, bfuncList);
        }

        // Generate bfunc combinations
        List<List<Bfunc>> combinations = new ArrayList<>();
        enumerateBfuncs(0, bfuncNames, bfuncPool, new ArrayList<>(), combinations);

        combinations = RandomSubset.getRandom(combinations, enumMax);

        // Generate testcases
        int cnt = 0;
        for (var combination: combinations) {
            Map<String, String> bfuncMap = new HashMap<>();
            for (int i = 0; i < bfuncNames.size(); i++) {
                bfuncMap.put(bfuncNames.get(i), combination.get(i).getName());
            }

            Rule newRule = rule.clone();
            newRule.id = newRule.id + "_" + cnt;
            newRule.rebindBfuncs(bfuncMap);

            TestCase tc = new TestCase(newRule);
            tc.setPatterns(newPatterns);
            tc.setBfuncs(combination);
            synchronized (testCases) {
                testCases.add(tc);
            }
            cnt++;
        }
    }

    static private void enumerateBfuncs(int index, List<String> bfuncName,
                                 Map<String, List<Bfunc>> bfuncPool,
                                 List<Bfunc> current, List<List<Bfunc>> result) {
        if (index == bfuncName.size()) {
            result.add(new ArrayList<>(current));
        } else {
            var bfuncs = bfuncPool.get(bfuncName.get(index));
            for (Bfunc bfunc: bfuncs) {
                current.add(bfunc);
                enumerateBfuncs(index + 1, bfuncName, bfuncPool, current, result);
                current.remove(bfunc);
            }
        }
    }

    /* Getters and Setters */

    public String getRulesInputDirectory() {
        return rulesInputDirectory;
    }

    public void setRulesInputDirectory(String rulesInputDirectory) {
        this.rulesInputDirectory = rulesInputDirectory;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public static void main(String[] args) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("preprocessor.rules-input-directory", "rules/4_level_rule/rule_data");

        Preprocessor preprocessor = new Preprocessor(arguments);
        try {
            preprocessor.readRules();
        } catch (XmlParseException e) {
            e.printStackTrace();
        }
        preprocessor.generateTestCases();
    }
}
