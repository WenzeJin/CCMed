package com.wenzejin.core.rule;

import java.util.*;

public class LiteralBfunc implements Formula{
    public static class Param implements Cloneable{
        public String pos;
        public String var;
        public String field;

        public Param(String pos, String var, String field) {
            this.pos = pos;
            this.var = var;
            this.field = field;
        }

        public String toXmlString() {
            return "<param pos = \"" + pos + "\" var = \"" + var + "\" field = \"" + field + "\"/>\n";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Param other = (Param) o;
            return pos.equals(other.pos) && var.equals(other.var) && field.equals(other.field);
        }

        @Override
        public int hashCode() {
            return Objects.hash("Param", pos, var, field);
        }

        @Override
        public Param clone() {
            try {
                return (Param) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError("Cloning not supported for Param class", e);
            }
        }
    }

    public List<Param> params;

    public String name;

    public LiteralBfunc(String name, List<Param> params) {
        this.params = params;
        this.name = name;
    }

    public LiteralBfunc() {
        this.params = new ArrayList<Param>();
    }

    public void addParam(String pos, String var, String field) {
        this.params.add(new Param(pos, var, field));
    }

    @Override
    public boolean isValid() {
        return name != null && !name.isEmpty() && !params.isEmpty();
    }

    @Override
    public String getNodeType() {
        return "bfunc";
    }

    @Override
    public String toXmlString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<bfunc name = \"").append(name).append("\">\n");
        for (Param param : params) {
            sb.append(param.toXmlString());
        }
        sb.append("</bfunc>\n");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiteralBfunc that = (LiteralBfunc) o;
        // We do not care the order of the params, because every param has a pos attribute.
        Set<Param> thisParam = new HashSet<>(params);
        Set<Param> thatParam = new HashSet<>(that.params);
        return name.equals(that.name) && thisParam.equals(thatParam);
    }

    @Override
    public int hashCode() {
        return Objects.hash("Bfunc", name, params);
    }

    @Override
    public LiteralBfunc clone() {
        try {
            LiteralBfunc clonedOp = (LiteralBfunc) super.clone();

            if (params != null) {
                List<Param> copiedList = new ArrayList<>();
                for (Param param : params) {
                    copiedList.add(param.clone());  // 深拷贝每个 Param 对象
                }
                clonedOp.params = copiedList;
            }

            return clonedOp;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloning not supported for LiteralBfunc class", e);
        }
    }
}
