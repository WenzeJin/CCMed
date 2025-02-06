package com.wenzejin.core.mr.crc;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.rule.Formula;
import com.wenzejin.core.rule.OpConnector;
import com.wenzejin.core.util.TestCase;

import java.util.*;

public class CRCFunc {

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
}
