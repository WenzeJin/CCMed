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

    Formula clone();

    String toString();

}
