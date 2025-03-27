package com.wenzejin.core.mr.crc;

import com.wenzejin.core.TestCase;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;

import java.util.List;

public class FormulaWithTag {

    public Formula formula;
    public Tag tag;
    public List<Pattern> patterns;
    public List<Bfunc> bfuncs;
    public TestCase parentTestCase;


    public FormulaWithTag(Formula formula, Tag tag, List<Pattern> patterns, List<Bfunc> bfuncs) {
        this.formula = formula;
        this.tag = tag;
        this.patterns = patterns;
        this.bfuncs = bfuncs;
        this.parentTestCase = null;
    }

    public FormulaWithTag(Formula formula, Tag tag, List<Pattern> patterns, List<Bfunc> bfuncs, TestCase parentTestCase) {
        this.formula = formula;
        this.tag = tag;
        this.patterns = patterns;
        this.bfuncs = bfuncs;
        this.parentTestCase = parentTestCase;
    }

    @Override
    public String toString() {
        return "{" + formula + ", " + tag + "}";
    }
}
