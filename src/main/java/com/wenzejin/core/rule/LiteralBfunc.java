package com.wenzejin.core.rule;

import java.util.ArrayList;
import java.util.List;

public class LiteralBfunc implements Formula{
    public static class Param {
        public String pos;
        public String var;
        public String field;

        public Param(String pos, String var, String field) {
            this.pos = pos;
            this.var = var;
            this.field = field;
        }
    }

    public List<Param> params;

    public LiteralBfunc(List<Param> params) {
        this.params = params;
    }

    public LiteralBfunc() {
        this.params = new ArrayList<Param>();
    }

    public void addParam(String pos, String var, String field) {
        this.params.add(new Param(pos, var, field));
    }

    public boolean isValid() {
        return !params.isEmpty();
    }
}
