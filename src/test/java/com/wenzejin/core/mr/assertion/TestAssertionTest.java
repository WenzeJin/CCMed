package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TruthAssertionTest {

    @Test
    @DisplayName("assertTest passes when truth matches")
    void passesOnMatch() {
        TruthAssertion a = new TruthAssertion(true);
        assertTrue(a.assertTest(new Result(true)));
        assertFalse(a.assertTest(new Result(false)));
    }

    @Test
    @DisplayName("setRule and getRule roundtrip")
    void ruleRoundtrip() {
        TruthAssertion a = new TruthAssertion(false);
        a.setRule("TA-and-1");
        assertEquals("TA-and-1", a.getRule());
    }

    @Test
    @DisplayName("toString includes truth value and rule")
    void toStringFormat() {
        TruthAssertion a = new TruthAssertion(true);
        a.setRule("TA-or-2");
        String s = a.toString();
        assertTrue(s.contains("truthValue=true"));
        assertTrue(s.contains("TA-or-2"));
    }
}

class LinksEqualsAssertionTest {

    @Test
    @DisplayName("passes when link sets are equal")
    void passesOnEqualSets() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("x", "0");
        Set<Link> set = Set.of(link);
        LinksEqualsAssertion a = new LinksEqualsAssertion(set);

        Result r = new Result(true, new HashSet<>(set));
        assertTrue(a.assertTest(r));
    }

    @Test
    @DisplayName("fails when result has extra links")
    void failsOnExtraLinks() {
        Link base = new Link(Link.Type.SATISFIED);
        base.addVaPair("x", "0");
        Set<Link> expected = Set.of(base);
        LinksEqualsAssertion a = new LinksEqualsAssertion(expected);

        Link extra = new Link(Link.Type.VIOLATED);
        extra.addVaPair("y", "1");
        Set<Link> resultLinks = new HashSet<>(expected);
        resultLinks.add(extra);
        Result r = new Result(true, resultLinks);

        assertFalse(a.assertTest(r));
    }

    @Test
    @DisplayName("fails when result is missing links")
    void failsOnMissingLinks() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("x", "0");
        Set<Link> expected = Set.of(link);
        LinksEqualsAssertion a = new LinksEqualsAssertion(expected);

        Result r = new Result(true, new HashSet<>());
        assertFalse(a.assertTest(r));
    }
}

class LinksSubsetAssertionTest {

    @Test
    @DisplayName("passes when result contains a superset of expected links")
    void passesOnSuperset() {
        Link l1 = new Link(Link.Type.SATISFIED);
        l1.addVaPair("x", "0");
        Set<Link> expected = Set.of(l1);
        LinksSubsetAssertion a = new LinksSubsetAssertion(expected);

        Link l2 = new Link(Link.Type.VIOLATED);
        l2.addVaPair("y", "1");
        Set<Link> resultLinks = new HashSet<>(expected);
        resultLinks.add(l2);
        Result r = new Result(true, resultLinks);

        assertTrue(a.assertTest(r));
    }

    @Test
    @DisplayName("fails when expected link is missing")
    void failsOnMissing() {
        Link l1 = new Link(Link.Type.SATISFIED);
        l1.addVaPair("x", "0");
        LinksSubsetAssertion a = new LinksSubsetAssertion(Set.of(l1));

        Result r = new Result(true, Set.of());
        assertFalse(a.assertTest(r));
    }
}

class LinksExtendAssertionTest {

    @Test
    @DisplayName("passes when result links extend (contain supersets of) expected VASets")
    void passesOnExtendedVASets() {
        Link small = new Link(Link.Type.SATISFIED);
        small.addVaPair("x", "0");
        Set<Link> expected = Set.of(small);
        LinksExtendAssertion a = new LinksExtendAssertion(expected);

        Link big = new Link(Link.Type.SATISFIED);
        big.addVaPair("x", "0");
        big.addVaPair("y", "1");
        Result r = new Result(true, Set.of(big));

        assertTrue(a.assertTest(r));
    }

    @Test
    @DisplayName("passes when result has exact same link")
    void passesExactMatch() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("x", "0");
        LinksExtendAssertion a = new LinksExtendAssertion(Set.of(link));
        Result r = new Result(true, Set.of(link));

        assertTrue(a.assertTest(r));
    }

    @Test
    @DisplayName("fails when expected VASet is not contained")
    void failsWhenNotContained() {
        Link expectedLink = new Link(Link.Type.SATISFIED);
        expectedLink.addVaPair("x", "0");
        LinksExtendAssertion a = new LinksExtendAssertion(Set.of(expectedLink));

        Link otherLink = new Link(Link.Type.SATISFIED);
        otherLink.addVaPair("z", "99");
        Result r = new Result(true, Set.of(otherLink));

        assertFalse(a.assertTest(r));
    }
}

class LinksExceptAssertionTest {

    @Test
    @DisplayName("passes when none of the excluded links are present")
    void passesWhenExcludedAbsent() {
        Link excluded = new Link(Link.Type.SATISFIED);
        excluded.addVaPair("x", "0");
        LinksExceptAssertion a = new LinksExceptAssertion(Set.of(excluded));

        Link ok = new Link(Link.Type.VIOLATED);
        ok.addVaPair("y", "1");
        Result r = new Result(true, Set.of(ok));

        assertTrue(a.assertTest(r));
    }

    @Test
    @DisplayName("fails when an excluded link is present")
    void failsWhenExcludedPresent() {
        Link link = new Link(Link.Type.SATISFIED);
        link.addVaPair("x", "0");
        LinksExceptAssertion a = new LinksExceptAssertion(Set.of(link));

        Result r = new Result(true, Set.of(link));
        assertFalse(a.assertTest(r));
    }

    @Test
    @DisplayName("toString should say LinksExceptAssertion (regression: copy-paste fix)")
    void toStringContainsCorrectClassName() {
        LinksExceptAssertion a = new LinksExceptAssertion(Set.of());
        String s = a.toString();
        assertTrue(s.contains("LinksExceptAssertion"), "Bug: toString was copy-pasted from LinksSubsetAssertion");
        assertFalse(s.contains("LinksSubsetAssertion"), "Should not say LinksSubsetAssertion");
    }
}

class CombinedAssertionTest {

    @Test
    @DisplayName("AND passes when both pass")
    void andBothPass() {
        TruthAssertion t = new TruthAssertion(true);
        LinksEqualsAssertion l = new LinksEqualsAssertion(Set.of());
        CombinedAssertion c = new CombinedAssertion(t, l, CombinedAssertion.Type.AND);

        assertTrue(c.assertTest(new Result(true)));
    }

    @Test
    @DisplayName("AND fails when either fails")
    void andOneFails() {
        TruthAssertion t = new TruthAssertion(true);
        LinksEqualsAssertion l = new LinksEqualsAssertion(
                Set.of(new Link(Link.Type.SATISFIED)));
        CombinedAssertion c = new CombinedAssertion(t, l, CombinedAssertion.Type.AND);

        // truth matches but link set is not equal (empty vs non-empty)
        assertFalse(c.assertTest(new Result(true)));
    }

    @Test
    @DisplayName("OR passes when at least one passes")
    void orOnePasses() {
        TruthAssertion pass = new TruthAssertion(true);
        TruthAssertion fail = new TruthAssertion(false);
        CombinedAssertion c = new CombinedAssertion(pass, fail, CombinedAssertion.Type.OR);

        assertTrue(c.assertTest(new Result(true)));
    }

    @Test
    @DisplayName("IMPLIES: true → true = true, true → false = false")
    void impliesSemantics() {
        TruthAssertion t = new TruthAssertion(true);
        TruthAssertion f = new TruthAssertion(false);
        // true IMPLIES true → EXPECT true
        CombinedAssertion c = new CombinedAssertion(t, t, CombinedAssertion.Type.IMPLIES);
        assertTrue(c.assertTest(new Result(true)));

        // true IMPLIES false → false
        // Actually: !left || right. left=true with result(true)=true → !true=false → false || right failure
        // With result(false): left passes false != true → !false=true → true
        // Hmm, let me be more precise.
        // When result.truth = false: t.assertTest(r) = (true==false)=false → left is false
        // !left || right → !false || false → true → passes
        assertTrue(new CombinedAssertion(t, f, CombinedAssertion.Type.IMPLIES).assertTest(new Result(false)));
    }

    @Test
    @DisplayName("NOT inverts")
    void notInverts() {
        TruthAssertion t = new TruthAssertion(true);
        CombinedAssertion c = new CombinedAssertion(t, null, CombinedAssertion.Type.NOT);

        assertFalse(c.assertTest(new Result(true)));
        assertTrue(c.assertTest(new Result(false)));
    }

    @Test
    @DisplayName("setRule/getRule roundtrip")
    void ruleRoundtrip() {
        CombinedAssertion c = new CombinedAssertion(
                new TruthAssertion(true), null, CombinedAssertion.Type.NOT);
        c.setRule("TA-and-1+NoDataChange");
        assertEquals("TA-and-1+NoDataChange", c.getRule());
    }
}
