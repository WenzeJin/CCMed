/**
 * Rule class of test cases.
 * Author: Wenze Jin
 */
package com.wenzejin.core.rule;


public class Rule {
    public String id;
    public Formula formula;

    public Rule(String id, Formula formula) {
        this.id = id;
        this.formula = formula;
    }

    public boolean isValid() {
        if (id == null || id.isEmpty() || formula == null) return false;
        return formula.isValid();
    }
}
