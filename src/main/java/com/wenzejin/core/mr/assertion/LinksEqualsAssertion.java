package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.mr.TestAssertion;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksEqualsAssertion implements TestAssertion {
    private final Set<Link> linkSet;

    public LinksEqualsAssertion(Set<Link> linkSet) {
        this.linkSet = linkSet;
    }

    @Override
    public boolean assertTest(Result result) {
        return result.links.containsAll(linkSet) && linkSet.containsAll(result.links);
    }

    @Override
    public String toString() {
        return "LinksEqualsAssertion{" +
                "linkSet=" + linkSet +
                '}';
    }

}
