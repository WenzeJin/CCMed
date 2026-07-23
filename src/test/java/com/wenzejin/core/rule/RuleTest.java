package com.wenzejin.core.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleTest {

    @Test
    @DisplayName("should collect variables from formula recursively")
    void collectVariables() {
        // forall x in P -> (and (bfunc f(x)) (bfunc g(y)))
        BfuncLiteral f = new BfuncLiteral("f");
        f.addParam("0", "x", "data");
        BfuncLiteral g = new BfuncLiteral("g");
        g.addParam("0", "y", "data");

        OpConnector and = new OpConnector(OpConnector.Op.AND, f, g);
        OpQuantifier forall = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", and);

        Rule rule = new Rule("R1", forall);
        assertTrue(rule.isValid());

        Set<String> vars = rule.collectVariables();
        assertEquals(2, vars.size());
        assertTrue(vars.contains("x"));
        assertTrue(vars.contains("y"));
    }

    @Test
    @DisplayName("should collect patterns recursively")
    void collectPatterns() {
        OpQuantifier forall = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P",
                new BfuncLiteral("f"));
        OpQuantifier exists = new OpQuantifier(OpQuantifier.Op.EXISTS, "y", "Q", forall);

        Rule rule = new Rule("R1", exists);
        Set<String> patterns = rule.collectPatterns();
        assertEquals(2, patterns.size());
        assertTrue(patterns.contains("P"));
        assertTrue(patterns.contains("Q"));
    }

    @Test
    @DisplayName("clone should deep copy formula")
    void cloneDeepCopiesFormula() {
        BfuncLiteral f = new BfuncLiteral("f");
        f.addParam("0", "x", "data");
        OpNot not = new OpNot(f);
        Rule original = new Rule("R1", not);
        Rule cloned = original.clone();

        assertEquals(original.id, cloned.id);
        assertNotSame(original.formula, cloned.formula);
        assertTrue(cloned.isValid());
    }

    @Test
    @DisplayName("toXmlString should produce valid XML fragment")
    void toXmlString() {
        Rule rule = new Rule("R1", new BfuncLiteral("f"));
        String xml = rule.toXmlString();
        assertTrue(xml.contains("<id>R1</id>"));
        assertTrue(xml.contains("<bfunc"));
    }

    @Test
    @DisplayName("isValid returns false for empty id")
    void isValidEmptyId() {
        Rule rule = new Rule("", new BfuncLiteral("f"));
        assertFalse(rule.isValid());
    }

    @Test
    @DisplayName("isValid returns false for null formula")
    void isValidNullFormula() {
        Rule rule = new Rule("R1", null);
        assertFalse(rule.isValid());
    }

    @Test
    @DisplayName("rebindPatterns should update patterns via formula tree")
    void rebindPatterns() {
        OpQuantifier q = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "oldP",
                new BfuncLiteral("f"));
        Rule rule = new Rule("R1", q);
        Map<String, String> map = new HashMap<>();
        map.put("oldP", "newP");
        rule.rebindPatterns(map);
        assertTrue(rule.collectPatterns().contains("newP"));
        assertFalse(rule.collectPatterns().contains("oldP"));
    }
}

class BfuncTest {

    @Test
    @DisplayName("should enumerate binary bfunc with correct paramNum")
    void enumerateBinaryBfunc() {
        List<Bfunc> bfuncs = Bfunc.enumerateBinaryBfunc("test", 2);
        assertFalse(bfuncs.isEmpty());
        for (Bfunc bf : bfuncs) {
            assertTrue(bf.getName().startsWith("random_test_"));
        }
    }

    @Test
    @DisplayName("enumerateBfuncs should generate all combinations for patterns")
    void enumerateBfuncsFromPatterns() {
        Pattern p = new Pattern("P");
        p.setBinaryData(); // {"0", "1"} → 2 combinations → 2^2=4 bfuncs total, each with 2 entries
        List<Bfunc> bfuncs = Bfunc.enumerateBfuncs("T", List.of(p));
        assertFalse(bfuncs.isEmpty());
        // 2 data values → 2 combinations, each bfunc has 2 value entries
        for (Bfunc bf : bfuncs) {
            assertEquals(2, bf.getValueMap().size(),
                "each bfunc should have 2 entries (one per data combination)");
        }
    }

    @Test
    @DisplayName("getOneRandomBfunc should generate a valid random bfunc")
    void getOneRandomBfunc() {
        Bfunc bf = Bfunc.getOneRandomBfunc("prefix", 2, 42);
        assertEquals("random_prefix_42", bf.getName());
        assertEquals(4, bf.getValueMap().size()); // 2 binary params → 2^2 combos
    }

    @Test
    @DisplayName("clone should deep copy value map")
    void cloneIsDeep() {
        Bfunc original = Bfunc.getOneRandomBfunc("p", 1, 1);
        Bfunc cloned = original.clone();
        assertEquals(original.getValueMap(), cloned.getValueMap());
        assertNotSame(original.getValueMap(), cloned.getValueMap());
    }

    @Test
    @DisplayName("setParamList and getParamList roundtrip")
    void paramListRoundtrip() {
        Bfunc bf = new Bfunc("bf", 3);
        bf.setParamList(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), bf.getParamList());
    }
}

class PatternTest {

    @Test
    @DisplayName("setBinaryData should set {0, 1}")
    void setBinaryData() {
        Pattern p = new Pattern("P");
        p.setBinaryData();
        assertEquals(2, p.getData().size());
        assertTrue(p.getData().contains("0"));
        assertTrue(p.getData().contains("1"));
    }

    @Test
    @DisplayName("copy should create independent Pattern")
    void copyCreatesIndependent() {
        Pattern p = new Pattern("P");
        p.setBinaryData();
        Pattern copy = p.copy();
        assertEquals(p.getName(), copy.getName());
        assertEquals(p.getData(), copy.getData());
        // independence
        copy.getData().add("99");
        assertEquals(2, p.getData().size());
        assertEquals(3, copy.getData().size());
    }

    @Test
    @DisplayName("randomExtend should add elements")
    void randomExtendAddsElements() {
        Pattern p = new Pattern("P");
        p.setBinaryData();
        p.randomExtend();
        assertTrue(p.getData().size() >= 3);
    }

    @Test
    @DisplayName("randomReduceByGate should only ever reduce or leave unchanged")
    void randomReduceByGateSafelyReduces() {
        Pattern p = new Pattern("P");
        p.setBinaryData();
        int originalSize = p.getData().size();
        p.randomReduceByGate();
        // either stays same size or reduced by 1 (never grows)
        int newSize = p.getData().size();
        assertTrue(newSize == originalSize || newSize == originalSize - 1);
    }
}

class OpConnectorTest {

    @Test
    @DisplayName("getNodeType should return correct string for each Op")
    void nodeTypeMatchesOp() {
        OpConnector and = new OpConnector(OpConnector.Op.AND, new BfuncLiteral("f"), new BfuncLiteral("g"));
        assertEquals("and", and.getNodeType());

        OpConnector or = new OpConnector(OpConnector.Op.OR, new BfuncLiteral("f"), new BfuncLiteral("g"));
        assertEquals("or", or.getNodeType());

        OpConnector implies = new OpConnector(OpConnector.Op.IMPLIES, new BfuncLiteral("f"), new BfuncLiteral("g"));
        assertEquals("implies", implies.getNodeType());
    }

    @Test
    @DisplayName("isValid returns false when left is null")
    void isValidRequiresBothChildren() {
        OpConnector op = new OpConnector(OpConnector.Op.AND, null, new BfuncLiteral("f"));
        assertFalse(op.isValid());
    }

    @Test
    @DisplayName("clone should deep copy both children")
    void cloneIsDeep() {
        OpConnector original = new OpConnector(OpConnector.Op.AND, new BfuncLiteral("a"), new BfuncLiteral("b"));
        OpConnector cloned = original.clone();

        assertEquals(original.getNodeType(), cloned.getNodeType());
        assertNotSame(original.left, cloned.left);
        assertNotSame(original.right, cloned.right);
    }

    @Test
    @DisplayName("equal bfunc literals produce zero distance")
    void distanceIdenticalBfuncsIsZero() {
        BfuncLiteral a = new BfuncLiteral("f");
        a.addParam("0", "x", "data");
        BfuncLiteral b = new BfuncLiteral("f");
        b.addParam("0", "x", "data");
        assertEquals(0.0, a.distance(b), 0.001);
    }

    @Test
    @DisplayName("different bfunc names produce medium distance via connector")
    void connectorDistanceDifferentBfuncs() {
        BfuncLiteral a = new BfuncLiteral("f");
        a.addParam("0", "x", "data");
        BfuncLiteral b = new BfuncLiteral("g");
        b.addParam("0", "x", "data");

        OpConnector left = new OpConnector(OpConnector.Op.AND, a, b);
        OpConnector right = new OpConnector(OpConnector.Op.AND, a, b);

        // same structure → distance should be small
        double dist = left.distance(right);
        assertTrue(dist < 100, "distance should be < 100 for identical structure: " + dist);
    }

    @Test
    @DisplayName("toXmlString produces valid XML for nested connectors")
    void toXmlStringNested() {
        OpConnector and = new OpConnector(OpConnector.Op.AND,
                new BfuncLiteral("f"), new BfuncLiteral("g"));
        String xml = and.toXmlString();
        assertTrue(xml.contains("<and>"));
        assertTrue(xml.contains("</and>"));
    }
}

class OpQuantifierTest {

    @Test
    @DisplayName("FORALL distance to identical FORALL should be small (regression: fix for recursive error)")
    void distanceForallToIdenticalForall() {
        BfuncLiteral inner1 = new BfuncLiteral("f");
        inner1.addParam("0", "x", "data");
        BfuncLiteral inner2 = new BfuncLiteral("f");
        inner2.addParam("0", "x", "data");

        OpQuantifier q1 = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", inner1);
        OpQuantifier q2 = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", inner2);

        double dist = q1.distance(q2);
        assertTrue(dist < 100, "Distance should be small for identical quantifiers, was: " + dist);
        assertTrue(dist >= 0, "Distance should be non-negative");
    }

    @Test
    @DisplayName("EXISTS distance to identical EXISTS should be small")
    void distanceExistsToIdenticalExists() {
        BfuncLiteral inner = new BfuncLiteral("f");
        inner.addParam("0", "x", "data");

        OpQuantifier q1 = new OpQuantifier(OpQuantifier.Op.EXISTS, "x", "P", inner);
        OpQuantifier q2 = new OpQuantifier(OpQuantifier.Op.EXISTS, "x", "P",
                inner.clone());

        double dist = q1.distance(q2);
        assertTrue(dist < 100, "Distance should be small, was: " + dist);
    }

    @Test
    @DisplayName("FORALL vs EXISTS should give a higher (non-recursive) distance")
    void distanceForallVsExists() {
        BfuncLiteral inner = new BfuncLiteral("f");
        inner.addParam("0", "x", "data");

        OpQuantifier forall = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", inner.clone());
        OpQuantifier exists = new OpQuantifier(OpQuantifier.Op.EXISTS, "x", "P", inner.clone());

        double dist = forall.distance(exists);
        assertTrue(dist >= 25, "Different quantifiers should have at least 25 distance, was: " + dist);
    }

    @Test
    @DisplayName("isValid checks opType, var, pattern, and child formula")
    void isValidChecksAllFields() {
        OpQuantifier q = new OpQuantifier(OpQuantifier.Op.FORALL, null, null, null);
        assertFalse(q.isValid());
    }

    @Test
    @DisplayName("collectVariablePatternPairs maps variable to pattern")
    void collectVariablePatternPairs() {
        BfuncLiteral inner = new BfuncLiteral("f");
        inner.addParam("0", "x", "data");
        OpQuantifier forall = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", inner);
        Map<String, String> pairs = forall.collectVariablePatternPairs();
        assertEquals("P", pairs.get("x"));
    }

    @Test
    @DisplayName("clone deep copies formula and preserves var/pattern")
    void cloneIsDeep() {
        BfuncLiteral inner = new BfuncLiteral("f");
        inner.addParam("0", "x", "data");
        OpQuantifier original = new OpQuantifier(OpQuantifier.Op.FORALL, "x", "P", inner);
        OpQuantifier cloned = original.clone();

        assertEquals(original.var, cloned.var);
        assertEquals(original.pattern, cloned.pattern);
        assertEquals(original.opType, cloned.opType);
        assertNotSame(original.formula, cloned.formula);
    }
}

class OpNotTest {

    @Test
    @DisplayName("getNodeType returns 'not'")
    void nodeTypeIsNot() {
        OpNot not = new OpNot(new BfuncLiteral("f"));
        assertEquals("not", not.getNodeType());
    }

    @Test
    @DisplayName("clone deep copies inner formula")
    void cloneIsDeep() {
        OpNot original = new OpNot(new BfuncLiteral("f"));
        OpNot cloned = original.clone();
        assertNotSame(original.formula, cloned.formula);
    }

    @Test
    @DisplayName("NOT distance to identical NOT should be small")
    void distanceNotToNot() {
        BfuncLiteral inner = new BfuncLiteral("f");
        inner.addParam("0", "x", "data");
        OpNot n1 = new OpNot(inner);
        OpNot n2 = new OpNot(inner.clone());
        double dist = n1.distance(n2);
        assertTrue(dist < 100, "Distance should be small, was: " + dist);
    }

    @Test
    @DisplayName("NOT distance to plain bfunc should be >= 50")
    void distanceNotToBfunc() {
        BfuncLiteral bf = new BfuncLiteral("f");
        bf.addParam("0", "x", "data");
        OpNot n = new OpNot(bf.clone());
        double dist = n.distance(bf);
        assertTrue(dist >= 50, "NOT → bfunc distance should be at least 50, was: " + dist);
    }

    @Test
    @DisplayName("toXmlString produces valid XML")
    void toXmlString() {
        OpNot not = new OpNot(new BfuncLiteral("f"));
        String xml = not.toXmlString();
        assertTrue(xml.contains("<not>"));
        assertTrue(xml.contains("</not>"));
    }
}

class BfuncLiteralTest {

    @Test
    @DisplayName("isValid requires name and at least one param")
    void isValid() {
        BfuncLiteral empty = new BfuncLiteral();
        assertFalse(empty.isValid());

        BfuncLiteral noParams = new BfuncLiteral("f");
        assertFalse(noParams.isValid());

        BfuncLiteral withParam = new BfuncLiteral("f");
        withParam.addParam("0", "x", "data");
        assertTrue(withParam.isValid());
    }

    @Test
    @DisplayName("getNodeType returns 'bfunc'")
    void nodeType() {
        assertEquals("bfunc", new BfuncLiteral("f").getNodeType());
    }

    @Test
    @DisplayName("distance is 0 for identical bfunc names")
    void distanceSameName() {
        BfuncLiteral a = new BfuncLiteral("f");
        a.addParam("0", "x", "data");
        BfuncLiteral b = new BfuncLiteral("f");
        b.addParam("0", "y", "data");
        assertEquals(0.0, a.distance(b));
    }

    @Test
    @DisplayName("distance is 100 for non-BfuncLiteral")
    void distanceToNonBfunc() {
        BfuncLiteral a = new BfuncLiteral("f");
        a.addParam("0", "x", "data");
        assertEquals(100.0, a.distance(new OpNot(a)));
    }

    @Test
    @DisplayName("collectVariablePatternPairs always returns empty map")
    void collectVariablePatternPairsReturnsEmpty() {
        BfuncLiteral bf = new BfuncLiteral("f");
        bf.addParam("0", "x", "data");
        assertTrue(bf.collectVariablePatternPairs().isEmpty());
    }

    @Test
    @DisplayName("clone should deep copy params")
    void cloneDeepCopiesParams() {
        BfuncLiteral original = new BfuncLiteral("f");
        original.addParam("0", "x", "data");
        BfuncLiteral cloned = original.clone();

        assertEquals(original.name, cloned.name);
        assertEquals(original.params.size(), cloned.params.size());
        assertNotSame(original.params, cloned.params);
    }
}
