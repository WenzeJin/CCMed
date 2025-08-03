package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksExtendAssertion implements TestAssertion {
    static {
        // Register this assertion in the TestAssertionRegistry for serialization and deserialization
        TestAssertionRegistry.register(LinksExtendAssertion.class);
    }

    private final Set<Link> linkSet;

    public LinksExtendAssertion(Set<Link> linkSet) {
        this.linkSet = linkSet;
    }

    @Override
    public boolean assertTest(Result result) {
        // Check if all links in linkSet are present in the result's links, or there is a bigger link containing all va set
        // of a link in linkSet
        for (Link link : linkSet) {
            boolean found = false;
            for (Link resultLink : result.links) {
                if (resultLink.equals(link) || resultLink.vaSet.containsAll(link.vaSet)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false; // If any link in linkSet is not found in result, assertion fails
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "LinksExtendAssertion{" +
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        LinksExtendAssertion that = (LinksExtendAssertion) obj;
        return linkSet.equals(that.linkSet);
    }
}
