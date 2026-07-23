package com.wenzejin.core.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.wenzejin.core.cases.TestCase;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LinkTest {

    @Test
    @DisplayName("should create a SATISFIED link with empty VASet")
    void createSatisfiedLink() {
        Link link = new Link(Link.Type.SATISFIED);
        assertEquals(Link.Type.SATISFIED, link.type);
        assertTrue(link.vaSet.isEmpty());
    }

    @Test
    @DisplayName("should create a VIOLATED link with given VASet")
    void createViolatedLinkWithVASet() {
        Set<VAPair> pairs = new HashSet<>();
        pairs.add(new VAPair("x", "1"));
        Link link = new Link(Link.Type.VIOLATED, pairs);
        assertEquals(Link.Type.VIOLATED, link.type);
        assertEquals(1, link.vaSet.size());
    }

    @Test
    @DisplayName("flip should toggle SATISFIED ↔ VIOLATED")
    void flipTogglesType() {
        Link link = new Link(Link.Type.SATISFIED);
        link.flip();
        assertEquals(Link.Type.VIOLATED, link.type);
        link.flip();
        assertEquals(Link.Type.SATISFIED, link.type);
    }

    @Test
    @DisplayName("addVaPair should add a variable-value pair")
    void addVaPairAddsEntry() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("x", "0");
        assertEquals(1, link.vaSet.size());
        assertTrue(link.vaSet.contains(new VAPair("x", "0")));
    }

    @Test
    @DisplayName("concat should merge two links of same type")
    void concatMergesSameType() {
        Link a = new Link(Link.Type.SATISFIED);
        a.addVaPair("x", "0");
        Link b = new Link(Link.Type.SATISFIED);
        b.addVaPair("y", "1");

        Link merged = a.concat(b);
        assertNotNull(merged);
        assertEquals(Link.Type.SATISFIED, merged.type);
        assertEquals(2, merged.vaSet.size());
    }

    @Test
    @DisplayName("concat should return null for different types")
    void concatReturnsNullForDifferentTypes() {
        Link a = new Link(Link.Type.SATISFIED);
        Link b = new Link(Link.Type.VIOLATED);
        assertNull(a.concat(b));
    }

    @Test
    @DisplayName("clone should create an independent copy")
    void cloneReturnsIndependentCopy() {
        Link original = new Link(Link.Type.SATISFIED);
        original.addVaPair("x", "0");
        Link cloned = original.clone();

        assertEquals(original.type, cloned.type);
        assertEquals(original.vaSet, cloned.vaSet);
        assertNotSame(original.vaSet, cloned.vaSet);

        cloned.addVaPair("y", "1");
        assertEquals(2, cloned.vaSet.size());
        assertEquals(1, original.vaSet.size());
    }

    @Test
    @DisplayName("equals should compare type and vaSet correctly")
    void equalsAndHashCode() {
        Link a = new Link(Link.Type.SATISFIED);
        a.addVaPair("x", "0");
        Link b = new Link(Link.Type.SATISFIED);
        b.addVaPair("x", "0");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("rebindVariables should rename variables in VASet")
    void rebindVariablesRenamesKeys() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("old_x", "0");
        link.addVaPair("old_y", "1");

        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("old_x", "new_x");
        link.rebindVariables(map);

        Set<String> vars = link.collectVariables();
        assertTrue(vars.contains("new_x"));
        assertFalse(vars.contains("old_x"));
        assertTrue(vars.contains("old_y")); // not remapped
    }
}

class VAPairTest {

    @Test
    @DisplayName("should store key and value correctly")
    void storesKeyAndValue() {
        VAPair pair = new VAPair("variable", "1");
        assertEquals("variable", pair.getKey());
        assertEquals("1", pair.getValue());
    }

    @Test
    @DisplayName("setters should update key and value")
    void settersUpdateFields() {
        VAPair pair = new VAPair("x", "0");
        pair.setKey("y");
        pair.setValue("1");
        assertEquals("y", pair.getKey());
        assertEquals("1", pair.getValue());
    }

    @Test
    @DisplayName("equals should match on key and value")
    void equalsMatchesKeyAndValue() {
        VAPair a = new VAPair("x", "0");
        VAPair b = new VAPair("x", "0");
        VAPair c = new VAPair("x", "1");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString should include key and value")
    void toStringIncludesKeyAndValue() {
        VAPair pair = new VAPair("x", "0");
        String s = pair.toString();
        assertTrue(s.contains("x"));
        assertTrue(s.contains("0"));
    }
}

class ResultTest {

    @Test
    @DisplayName("truth-only constructor creates empty links set")
    void truthConstructorEmptyLinks() {
        Result r = new Result(true);
        assertTrue(r.truth);
        assertTrue(r.links.isEmpty());
    }

    @Test
    @DisplayName("full constructor stores truth and links")
    void fullConstructor() {
        Set<Link> links = new HashSet<>();
        links.add(new Link(Link.Type.SATISFIED));
        Result r = new Result(false, links);
        assertFalse(r.truth);
        assertEquals(1, r.links.size());
    }
}

class ExecutionStatusTest {

    @Test
    @DisplayName("should have all five status values")
    void allValuesPresent() {
        ExecutionStatus[] values = ExecutionStatus.values();
        assertEquals(5, values.length);
    }
}

class TestOutcomeTest {

    @Test
    @DisplayName("isPassed returns true only for SUCCESS with no failed assertions")
    void isPassedOnlyForSuccessWithoutFailures() {
        TestCase tc = new com.wenzejin.core.cases.TestCase();

        TestOutcome passed = new TestOutcome(tc, false, ExecutionStatus.SUCCESS, null,
                java.util.Collections.emptyList(), null, 100);
        assertTrue(passed.isPassed());

        TestOutcome crashed = new TestOutcome(tc, false, ExecutionStatus.CRASHED, null,
                java.util.Collections.emptyList(), "boom", 100);
        assertFalse(crashed.isPassed());
    }

    @Test
    @DisplayName("hasSystemError returns true for CRASHED/TIMEOUT/INVALID_OUTPUT")
    void hasSystemErrorDetection() {
        TestCase tc = new com.wenzejin.core.cases.TestCase();

        assertTrue(new TestOutcome(tc, false, ExecutionStatus.CRASHED, null,
                java.util.Collections.emptyList(), null, 0).hasSystemError());
        assertTrue(new TestOutcome(tc, false, ExecutionStatus.TIMEOUT, null,
                java.util.Collections.emptyList(), null, 0).hasSystemError());
        assertTrue(new TestOutcome(tc, false, ExecutionStatus.INVALID_OUTPUT, null,
                java.util.Collections.emptyList(), null, 0).hasSystemError());
        assertFalse(new TestOutcome(tc, false, ExecutionStatus.SUCCESS, null,
                java.util.Collections.emptyList(), null, 0).hasSystemError());
        assertFalse(new TestOutcome(tc, false, ExecutionStatus.SKIPPED, null,
                java.util.Collections.emptyList(), null, 0).hasSystemError());
    }
}
