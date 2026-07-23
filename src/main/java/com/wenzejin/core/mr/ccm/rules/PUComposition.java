package com.wenzejin.core.mr.ccm.rules;

import com.wenzejin.core.mr.ccm.ClosedTag;
import com.wenzejin.core.mr.ccm.UtilFunctions;
import com.wenzejin.core.mr.ccm.PrimitiveUnit;
import com.wenzejin.core.mr.ccm.OpenTag;
import com.wenzejin.core.mr.ccm.Tag;
import com.wenzejin.core.rule.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PUComposition {

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
    public static PrimitiveUnit unifyForQuant(PrimitiveUnit f, PrimitiveUnit g, String newOp) {
        Formula leftFormula = f.formula.clone();
        Formula rightFormula = g.formula.clone();
        Formula gen = null;
        OpenTag newTag = null;

        if (!(f.tag instanceof OpenTag fTag) || !(g.tag instanceof OpenTag gTag)) {
            // Invalid tags
            return null;
        }
        
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

        if (newTag == null) {
            return null;
        }

        newTag.addAllSources(fTag.getSource());
        newTag.addAllSources(gTag.getSource());

        String newVar = newTag.variable;
        Map<String, String> bindMap = new HashMap<>();
        bindMap.put(fTag.variable, newVar);
        bindMap.put(gTag.variable, newVar);

        List<Pattern> patterns = new ArrayList<>();
        for (Pattern p : f.patterns) {
            patterns.add(p.copy());
        }
        for (Pattern p : g.patterns) {
            patterns.add(p.copy());
        }
        List<Bfunc> bfuncs = new ArrayList<>();
        for (Bfunc bfunc: f.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        for (Bfunc bfunc: g.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        UtilFunctions.rebindBfuncs(bfuncs, bindMap);
        gen.rebindVariables(bindMap);

        var newUnit = new PrimitiveUnit(gen, newTag, patterns, bfuncs);

        // deal with the oriRules
        if (f.oriRules != null && g.oriRules != null) {
            newUnit.oriRules = new ArrayList<>();
            newUnit.oriRules.addAll(f.oriRules);
            newUnit.oriRules.addAll(g.oriRules);
        } else if (f.oriRules != null) {
            newUnit.oriRules = new ArrayList<>(f.oriRules);
            newUnit.oriRules.add(g.oriRule);
        } else if (g.oriRules != null) {
            newUnit.oriRules = new ArrayList<>(g.oriRules);
            newUnit.oriRules.add(f.oriRule);
        }

        return newUnit;
    }

    public static PrimitiveUnit unifyForBin(PrimitiveUnit f, PrimitiveUnit g, String newOp) {
        Formula leftFormula = f.formula.clone();
        Formula rightFormula = g.formula.clone();
        Formula gen = null;
        ClosedTag newTag = null;

        if (!(f.tag instanceof ClosedTag fTag) || !(g.tag instanceof ClosedTag gTag)) {
            // Invalid tags
            return null;
        }

        switch (newOp) {
            case "and":
                gen = new OpConnector(OpConnector.Op.AND, leftFormula, rightFormula);
                newTag = and(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "and";
                }
                break;
            case "or":
                gen = new OpConnector(OpConnector.Op.OR, leftFormula, rightFormula);
                newTag = or(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "or";
                }
                break;
            case "implies":
                gen = new OpConnector(OpConnector.Op.IMPLIES, leftFormula, rightFormula);
                newTag = implies(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "implies";
                }
                break;
            default:
                break;
        }

        if (newTag == null) {
            return null;
        }

        newTag.addAllSources(fTag.getSource());
        newTag.addAllSources(gTag.getSource());

        List<Pattern> patterns = new ArrayList<>();
        for (Pattern p : f.patterns) {
            patterns.add(p.copy());
        }
        for (Pattern p : g.patterns) {
            patterns.add(p.copy());
        }
        List<Bfunc> bfuncs = new ArrayList<>();
        for (Bfunc bfunc: f.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        for (Bfunc bfunc: g.bfuncs) {
            bfuncs.add(bfunc.clone());
        }

        var newUnit = new PrimitiveUnit(gen, newTag, patterns, bfuncs);

        // deal with the oriRules
        if (f.oriRules != null && g.oriRules != null) {
            newUnit.oriRules = new ArrayList<>();
            newUnit.oriRules.addAll(f.oriRules);
            newUnit.oriRules.addAll(g.oriRules);
        } else if (f.oriRules != null) {
            newUnit.oriRules = new ArrayList<>(f.oriRules);
            newUnit.oriRules.add(g.oriRule);
        } else if (g.oriRules != null) {
            newUnit.oriRules = new ArrayList<>(g.oriRules);
            newUnit.oriRules.add(f.oriRule);
        }

        return newUnit;
    }

    public static PrimitiveUnit negation(PrimitiveUnit pu) {
        Formula newFormula = new OpNot(pu.formula.clone());
        Tag tag = pu.tag;
        Tag newTag = null;
        if (tag instanceof OpenTag qTag) {
            newTag = notInsert(qTag);
        } else if (tag instanceof ClosedTag bTag) {
            newTag = notInsert(bTag);
        }
        List<Pattern> patterns = new ArrayList<>();
        for (Pattern p : pu.patterns) {
            patterns.add(p.copy());
        }
        List<Bfunc> bfuncs = new ArrayList<>();
        for (Bfunc bfunc: pu.bfuncs) {
            bfuncs.add(bfunc.clone());
        }
        var newUnit = new PrimitiveUnit(newFormula, newTag, patterns, bfuncs);

        // deal with the oriRules
        if (pu.oriRules != null) {
            newUnit.oriRules = new ArrayList<>(pu.oriRules);
        } else {
            newUnit.oriRule = pu.oriRule;
            newUnit.oriRules = new ArrayList<>();
            newUnit.oriRules.add(pu.oriRule);
        }

        return newUnit;
    }


    /* Unification for quantifier tags */

    private static OpenTag and(OpenTag f, OpenTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        OpenTag newTag = new OpenTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.retainAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.addAll(g.valueFalse);

        newTag.ruleUsed = "TU-and";

        return newTag;
    }

    private static OpenTag or(OpenTag f, OpenTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        OpenTag newTag = new OpenTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = "TU-or";

        return newTag;
    }

    private static OpenTag implies(OpenTag f, OpenTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        OpenTag newTag = new OpenTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = "TU-implies";

        return newTag;
    }

    private static ClosedTag and(ClosedTag f, ClosedTag g) {
        if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, UtilFunctions.cartesian(f.links, g.links));
        } else if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.FALSE, new HashSet<>(g.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.FALSE, new HashSet<>(f.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.FALSE, UtilFunctions.union(f.links, g.links));
        }
        return null;
    }

    private static ClosedTag or(ClosedTag f, ClosedTag g) {
        if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, UtilFunctions.union(f.links, g.links));
        } else if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, new HashSet<>(f.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, new HashSet<>(g.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.FALSE, UtilFunctions.cartesian(f.links, g.links));
        }
        return null;
    }

    private static ClosedTag implies(ClosedTag f, ClosedTag g) {
        if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, new HashSet<>(g.links));
        } else if (f.truth == ClosedTag.Truth.TRUE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.FALSE, UtilFunctions.cartesian(UtilFunctions.flipSet(f.links), g.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.TRUE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, UtilFunctions.union(UtilFunctions.flipSet(f.links), g.links));
        } else if (f.truth == ClosedTag.Truth.FALSE && g.truth == ClosedTag.Truth.FALSE) {
            return new ClosedTag(ClosedTag.Truth.TRUE, UtilFunctions.flipSet(f.links));
        }
        return null;
    }

    private static ClosedTag notInsert(ClosedTag f) {
        ClosedTag newTag = null;
        if (f.truth == ClosedTag.Truth.TRUE) {
            newTag = new ClosedTag(ClosedTag.Truth.FALSE, UtilFunctions.flipSet(f.links));
        } else if (f.truth == ClosedTag.Truth.FALSE) {
            newTag = new ClosedTag(ClosedTag.Truth.TRUE, UtilFunctions.flipSet(f.links));
        }
        if (newTag != null) {
            newTag.addAllSources(f.getSource());
        }
        return newTag;
    }

    private static OpenTag notInsert(OpenTag f) {
        OpenTag newTag = new OpenTag(f.variable);
        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.addAllSources(f.getSource());
        return newTag;
    }
}
