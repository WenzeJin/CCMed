package com.wenzejin.core.rule;

import java.util.Map;
import java.util.Set;

public interface Formula extends Cloneable {

    boolean isValid();

    String getNodeType();

    String toXmlString();

    void rebindPatterns(Map<String, String> patternMap);

    Set<String> collectPatterns();

    void rebindBfuncs(Map<String, String> bfuncMap);

    Set<LiteralBfunc> collectBfuncs();

    void rebindVariables(Map<String, String> variableMap);

    Set<String> collectVariables();

    Formula clone();

    String toString();

    /**
     * Calculate the distance between two formulas. <br>
     * The distance is in the range of [0, 100]. <br>
     * The smaller the distance, the more similar the two formulas are.
     * The similarity comes from the structure of the formulas.
     * @param formula the formula to compare with
     * @return the distance between the two formulas
     */
    double distance(Formula formula);
}
