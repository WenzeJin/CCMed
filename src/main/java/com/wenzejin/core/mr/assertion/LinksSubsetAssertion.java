package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksSubsetAssertion implements TestAssertion {

    private final Set<Link> linkSet;

    public LinksSubsetAssertion(Set<Link> linkSet) {
        this.linkSet = linkSet;
    }

    @Override
    public boolean assertTest(Result result) {
        return result.links.containsAll(linkSet);
    }

    @Override
    public String toString() {
        return "LinksSubsetAssertion{" +
                "linkSet=" + linkSet +
                ", according to rule: " + rule +
                '}';
    }


    private String rule;

    @Override
    public void setRule(String rule) {
        this.rule = rule;
    }

    @Override
    public String getRule() {
        return rule;
    }
}
