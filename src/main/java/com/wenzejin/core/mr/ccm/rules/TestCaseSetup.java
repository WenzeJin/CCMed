package com.wenzejin.core.mr.ccm.rules;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.mr.assertion.*;
import com.wenzejin.core.mr.ccm.ClosedTag;
import com.wenzejin.core.mr.ccm.PrimitiveUnit;
import com.wenzejin.core.mr.ccm.OpenTag;
import com.wenzejin.core.mr.assertion.LinksValueAssertion;
import com.wenzejin.core.rule.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestCaseSetup {

    public static boolean simpleMode = false;

    private static final AtomicInteger idCounter = new AtomicInteger();

    public static TestCase binaryFinalize(PrimitiveUnit f) {
        if (f.tag instanceof ClosedTag tag) {
            if (tag.truth == ClosedTag.Truth.UNKNOWN) {
                return null;
            }

            // collect variables which appear in links
            var links = tag.links;

            Set<String> vars = new HashSet<>();
            for (var link : links) {
                vars.addAll(link.collectVariables());
            }

            // randomness injection for patterns
            var patterns = new ArrayList<Pattern>();
            var patMap = new HashMap<String, Pattern>();
            for (var p : f.patterns) {
                var newPat = p.copy();
                patterns.add(newPat);
                patMap.put(p.getName(), newPat);
            }

            String dataType = "+NoDataChange";

            boolean dataExtended = false;

            if (!simpleMode && Math.random() > 0.5) {
                // randomness injection for patterns
                var varToPattern = f.formula.collectVariablePatternPairs();
                for (var v: vars) {
                    if (varToPattern.containsKey(v)) {
                        dataType = "+DataExtension";
                        dataExtended = true;
                        var patName = varToPattern.get(v);
                        var pat = patMap.get(patName);
                        if (pat != null) {

                            pat.randomExtend();
                        }
                    }
                }
            }


            // set assertions
            List<TestAssertion> assertions = new ArrayList<>();
            if (tag.truth == ClosedTag.Truth.TRUE) {
                var assertion = new TruthAssertion(true);
                assertion.setRule(tag.ruleUsed + dataType);
                assertions.add(assertion);
            } else if (tag.truth == ClosedTag.Truth.FALSE) {
                var assertion = new TruthAssertion(false);
                assertion.setRule(tag.ruleUsed + dataType);
                assertions.add(assertion);
            }

            TestAssertion setAssertion = null;
            if (dataExtended) {
                setAssertion = new LinksExtendAssertion(tag.links);
                setAssertion.setRule(tag.ruleUsed + dataType);
            } else {
                setAssertion = new LinksEqualsAssertion(tag.links);
                setAssertion.setRule(tag.ruleUsed + dataType);
            }
            assertions.add(setAssertion);

            // Build rule
            Rule genRule = new Rule("CRC-Gen-" + idCounter.incrementAndGet(), f.formula);
            List<Bfunc> bfuncs = new ArrayList<>(f.bfuncs);

            // Build test case and return
            TestCase genTC = new TestCase(genRule);
            genTC.setPatterns(patterns);
            genTC.setBfuncs(bfuncs);
            genTC.setAssertions(assertions);
            genTC.getRuleUsed().add("TA-binary");
            genTC.getRuleUsed().add(tag.ruleUsed);
            genTC.setSource(tag.getSource());
            return genTC;

        } else {
            return null;
        }
    }

    public static TestCase quantifierEntry(PrimitiveUnit f, String newOp, boolean expectTruth) {
        Formula ori = f.formula;
        Formula gen = null;
        List<TestAssertion> assertions = null;
        if (!(f.tag instanceof OpenTag tag)) {
            // Invalid tag
            return null;
        }

        String newName = "CRC-Gen-" + idCounter.incrementAndGet();
        String newPatName = newName + "-Pattern";
        Pattern newPattern = null;

        switch (newOp) {
            case "forall":
                if (expectTruth) {
                    newPattern = new Pattern(newPatName, tag.valueTrue);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    if (!simpleMode) {
                        newPattern.randomReduceByGate();
                    }
                } else {
                    newPattern = new Pattern(newPatName, tag.valueFalse);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    if (!simpleMode) {
                        newPattern.randomExtend();
                    }
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
                    if (!simpleMode) {
                        newPattern.randomExtend();
                    }
                } else {
                    newPattern = new Pattern(newPatName, tag.valueFalse);
                    if (newPattern.getData().isEmpty()) {
                        // Invalid context
                        return null;
                    }
                    if (!simpleMode) {
                        newPattern.randomReduceByGate();
                    }
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
        List<Pattern> patterns = new ArrayList<>(f.patterns);
        patterns.add(newPattern);
        List<Bfunc> bfuncs = new ArrayList<>(f.bfuncs);

        TestCase genTC = new TestCase(genRule);
        genTC.setPatterns(patterns);
        genTC.setBfuncs(bfuncs);
        genTC.setAssertions(assertions);

        genTC.setSource(tag.getSource());

        return genTC;
    }

    public static List<TestAssertion> forall(OpenTag tag, Set<String> newData, boolean expectTruth) {
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
            assertion.setRule(tag.ruleUsed+"+"+usedRule);
        }
        return assertions;
    }

    public static List<TestAssertion> exists(OpenTag tag, Set<String> newData, boolean expectTruth) {
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
            assertion.setRule(tag.ruleUsed+"+"+usedRule);
        }
        return assertions;
    }


    /**
     * Determines the rule code based on the specified binary tags and the logical operation. <br>
     * The rule code may be different with the number in TA-rules, for example, TA-implies-1 is 2 in the rule code. <br>
     * The rule code indicates the 'kernel' difference between the rules. For example, TA-implies-1 can be derived from TA-and-2, so the rule code is 2. <br>
     * The rule code is set for TA-and-1 to TA-and-6, while the rule code of other situations depends on which TA-and-1 to TA-and-6 can be derived from. <br>
     *
     * @param left  The left operand represented as a {@link ClosedTag}.
     * @param right The right operand represented as a {@link ClosedTag}.
     * @param newOp The logical operation to be applied. Supported operations are:
     *              <ul>
     *                  <li>"and" - Logical AND operation.</li>
     *                  <li>"or" - Logical OR operation.</li>
     *                  <li>"implies" - Logical implication operation.</li>
     *              </ul>
     * @return A byte representing the rule code for the specified operation and operands.
     *         Returns 0 if the operation is not recognized.
     */
    public static byte getRuleCode(ClosedTag left, ClosedTag right, String newOp) {
        return switch (newOp) {
            case "and" -> getAndRuleCode(left, right);
            case "or" -> getOrRuleCode(left, right);
            case "implies" -> getImpliesRuleCode(left, right);
            default -> 0;
        };
    }
    
    private static byte getAndRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 6;
        } else {
            return getAndRuleCode(right, left);
        }
    }

    private static byte getOrRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 6;
        } else {
            return getOrRuleCode(right, left);
        }
    }

    private static byte getImpliesRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.TRUE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.TRUE) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.FALSE) {
            return 4;
        } else {
            return 6;
        }
    }

    public static byte getRealRuleCode(ClosedTag left, ClosedTag right, String newOp) {
        return switch (newOp) {
            case "and" -> getRealAndRuleCode(left, right);
            case "or" -> getRealOrRuleCode(left, right);
            case "implies" -> getRealImpliesRuleCode(left, right);
            default -> 0;
        };
    }
    
    private static byte getRealAndRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 6;
        } else {
            return getRealAndRuleCode(right, left);
        }
    }

    private static byte getRealOrRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 6;
        } else {
            return getRealOrRuleCode(right, left);
        }
    }

    private static byte getRealImpliesRuleCode(ClosedTag left, ClosedTag right) {
        if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.TRUE) {
            return 1;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.FALSE) {
            return 2;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.TRUE) {
            return 3;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.FALSE) {
            return 4;
        } else if (left.truth == ClosedTag.Truth.TRUE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 5;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.TRUE) {
            return 6;
        } else if (left.truth == ClosedTag.Truth.FALSE && right.truth == ClosedTag.Truth.UNKNOWN) {
            return 7;
        } else if (left.truth == ClosedTag.Truth.UNKNOWN && right.truth == ClosedTag.Truth.FALSE) {
            return 8;
        } else {
            return 8;
        }
    }

    public static String getRuleName(ClosedTag left, ClosedTag right, String newOp) {
        byte ruleCode = getRealRuleCode(left, right, newOp);
        return switch (newOp) {
            case "and" -> "TA-and-" + ruleCode;
            case "or" -> "TA-or-" + ruleCode;
            case "implies" -> "TA-implies-" + ruleCode;
            default -> "";
        };
    }
}
