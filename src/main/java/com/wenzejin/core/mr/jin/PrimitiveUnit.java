package com.wenzejin.core.mr.jin;

import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.Pattern;

import java.util.List;
import java.util.ArrayList;

public class PrimitiveUnit {

    public Formula formula;
    public List<Pattern> patterns;
    public List<Bfunc> bfuncs;
    
    public Tag tag;
    
    // track the initial rule that this unit used
    public String oriRule;

    // track the rules when composing
    public List<String> oriRules;


    public PrimitiveUnit(Formula formula, Tag tag, List<Pattern> patterns, List<Bfunc> bfuncs) {
        this.formula = formula;
        this.tag = tag;
        this.patterns = patterns;
        this.bfuncs = bfuncs;
        this.oriRule = "";
        this.oriRules = null;
    }

    public void initializeForComposition() {
        this.oriRules = new ArrayList<>();
        this.oriRules.add(oriRule);
    }

    @Override
    public String toString() {
        return "{" + formula + ", " + tag + "}";
    }
}
