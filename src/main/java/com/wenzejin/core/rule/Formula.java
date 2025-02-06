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

}
