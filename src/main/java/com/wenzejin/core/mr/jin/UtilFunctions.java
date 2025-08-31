package com.wenzejin.core.mr.jin;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;

import java.util.*;

public class UtilFunctions {

    public static List<Set<Link>> attribution(TestCase tc) {
        Result result = tc.getResult();
        if (result == null) {
            return null;
        }

        // self guard: only connective formula is allowed
        List<String> availTypes = List.of("and", "or", "implies");
        Formula formula = tc.getRule().formula;
        if (formula == null || !availTypes.contains(formula.getNodeType())) {
            return null;
        }
        Formula leftFormula = ((OpConnector) formula).left;
        Formula rightFormula = ((OpConnector) formula).right;

        Set<String> leftVars = leftFormula.collectVariables();
        Set<String> rightVars = rightFormula.collectVariables();

        Set<Link> leftLinks = new HashSet<>();
        Set<Link> rightLinks = new HashSet<>();

        for (Link link: result.links) {
            Link leftLink = new Link(link.type);
            Link rightLink = new Link(link.type);

            for (var bind: link.vaSet) {
                if (leftVars.contains(bind.getKey())) {
                    leftLink.vaSet.add(bind);
                }
                if (rightVars.contains(bind.getKey())) {
                    rightLink.vaSet.add(bind);
                }
            }

            if (!leftLink.vaSet.isEmpty()) {
                leftLinks.add(leftLink);
            }
            if (!rightLink.vaSet.isEmpty()) {
                rightLinks.add(rightLink);
            }
        }

        return List.of(leftLinks, rightLinks);
    }

    public static Set<Link> flipSet(Set<Link> links) {
        Set<Link> newLinks = new HashSet<>();
        for (Link link: links) {
            Link newLink = link.clone();
            newLink.flip();
            newLinks.add(newLink);
        }
        return newLinks;
    }

    public static Set<Link> cartesian(Set<Link> a, Set<Link> b) {
        Set<Link> res = new HashSet<>();
        if (!a.isEmpty() && !b.isEmpty()) {
            for (Link al: a) {
                for (Link bl: b) {
                    Link concat = al.concat(bl);
                    res.add(concat);
                }
            }
        } else {
            res = union(a, b);
        }
        return res;
    }

    public static Set<Link> union(Set<Link> a, Set<Link> b) {
        Set<Link> res = new HashSet<>();
        res.addAll(a);
        res.addAll(b);
        return res;
    }

    /**
     * Extract the possible values of a variable which makes the formula's truth value to be true or false
     * @param links the links of the formula
     * @param truth true for true, false for false
     * @param variable the variable to be extracted
     * @return the possible values of the variable
     */
    public static Set<String> val(Set<Link> links, boolean truth, String variable) {
        var type = truth ? Link.Type.SATISFIED : Link.Type.VIOLATED;
        Set<String> res = new HashSet<>();
        for (Link link: links) {
            if (link.type == type) {
                for (var bind: link.vaSet) {
                    if (bind.getKey().equals(variable)) {
                        res.add(bind.getValue());
                    }
                }
            }
        }
        return res;
    }

    public static Set<Link> syncCartesian(Set<Link> a, Set<Link> b, String var, Set<String> target) {
        Set<Link> res = new HashSet<>();

        for (String c: target) {
            for (Link al: a) {
                for (Link bl: b) {
                    if (al.vaSet.stream().anyMatch(x -> x.getKey().equals(var) && x.getValue().equals(c)) &&
                            bl.vaSet.stream().anyMatch(x -> x.getKey().equals(var) && x.getValue().equals(c))) {
                        Link concat = al.concat(bl);
                        res.add(concat);
                    }
                }
            }
        }

        return res;
    }

    public static void rebindLinks(Set<Link> links, Map<String, String> variableMap) {
        for (Link link: links) {
            link.rebindVariables(variableMap);
        }
    }

    public static void rebindBfuncs(List<Bfunc> bfuncs, Map<String, String> variableMap) {
        for (Bfunc bfunc: bfuncs) {
            bfunc.rebindVariables(variableMap);
        }
    }

    public static Set<Link> linksProjection(Set<Link> links, String var, Set<String> target) {
        Set<Link> res = new HashSet<>();

        for (Link link: links) {
            for (var pair: link.vaSet) {
                if (pair.getKey().equals(var) && target.contains(pair.getValue())) {
                    res.add(link);
                }
            }
        }

        return res;
    }


}
