package com.wenzejin.core.mr.crc.rules;

import com.wenzejin.core.mr.crc.CRCFunc;
import com.wenzejin.core.mr.crc.FormulaWithTag;
import com.wenzejin.core.mr.crc.QuantifierTag;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.rule.Pattern;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TagUnification {

    private static final AtomicInteger idCounter = new AtomicInteger();

    /**
     * Unifies two formulas with their respective tags into a new formula with a combined tag.
     * <p>
     * The method performs the following steps:
     * 1. Clones the formulas from the input objects to avoid modifying the originals.
     * 2. Validates that both input tags are instances of {@code QuantifierTag}.
     * 3. Creates a new formula by applying the specified logical operator to the input formulas.
     * 4. Combines the tags of the input formulas using the appropriate tag unification logic.
     * 5. Updates the parent ID of the new tag by concatenating the parent IDs of the input tags.
     * 6. Rebinds variables in the formulas and boolean functions using a mapping derived from the input tags.
     * 7. Combines the patterns and boolean functions from the input formulas into the resulting formula.
     * </p>
     *
     * @param f       The first formula with its associated tag and metadata.
     * @param g       The second formula with its associated tag and metadata.
     * @param newOp   The logical operator to unify the formulas. Supported values are "and", "or", and "implies".
     * @return        A new {@code FormulaWithTag} object representing the unified formula and tag,
     *                or {@code null} if the tags of the input formulas are invalid.
     */
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
        newTag.parentID = fTag.parentID + "," + gTag.parentID;

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
        return new FormulaWithTag(gen, newTag, patterns, bfuncs);
    }

    private static QuantifierTag and(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.retainAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.addAll(g.valueFalse);


        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-and";

        return newTag;
    }

    private static QuantifierTag or(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-or";

        return newTag;
    }

    private static QuantifierTag implies(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = f.ruleUsed + "," + g.ruleUsed + ",TU-implies";

        return newTag;
    }
}
