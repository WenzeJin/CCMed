package com.wenzejin.core.mr.jin.rules;

import com.wenzejin.core.mr.jin.BinaryTag;
import com.wenzejin.core.mr.jin.UtilFunctions;
import com.wenzejin.core.mr.jin.FormulaWithTag;
import com.wenzejin.core.mr.jin.QuantifierTag;
import com.wenzejin.core.rule.*;

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
    public static FormulaWithTag unifyForQuant(FormulaWithTag f, FormulaWithTag g, String newOp) {
        Formula leftFormula = f.formula.clone();
        Formula rightFormula = g.formula.clone();
        Formula gen = null;
        QuantifierTag newTag = null;

        if (!(f.tag instanceof QuantifierTag fTag) || !(g.tag instanceof QuantifierTag gTag)) {
            // Invalid tags
            return null;
        }

        boolean notInserted = false;
        // random insert not on left or right
        if (Math.random() > 0.75) {
            leftFormula = new OpNot(leftFormula);
            fTag = notInsert(fTag);
            notInserted = true;
        }
        if (Math.random() > 0.75) {
            rightFormula = new OpNot(rightFormula);
            gTag = notInsert(gTag);
            notInserted = true;
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

        if (notInserted) {
            newTag.ruleUsed += "+NotInserted";
        }

        newTag.addAllSources(fTag.getSource());
        newTag.addAllSources(gTag.getSource());

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
        UtilFunctions.rebindBfuncs(bfuncs, bindMap);
        gen.rebindVariables(bindMap); 
        return new FormulaWithTag(gen, newTag, patterns, bfuncs);
    }

    public static FormulaWithTag unifyForBin(FormulaWithTag f, FormulaWithTag g, String newOp) {
        Formula leftFormula = f.formula.clone();
        Formula rightFormula = g.formula.clone();
        Formula gen = null;
        BinaryTag newTag = null;

        if (!(f.tag instanceof BinaryTag fTag) || !(g.tag instanceof BinaryTag gTag)) {
            // Invalid tags
            return null;
        }

        boolean notInserted = false;
        // random insert not on left or right
        if (Math.random() > 0.75) {
            leftFormula = new OpNot(leftFormula);
            fTag = notInsert(fTag);
            notInserted = true;
        }
        if (Math.random() > 0.75) {
            rightFormula = new OpNot(rightFormula);
            gTag = notInsert(gTag);
            notInserted = true;
        }
        switch (newOp) {
            case "and":
                gen = new OpConnector(OpConnector.Op.AND, leftFormula, rightFormula);
                newTag = and(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "TU-and";
                }
                break;
            case "or":
                gen = new OpConnector(OpConnector.Op.OR, leftFormula, rightFormula);
                newTag = or(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "TU-or";
                }
                break;
            case "implies":
                gen = new OpConnector(OpConnector.Op.IMPLIES, leftFormula, rightFormula);
                newTag = implies(fTag, gTag);
                if (newTag != null) {
                    newTag.ruleUsed = "TU-implies";
                }
                break;
            default:
                break;
        }

        if (newTag == null) {
            return null;
        }

        if (notInserted) {
            newTag.ruleUsed += "+NotInserted";
        }

        newTag.addAllSources(fTag.getSource());
        newTag.addAllSources(gTag.getSource());

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

        return new FormulaWithTag(gen, newTag, patterns, bfuncs);
    }

    /* Unification for quantifier tags */

    private static QuantifierTag and(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.retainAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.addAll(g.valueFalse);

        newTag.ruleUsed = "TU-and";

        return newTag;
    }

    private static QuantifierTag or(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueTrue);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueFalse);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = "TU-or";

        return newTag;
    }

    private static QuantifierTag implies(QuantifierTag f, QuantifierTag g) {
        String newVar = "tu-gen-" + idCounter.getAndIncrement();
        QuantifierTag newTag = new QuantifierTag(newVar);

        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueTrue.addAll(g.valueTrue);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.valueFalse.retainAll(g.valueFalse);

        newTag.ruleUsed = "TU-implies";

        return newTag;
    }

    private static BinaryTag and(BinaryTag f, BinaryTag g) {
        BinaryTag newTag = null;
        if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, UtilFunctions.cartesian(f.links, g.links));
        } else if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.FALSE, new HashSet<>(g.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.FALSE, new HashSet<>(f.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.FALSE, UtilFunctions.union(f.links, g.links));
        }
        return null;
    }

    private static BinaryTag or(BinaryTag f, BinaryTag g) {
        if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, UtilFunctions.union(f.links, g.links));
        } else if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, new HashSet<>(f.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, new HashSet<>(g.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.FALSE, UtilFunctions.cartesian(f.links, g.links));
        }
        return null;
    }

    private static BinaryTag implies(BinaryTag f, BinaryTag g) {
        if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, new HashSet<>(g.links));
        } else if (f.truth == BinaryTag.Truth.TRUE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.FALSE, UtilFunctions.cartesian(UtilFunctions.flipSet(f.links), g.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.TRUE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, UtilFunctions.union(UtilFunctions.flipSet(f.links), g.links));
        } else if (f.truth == BinaryTag.Truth.FALSE && g.truth == BinaryTag.Truth.FALSE) {
            return new BinaryTag(BinaryTag.Truth.TRUE, UtilFunctions.flipSet(f.links));
        }
        return null;
    }

    private static BinaryTag notInsert(BinaryTag f) {
        BinaryTag newTag = null;
        if (f.truth == BinaryTag.Truth.TRUE) {
            newTag = new BinaryTag(BinaryTag.Truth.FALSE, UtilFunctions.flipSet(f.links));
        } else if (f.truth == BinaryTag.Truth.FALSE) {
            newTag = new BinaryTag(BinaryTag.Truth.TRUE, UtilFunctions.flipSet(f.links));
        }
        if (newTag != null) {
            newTag.addAllSources(f.getSource());
        }
        return newTag;
    }

    private static QuantifierTag notInsert(QuantifierTag f) {
        QuantifierTag newTag = new QuantifierTag(f.variable);

        newTag.valueTrue = new HashSet<>(f.valueFalse);
        newTag.valueFalse = new HashSet<>(f.valueTrue);
        newTag.addAllSources(f.getSource());

        return newTag;
    }
}
