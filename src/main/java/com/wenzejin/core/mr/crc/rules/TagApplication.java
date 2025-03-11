package com.wenzejin.core.mr.crc.rules;

import com.wenzejin.core.mr.assertion.*;
import com.wenzejin.core.mr.crc.BinaryTag;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.mr.crc.FormulaWithTag;
import com.wenzejin.core.mr.crc.QuantifierTag;
import com.wenzejin.core.mr.crc.assertion.LinksValueAssertion;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.rule.*;
import com.wenzejin.core.util.TestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class TagApplication {

    private static final AtomicInteger idCounter = new AtomicInteger();

    public static TestCase binaryEntry(FormulaWithTag left, FormulaWithTag right, String newOp) {
        Formula leftFormula = left.formula.clone();
        Formula rightFormula = right.formula.clone();
        Formula gen = null;
        List<TestAssertion> assertions = null;

        if (!(left.tag instanceof BinaryTag) || !(right.tag instanceof BinaryTag)) {
            // Invalid tag
            return null;
        }

        switch (newOp) {
            case "and":
                gen = new OpConnector(OpConnector.Op.AND, leftFormula, rightFormula);
                assertions = and((BinaryTag) left.tag, (BinaryTag) right.tag);
                break;
            case "or":
                gen = new OpConnector(OpConnector.Op.OR, leftFormula, rightFormula);
                assertions = or((BinaryTag) left.tag, (BinaryTag) right.tag);
                break;
            case "implies":
                gen = new OpConnector(OpConnector.Op.IMPLIES, leftFormula, rightFormula);
                assertions = implies((BinaryTag) left.tag, (BinaryTag) right.tag);
                break;
            default:
                break;
        }

        if (assertions == null || assertions.isEmpty()) {
            // Invalid combination
            return null;
        }
        Rule genRule = new Rule("CRC-Gen-" + idCounter.incrementAndGet(), gen);
        List<Pattern> patterns = new ArrayList<>();
        patterns.addAll(left.patterns);
        patterns.addAll(right.patterns);
        List<Bfunc> bfuncs = new ArrayList<>();
        bfuncs.addAll(left.bfuncs);
        bfuncs.addAll(right.bfuncs);

        TestCase genTC = new TestCase(genRule);
        genTC.setPatterns(patterns);
        genTC.setBfuncs(bfuncs);
        genTC.setAssertions(assertions);
        genTC.getSource().add(left.parentTestCase.getId());
        genTC.getSource().add(right.parentTestCase.getId());

        for (TestAssertion assertion : assertions) {
            genTC.getRuleUsed().add(assertion.getRule());
        }

        genTC.getRuleUsed().add(((BinaryTag) left.tag).ruleUsed);
        genTC.getRuleUsed().add(((BinaryTag) right.tag).ruleUsed);

        return genTC;
    }

    public static TestCase quantifierEntry(FormulaWithTag f, String newOp, boolean expectTruth) {
        Formula ori = f.formula;
        Formula gen = null;
        List<TestAssertion> assertions = null;
        if (!(f.tag instanceof QuantifierTag)) {
            // Invalid tag
            return null;
        }

        String newName = "CRC-Gen-" + idCounter.incrementAndGet();
        String newPatName = newName + "-Pattern";
        Pattern newPattern = null;

        QuantifierTag tag = (QuantifierTag) f.tag;

        switch (newOp) {
            case "forall":
                if (expectTruth) {
                    newPattern = new Pattern(newPatName, tag.valueTrue);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    newPattern.randomReduceByGate();
                } else {
                    newPattern = new Pattern(newPatName, tag.valueFalse);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    newPattern.randomExtendByGate();
                }
                gen = new OpQuantifier(OpQuantifier.Op.FORALL, tag.variable, newPatName, ori);
                assertions = forall(tag, newPattern.getData(), expectTruth);
                break;
            case "exists":
                if (expectTruth) {
                    newPattern = new Pattern(newPatName, tag.valueTrue);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    newPattern.randomExtendByGate();
                } else {
                    newPattern = new Pattern(newPatName, tag.valueFalse);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    newPattern.randomReduceByGate();
                }
                gen = new OpQuantifier(OpQuantifier.Op.EXISTS, tag.variable, newPatName, ori);
                assertions = exists(tag, newPattern.getData(), expectTruth);
                break;
            default:
                break;
        }

        if (assertions == null || assertions.isEmpty()) {
            // Invalid combination
            return null;
        }


        Rule genRule = new Rule(newName, gen);
        List<Pattern> patterns = new ArrayList<>();
        patterns.addAll(f.patterns);
        patterns.add(newPattern);
        List<Bfunc> bfuncs = new ArrayList<>();
        bfuncs.addAll(f.bfuncs);

        TestCase genTC = new TestCase(genRule);
        genTC.setPatterns(patterns);
        genTC.setBfuncs(bfuncs);
        genTC.setAssertions(assertions);

        for (TestAssertion assertion : assertions) {
            genTC.getRuleUsed().add(assertion.getRule());
        }
        String[] rulesUsed = tag.ruleUsed.split(",");
        for (String rule : rulesUsed) {
            genTC.getRuleUsed().add(rule);
        }
        String[] parentIDs = tag.parentID.split(",");
        for (String parentID : parentIDs) {
            genTC.getSource().add(Integer.parseInt(parentID));
        }

        return genTC;
    }


    private static List<TestAssertion> and(BinaryTag left, BinaryTag right) {
        List<TestAssertion> assertions = new ArrayList<>();
        String usedRule = "";
        if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-and-1
            usedRule = "TA-and-1";
            assertions.add(new TruthAssertion(true));
            Set<Link> cartesian = CRCFunc.cartesian(left.links, right.links);
            if (!cartesian.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(cartesian));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-and-2-a
            usedRule = "TA-and-2";
            assertions.add(new TruthAssertion(false));
            if (!right.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(right.links));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-and-3
            usedRule = "TA-and-3";
            assertions.add(new TruthAssertion(false));
            Set<Link> union = CRCFunc.union(left.links, right.links);
            if (!union.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(union));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-and-4-a
            usedRule = "TA-and-4";
            if (!left.links.isEmpty()) {
                TestAssertion pre = new TruthAssertion(false);
                TestAssertion after = new LinksExceptAssertion(CRCFunc.flipSet(left.links));
                TestAssertion cond = new CombinedAssertion(pre, after, CombinedAssertion.Type.IMPLIES);
                assertions.add(cond);
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-and-5-a
            usedRule = "TA-and-5";
            if (!left.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(left.links));
            }
        } else if (left.truth == BinaryTag.Truth.UNKNOWN && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-and-6 no assertion pass
            usedRule = "TA-and-6";
        } else {
            // try switch to match some rules
            assertions.addAll(and(right,left));
        }
        if (!usedRule.isEmpty()) {
            for (TestAssertion assertion : assertions) {
                assertion.setRule(usedRule);
            }
        }
        return assertions;
    }

    private static List<TestAssertion> or(BinaryTag left, BinaryTag right) {
        List<TestAssertion> assertions = new ArrayList<>();
        String usedRule = "";
        if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-or-1
            usedRule = "TA-or-1";
            assertions.add(new TruthAssertion(true));
            Set<Link> union = CRCFunc.union(left.links, right.links);
            if (!union.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(union));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-or-2-a
            usedRule = "TA-or-2";
            assertions.add(new TruthAssertion(true));
            if (!left.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(left.links));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-or-3
            usedRule = "TA-or-3";
            assertions.add(new TruthAssertion(false));
            Set<Link> cartesian = CRCFunc.cartesian(left.links, right.links);
            if (!cartesian.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(cartesian));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-or-4-a
            usedRule = "TA-or-4";
            if (!left.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(left.links));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-or-5-a
            usedRule = "TA-or-5";
            if (!left.links.isEmpty()) {
                TestAssertion pre = new TruthAssertion(true);
                TestAssertion after = new LinksExceptAssertion(CRCFunc.flipSet(left.links));
                TestAssertion cond = new CombinedAssertion(pre, after, CombinedAssertion.Type.IMPLIES);
                assertions.add(cond);
            }
        } else if (left.truth == BinaryTag.Truth.UNKNOWN && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-or-6 no assertion pass
            usedRule = "TA-or-6";
        } else {
            // try switch to match some rules
            assertions.addAll(or(right,left));
        }
        if (!usedRule.isEmpty()) {
            for (TestAssertion assertion : assertions) {
                assertion.setRule(usedRule);
            }
        }
        return assertions;
    }

    private static List<TestAssertion> implies(BinaryTag left, BinaryTag right) {
        List<TestAssertion> assertions = new ArrayList<>();
        String usedRule = "";
        if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-implies-1
            usedRule = "TA-implies-1";
            assertions.add(new TruthAssertion(true));
            if (!right.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(right.links));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-implies-2
            usedRule = "TA-implies-2";
            assertions.add(new TruthAssertion(false));
            Set<Link> cartesian = CRCFunc.cartesian(CRCFunc.flipSet(left.links), right.links);
            if (!cartesian.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(cartesian));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.TRUE) {
            // TA-implies-3
            usedRule = "TA-implies-3";
            assertions.add(new TruthAssertion(true));
            Set<Link> union = CRCFunc.union(CRCFunc.flipSet(left.links), right.links);
            if (!union.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(union));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.FALSE) {
            // TA-implies-4
            usedRule = "TA-implies-4";
            assertions.add(new TruthAssertion(true));
            if (!left.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(CRCFunc.flipSet(left.links)));
            }
        } else if (left.truth == BinaryTag.Truth.TRUE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-implies-5
            usedRule = "TA-implies-5";
            if (!left.links.isEmpty()) {
                TestAssertion pre = new TruthAssertion(false);
                TestAssertion after = new LinksExceptAssertion(left.links);
                TestAssertion cond = new CombinedAssertion(pre, after, CombinedAssertion.Type.IMPLIES);
                assertions.add(cond);
            }
        } else if (left.truth == BinaryTag.Truth.UNKNOWN && right.truth == BinaryTag.Truth.TRUE) {
            // TA-implies-6
            usedRule = "TA-implies-6";
            assertions.add(new TruthAssertion(true));
            if (!right.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(right.links));
            }
        } else if (left.truth == BinaryTag.Truth.FALSE && right.truth == BinaryTag.Truth.UNKNOWN) {
            // TA-implies-7
            usedRule = "TA-implies-7";
            assertions.add(new TruthAssertion(true));
            if (!left.links.isEmpty()) {
                assertions.add(new LinksSubsetAssertion(CRCFunc.flipSet(left.links)));
            }
        } else if (left.truth == BinaryTag.Truth.UNKNOWN && right.truth == BinaryTag.Truth.FALSE) {
            // TA-implies-8
            usedRule = "TA-implies-8";
            if (!right.links.isEmpty()) {
                TestAssertion pre = new TruthAssertion(true);
                TestAssertion after = new LinksExceptAssertion(CRCFunc.flipSet(right.links));
                TestAssertion cond = new CombinedAssertion(pre, after, CombinedAssertion.Type.IMPLIES);
                assertions.add(cond);
            }
        } else {
            // TA-implies-9 no assertions pass
            usedRule = "TA-implies-9";
        }
        for (TestAssertion assertion : assertions) {
            assertion.setRule(usedRule);
        }
        return assertions;
    }

    public static List<TestAssertion> forall(QuantifierTag tag, Set<String> newData, boolean expectTruth) {
        List<TestAssertion> assertions = new ArrayList<>();
        String usedRule = "";
        if (expectTruth) {
            // TA-forall-1
            if (tag.valueTrue.containsAll(newData)) {
                assertions.add(new TruthAssertion(true));
                assertions.add(new LinksEqualsAssertion(new HashSet<>())); // empty set
                usedRule = "TA-forall-1";
            }
        } else {
            // TA-forall-2
            Set<String> both = new HashSet<>(tag.valueFalse);
            both.retainAll(newData);
            if (!both.isEmpty()) {
                assertions.add(new TruthAssertion(false));
                assertions.add(new LinksValueAssertion(tag.variable, false, both));
                usedRule = "TA-forall-2";
            }
        }
        for (TestAssertion assertion : assertions) {
            assertion.setRule(usedRule);
        }
        return assertions;
    }

    public static List<TestAssertion> exists(QuantifierTag tag, Set<String> newData, boolean expectTruth) {
        List<TestAssertion> assertions = new ArrayList<>();
        String usedRule = "";
        if (expectTruth) {
            // TA-exists-1
            Set<String> both = new HashSet<>(tag.valueTrue);
            both.retainAll(newData);
            if (!both.isEmpty()) {
                assertions.add(new TruthAssertion(true));
                assertions.add(new LinksValueAssertion(tag.variable, true, both));
                usedRule = "TA-exists-1";
            }
        } else {
            // TA-exists-2
            if (tag.valueFalse.containsAll(newData)) {
                assertions.add(new TruthAssertion(false));
                assertions.add(new LinksEqualsAssertion(new HashSet<>())); // empty set
                usedRule = "TA-exists-2";
            }
        }
        for (TestAssertion assertion : assertions) {
            assertion.setRule(usedRule);
        }
        return assertions;
    }

}
