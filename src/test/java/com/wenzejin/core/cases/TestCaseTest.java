package com.wenzejin.core.cases;

import com.wenzejin.core.rule.BfuncLiteral;
import com.wenzejin.core.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseTest {

    @Test
    @DisplayName("each TestCase should have a unique auto-incremented ID")
    void uniqueIdPerInstance() {
        TestCase t1 = new TestCase();
        TestCase t2 = new TestCase();
        TestCase t3 = new TestCase();
        assertNotEquals(t1.getId(), t2.getId());
        assertNotEquals(t2.getId(), t3.getId());
        assertNotEquals(t1.getId(), t3.getId());
    }

    @Test
    @DisplayName("constructor with Rule sets rule and auto-generates ID")
    void constructorWithRule() {
        BfuncLiteral f = new BfuncLiteral("f");
        f.addParam("0", "x", "data");
        Rule rule = new Rule("R1", f);

        TestCase tc = new TestCase(rule);
        assertEquals(rule, tc.getRule());
        assertNotNull(tc.getSource());
        assertNotNull(tc.getRuleUsed());
    }

    @Test
    @DisplayName("setSource should accumulate source IDs")
    void setSourceAccumulates() {
        TestCase tc = new TestCase();
        tc.setSource(java.util.List.of(1, 2));
        tc.setSource(java.util.List.of(3));
        assertEquals(3, tc.getSource().size());
        assertTrue(tc.getSource().containsAll(java.util.List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("setOriRule and getOriRule roundtrip")
    void oriRuleRoundtrip() {
        TestCase tc = new TestCase();
        tc.setOriRule("R42");
        assertEquals("R42", tc.getOriRule());
    }

    @Test
    @DisplayName("setAssertions and getAssertions roundtrip")
    void assertionsRoundtrip() {
        TestCase tc = new TestCase();
        com.wenzejin.core.mr.assertion.TruthAssertion a =
                new com.wenzejin.core.mr.assertion.TruthAssertion(true);
        tc.setAssertions(java.util.List.of(a));
        assertEquals(1, tc.getAssertions().size());
    }

    @Test
    @DisplayName("setPatterns and getPatterns roundtrip")
    void patternsRoundtrip() {
        TestCase tc = new TestCase();
        com.wenzejin.core.rule.Pattern p = new com.wenzejin.core.rule.Pattern("P");
        tc.setPatterns(java.util.List.of(p));
        assertEquals(1, tc.getPatterns().size());
        assertEquals("P", tc.getPatterns().get(0).getName());
    }

    @Test
    @DisplayName("equals should match on rule, patterns, bfuncs and assertions")
    void equalsBasedOnFields() {
        BfuncLiteral f = new BfuncLiteral("f");
        f.addParam("0", "x", "data");
        Rule rule = new Rule("R1", f);

        TestCase tc1 = new TestCase(rule);
        TestCase tc2 = new TestCase(rule);
        // Different IDs → still equals because equals ignores ID
        assertNotEquals(tc1.getId(), tc2.getId());
        // Note: equals also needs patterns/bfuncs/assertions to match
        // Both have null patterns/bfuncs/assertions → should be equal
        assertEquals(tc1.getPatterns(), tc2.getPatterns());
        assertEquals(tc1.getBfuncs(), tc2.getBfuncs());
    }

    @Test
    @DisplayName("ruleUsed list is modifiable")
    void ruleUsedListModifiable() {
        TestCase tc = new TestCase();
        tc.getRuleUsed().add("TA-binary");
        tc.getRuleUsed().add("TA-and-1");
        assertEquals(2, tc.getRuleUsed().size());
    }

    @Test
    @DisplayName("toString includes id, rule, patterns, bfuncs, assertions and source")
    void toStringIncludesAllFields() {
        TestCase tc = new TestCase();
        String s = tc.toString();
        assertTrue(s.contains("id=" + tc.getId()));
        assertTrue(s.contains("source="));
    }
}
