package com.wenzejin.core.mr.crc.rules;

import com.wenzejin.core.exception.NoRuleException;
import com.wenzejin.core.exception.NoTestResultException;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.mr.crc.BinaryTag;
import com.wenzejin.core.mr.crc.Tag;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.util.TestCase;

import java.util.List;
import java.util.Set;

public class TagCreation {

    private static void guardBeforeAll(TestCase tc) throws NoTestResultException {
        if (!tc.isChecked()) {
            throw new NoTestResultException("The test case has not been checked yet.");
        }
    }

    /**
     * Create tags, based on TC rules
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    public static List<Tag> entry(TestCase tc) throws NoTestResultException {
        String nodeType = tc.getRule().formula.getNodeType();
        return switch (nodeType) {
            case "and"      -> and(tc);
            case "or"       -> or(tc);
            case "implies"  -> implies(tc);
            default         -> throw new NoRuleException("The test case is not a valid CRC test case.");
        };
    }

    /**
     * Create tags, based on TC-AND rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> and(TestCase tc) throws NoTestResultException {
        guardBeforeAll(tc);
        Result result = tc.getResult();
        List<Set<Link>> links = CRCFunc.attribution(tc);
        Set<Link> leftLinks = links.get(0);
        Set<Link> rightLinks = links.get(1);

        // self guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("and"))) {
            throw new IllegalStateException("The test case is not a valid TC-AND test case.");
        }

        int index = (result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        Tag leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, leftLinks);
        Tag rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);

        switch (index) {
            case 0: // TC-AND-1
            case 1: // TC-AND-2
            case 2: // TC-AND-3
            case 3: // TC-AND-4
                leftTag = new BinaryTag(BinaryTag.Truth.TRUE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
            case 4: // TC-AND-5
                leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
            case 5: // TC-AND-6
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-AND-7
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
            case 7: // TC-AND-8
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.FALSE, rightLinks);
                break;
        }

        return List.of(leftTag, rightTag);
    }


    /**
     * Create tags, based on TC-OR rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> or(TestCase tc) throws NoTestResultException {
        guardBeforeAll(tc);
        Result result = tc.getResult();
        List<Set<Link>> links = CRCFunc.attribution(tc);
        Set<Link> leftLinks = links.get(0);
        Set<Link> rightLinks = links.get(1);

        // self guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("or"))) {
            throw new IllegalStateException("The test case is not a valid TC-OR test case.");
        }

        int index = (!result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        Tag leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, leftLinks);
        Tag rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);

        switch (index) {
            case 0: // TC-OR-1
            case 1: // TC-OR-2
            case 2: // TC-OR-3
            case 3: // TC-OR-4
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.FALSE, rightLinks);
                break;
            case 4: // TC-OR-5
                leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);
                break;
            case 5: // TC-OR-6
                leftTag = new BinaryTag(BinaryTag.Truth.TRUE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-OR-7
                leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
            case 7: // TC-OR-8
                leftTag = new BinaryTag(BinaryTag.Truth.TRUE, leftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
        }

        return List.of(leftTag, rightTag);
    }

    /**
     * Create tags, based on TC-IMPLIES rules.
     * @param tc test case
     * @return a list of tags, [leftTag, rightTag]
     * @throws NoTestResultException if the test case has not been checked yet
     */
    private static List<Tag> implies(TestCase tc) throws NoTestResultException {
        guardBeforeAll(tc);
        Result result = tc.getResult();
        List<Set<Link>> links = CRCFunc.attribution(tc);
        Set<Link> leftLinks = links.get(0);
        Set<Link> rightLinks = links.get(1);
        Set<Link> flippedLeftLinks = CRCFunc.flipSet(leftLinks);

        // self guard
        if (!(tc.getRule().formula.getNodeType().equalsIgnoreCase("implies"))) {
            throw new IllegalStateException("The test case is not a valid TC-OR test case.");
        }

        int index = (!result.truth ? 0 : 4) + (rightLinks.isEmpty() ? 0 : 2) + (leftLinks.isEmpty() ? 0 : 1);
        Tag leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, flippedLeftLinks);
        Tag rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);

        switch (index) {
            case 0: // TC-IMPLIES-1
            case 1: // TC-IMPLIES-2
            case 2: // TC-IMPLIES-3
            case 3: // TC-IMPLIES-4
                leftTag = new BinaryTag(BinaryTag.Truth.TRUE, flippedLeftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.FALSE, rightLinks);
                break;
            case 4: // TC-IMPLIES-5
                leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, flippedLeftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);
                break;
            case 5: // TC-IMPLIES-6
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, flippedLeftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, rightLinks);
                break;
            case 6: // TC-IMPLIES-7
                leftTag = new BinaryTag(BinaryTag.Truth.UNKNOWN, flippedLeftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
            case 7: // TC-IMPLIES-8
                leftTag = new BinaryTag(BinaryTag.Truth.FALSE, flippedLeftLinks);
                rightTag = new BinaryTag(BinaryTag.Truth.TRUE, rightLinks);
                break;
        }

        return List.of(leftTag, rightTag);
    }
}
