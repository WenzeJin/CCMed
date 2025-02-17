package com.wenzejin.core.mr.crc.rules;

import com.wenzejin.core.mr.crc.BinaryTag;
import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.mr.crc.FormulaWithTag;
import com.wenzejin.core.mr.crc.QuantifierTag;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.Pattern;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TagUnification {

    private static final AtomicInteger idCounter = new AtomicInteger();

    public static FormulaWithTag unify(FormulaWithTag f, FormulaWithTag g, String newOp) {
        Formula leftFormula = f.formula.clone();
        Formula rightFormula = g.formula.clone();
        Formula gen = null;
        QuantifierTag newTag = null;

        if (!(f.tag instanceof QuantifierTag) || !(g.tag instanceof QuantifierTag)) {
            // Invalid tags
            return null;
        }

        QuantifierTag fTag = (QuantifierTag) f.tag;
        QuantifierTag gTag = (QuantifierTag) g.tag;

        switch (newOp) {
            case "and":
                gen = new OpConnector(OpConnector.Op.AND, leftFormula, rightFormula);
                newTag = and(fTag, gTag);
                break;
            case "or":
                gen = new OpConnector(OpConnector.Op.OR, leftFormula, rightFormula);
                newTag = or(fTag, gTag);
                break;
            case "implies":
                gen = new OpConnector(OpConnector.Op.IMPLIES, leftFormula, rightFormula);
                newTag = implies(fTag, gTag);
                break;
            default:
                break;
        }

        String newVar = newTag.variable;
        Map<String, String> bindMap = new HashMap<>();
        bindMap.put(fTag.variable, newVar);
        bindMap.put(gTag.variable, newVar);

        List<Pattern> patterns = new ArrayList<>();
        patterns.addAll(f.patterns);
        patterns.addAll(g.patterns);
        List<Bfunc> bfuncs = new ArrayList<>();
        for (Bfunc bfunc: f.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        for (Bfunc bfunc: g.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        CRCFunc.rebindBfuncs(bfuncs, bindMap);
        gen.rebindVariables(bindMap);

        // TODO: 解决 parent testcase 跟踪问题
        return new FormulaWithTag(gen, newTag, patterns, bfuncs);
    }

    private static QuantifierTag and(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);
        Map<String, String> bindMap = new HashMap<>();
        bindMap.put(f.variable, newVar);
        bindMap.put(g.variable, newVar);
        Set<Link> fTrueLinks = new HashSet<>(f.linksTrue);
        Set<Link> gTrueLinks = new HashSet<>(g.linksTrue);
        Set<Link> fFalseLinks = new HashSet<>(f.linksFalse);
        Set<Link> gFalseLinks = new HashSet<>(g.linksFalse);

        CRCFunc.rebindLinks(fTrueLinks, bindMap);
        CRCFunc.rebindLinks(gTrueLinks, bindMap);
        CRCFunc.rebindLinks(fFalseLinks, bindMap);
        CRCFunc.rebindLinks(gFalseLinks, bindMap);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.retainAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.addAll(g.valueFalse);

        newTag.linksTrue = CRCFunc.syncCartesian(fTrueLinks, gTrueLinks, newVar, newTag.valueTrue);
        newTag.linksFalse = CRCFunc.union(fFalseLinks, gFalseLinks);

        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-and";

        return newTag;
    }

    private static QuantifierTag or(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);
        Map<String, String> bindMap = new HashMap<>();
        bindMap.put(f.variable, newVar);
        bindMap.put(g.variable, newVar);
        Set<Link> fTrueLinks = new HashSet<>(f.linksTrue);
        Set<Link> gTrueLinks = new HashSet<>(g.linksTrue);
        Set<Link> fFalseLinks = new HashSet<>(f.linksFalse);
        Set<Link> gFalseLinks = new HashSet<>(g.linksFalse);

        CRCFunc.rebindLinks(fTrueLinks, bindMap);
        CRCFunc.rebindLinks(gTrueLinks, bindMap);
        CRCFunc.rebindLinks(fFalseLinks, bindMap);
        CRCFunc.rebindLinks(gFalseLinks, bindMap);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.linksTrue = CRCFunc.union(fTrueLinks, gTrueLinks);
        newTag.linksFalse = CRCFunc.syncCartesian(fFalseLinks, gFalseLinks, newVar, newTag.valueFalse);

        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-or";

        return newTag;
    }

    private static QuantifierTag implies(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);
        Map<String, String> bindMap = new HashMap<>();
        bindMap.put(f.variable, newVar);
        bindMap.put(g.variable, newVar);
        Set<Link> fTrueLinks = new HashSet<>(f.linksTrue);
        Set<Link> gTrueLinks = new HashSet<>(g.linksTrue);
        Set<Link> fFalseLinks = new HashSet<>(f.linksFalse);
        Set<Link> gFalseLinks = new HashSet<>(g.linksFalse);

        CRCFunc.rebindLinks(fTrueLinks, bindMap);
        CRCFunc.rebindLinks(gTrueLinks, bindMap);
        CRCFunc.rebindLinks(fFalseLinks, bindMap);
        CRCFunc.rebindLinks(gFalseLinks, bindMap);

        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.linksTrue = CRCFunc.union(CRCFunc.flipSet(fFalseLinks), gTrueLinks);
        newTag.linksFalse = CRCFunc.syncCartesian(CRCFunc.flipSet(fTrueLinks), gFalseLinks, newVar, newTag.valueFalse);

        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-implies";

        return newTag;
    }
}
