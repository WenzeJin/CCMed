package com.wenzejin.core.mr.ccm.rules;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.ccm.UtilFunctions;
import com.wenzejin.core.mr.ccm.ClosedTag;
import com.wenzejin.core.mr.ccm.OpenTag;
import com.wenzejin.core.mr.ccm.Tag;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.result.TestOutcome;
import com.wenzejin.core.result.ExecutionStatus;
import com.wenzejin.core.rule.OpQuantifier;
import com.wenzejin.core.rule.Pattern;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PUExtraction {

    private static void guardBeforeAll(TestOutcome outcome) throws NoTestResultException {
        if (outcome.status != ExecutionStatus.SUCCESS) {
            throw new NoTestResultException("The test case has not been checked yet.");
        }
    }

    /**
     * Create tags, based on TC rules
     * @param outcome test outcome
     * @return a list of tags, [leftTag, rightTag] (If the test case's root is a quantifier, only one tag is returned)
     * @throws NoTestResultException if the test case has not been checked yet
     */
    public static List<Tag> entry(TestOutcome outcome) throws NoTestResultException {
        TestCase tc = outcome.testCase;
        String nodeType = tc.getRule().formula.getNodeType();
        return switch (nodeType) {
            case "and"      -> and(outcome);
            case "or"       -> or(outcome);
            case "implies"  -> implies(outcome);
            case "forall", "exists" -> List.of(quantifier(outcome));
            default         -> throw new NoRuleException("The test case is not a valid CRC test case.");
        };
    }

    /**
     * Create tags, based on TC-AND rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> and(TestOutcome outcome) throws NoTestResultException {
        guardBeforeAll(outcome);
        TestCase tc = outcome.testCase;
        Result result = outcome.sutResult;
        List<Set<Link>> links = UtilFunctions.attribution(tc, result);
        Set<Link> leftLinks = links.get(0);
        Set<Link> rightLinks = links.get(1);

        // self-guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("and"))) {
            throw new IllegalStateException("The test case is not a valid TC-AND test case.");
        }

        int index = (result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        ClosedTag leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
        ClosedTag rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
        String ruleUsed = "";

        switch (index) {
            case 0: // TC-AND-1
                ruleUsed = "TC-AND-1";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 1: // TC-AND-2
                ruleUsed = "TC-AND-2";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 2: // TC-AND-3
                ruleUsed = "TC-AND-3";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 3: // TC-AND-4
                ruleUsed = "TC-AND-4";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 4: // TC-AND-5
                ruleUsed = "TC-AND-5";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 5: // TC-AND-6
                ruleUsed = "TC-AND-6";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-AND-7
                ruleUsed = "TC-AND-7";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 7: // TC-AND-8
                ruleUsed = "TC-AND-8";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
        }
        leftTag.ruleUsed = ruleUsed;
        rightTag.ruleUsed = ruleUsed;
        leftTag.addSource(tc.getId());
        rightTag.addSource(tc.getId());
        return List.of(leftTag, rightTag);
    }


    /**
     * Create tags, based on TC-OR rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> or(TestOutcome outcome) throws NoTestResultException {
        guardBeforeAll(outcome);
        TestCase tc = outcome.testCase;
        Result result = outcome.sutResult;
        List<Set<Link>> links = UtilFunctions.attribution(tc, result);
        Set<Link> leftLinks = links.get(0);
        Set<Link> rightLinks = links.get(1);

        // self-guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("or"))) {
            throw new IllegalStateException("The test case is not a valid TC-OR test case.");
        }

        int index = (!result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        ClosedTag leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
        ClosedTag rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
        String ruleUsed = "";

        switch (index) {
            case 0: // TC-OR-1
                ruleUsed = "TC-OR-1";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 1: // TC-OR-2
                ruleUsed = "TC-OR-2";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 2: // TC-OR-3
                ruleUsed = "TC-OR-3";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 3: // TC-OR-4
                ruleUsed = "TC-OR-4";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 4: // TC-OR-5
                ruleUsed = "TC-OR-5";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 5: // TC-OR-6
                ruleUsed = "TC-OR-6";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-OR-7
                ruleUsed = "TC-OR-7";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 7: // TC-OR-8
                ruleUsed = "TC-OR-8";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, leftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
        }
        leftTag.ruleUsed = ruleUsed;
        rightTag.ruleUsed = ruleUsed;
        leftTag.addSource(tc.getId());
        rightTag.addSource(tc.getId());

        return List.of(leftTag, rightTag);
    }

    /**
     * Create tags, based on TC-IMPLIES rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> implies(TestOutcome outcome) throws NoTestResultException {
        guardBeforeAll(outcome);
        TestCase tc = outcome.testCase;
        Result result = outcome.sutResult;
        List<Set<Link>> links = UtilFunctions.attribution(tc, result);
        Set<Link> leftLinks = new HashSet<>(links.get(0));
        Set<Link> rightLinks = new HashSet<>(links.get(1));
        Set<Link> flippedLeftLinks = UtilFunctions.flipSet(leftLinks);

        // self-guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("implies"))) {
            throw new IllegalStateException("The test case is not a valid TC-OR test case.");
        }

        int index = (!result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        ClosedTag leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, flippedLeftLinks);
        ClosedTag rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
        String ruleUsed = "";

        switch (index) {
            case 0: // TC-IMPLIES-1
                ruleUsed = "TC-IMPLIES-1";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 1: // TC-IMPLIES-2
                ruleUsed = "TC-IMPLIES-2";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 2: // TC-IMPLIES-3
                ruleUsed = "TC-IMPLIES-3";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 3: // TC-IMPLIES-4
                ruleUsed = "TC-IMPLIES-4";
                leftTag = new ClosedTag(ClosedTag.Truth.TRUE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.FALSE, rightLinks);
                break;
            case 4: // TC-IMPLIES-5
                ruleUsed = "TC-IMPLIES-5";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 5: // TC-IMPLIES-6
                ruleUsed = "TC-IMPLIES-6";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-IMPLIES-7
                ruleUsed = "TC-IMPLIES-7";
                leftTag = new ClosedTag(ClosedTag.Truth.UNKNOWN, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
            case 7: // TC-IMPLIES-8
                ruleUsed = "TC-IMPLIES-8";
                leftTag = new ClosedTag(ClosedTag.Truth.FALSE, flippedLeftLinks);
                rightTag = new ClosedTag(ClosedTag.Truth.TRUE, rightLinks);
                break;
        }

        leftTag.ruleUsed = ruleUsed;
        rightTag.ruleUsed = ruleUsed;
        leftTag.addSource(tc.getId());
        rightTag.addSource(tc.getId());

        return List.of(leftTag, rightTag);
    }

    private static Tag quantifier(TestOutcome outcome) throws NoTestResultException {
        guardBeforeAll(outcome);
        TestCase tc = outcome.testCase;
        Result result = outcome.sutResult;
        OpQuantifier formula = (OpQuantifier) tc.getRule().formula;

        if (formula == null) {
            throw new IllegalStateException("The test case is not a valid TC-FORALL test case.");
        }

        String varName = formula.var;
        String patternName = formula.pattern;
        Pattern pattern = null;
        for (Pattern p: tc.getPatterns()) {
            if (p.getName().equals(patternName)) {
                pattern = p;
                break;
            }
        }

        if (pattern == null) {
            throw new IllegalStateException("The pattern is not found in the test case.");
        }

        Set<String> allValue = new HashSet<>(pattern.getData());
        Set<String> valTrue = UtilFunctions.val(result.links, true, varName);
        Set<String> valFalse = UtilFunctions.val(result.links, false, varName);
        OpenTag tag = new OpenTag(varName);

        if (formula.getNodeType().equalsIgnoreCase("forall")) {
            if (result.truth) {
                // TC-FORALL-1
                tag.valueTrue = allValue;
                tag.ruleUsed = "TC-FORALL-1";
                tag.addSource(tc.getId());
                return tag;
            } else {
                // TC-FORALL-2
                allValue.removeAll(valFalse);
                tag.valueTrue = allValue;
                tag.valueFalse = valFalse;
                tag.ruleUsed = "TC-FORALL-2";
                tag.addSource(tc.getId());
                return tag;
            }
        } else {
            if (result.truth) {
                // TC-EXISTS-1
                tag.valueTrue = valTrue;
                allValue.removeAll(valTrue);
                tag.valueFalse = allValue;
                tag.ruleUsed = "TC-EXISTS-1";
                tag.addSource(tc.getId());
                return tag;
            } else {
                // TC-EXISTS-2
                tag.valueFalse = allValue;
                tag.ruleUsed = "TC-EXISTS-2";
                tag.addSource(tc.getId());
                return tag;
            }
        }

    }
}
