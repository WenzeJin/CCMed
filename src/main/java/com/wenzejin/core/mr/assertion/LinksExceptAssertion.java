package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksExceptAssertion implements TestAssertion {

    private final Set<Link> linkSet;

    public LinksExceptAssertion(Set<Link> linkSet) {
        this.linkSet = linkSet;
    }

    @Override
    public boolean assertTest(Result result) {
        for (Link link: linkSet) {
            if (result.links.contains(link)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "LinksSubsetAssertion{" +
                "linkSet=" + linkSet +
                '}';
    }
}
