package com.wenzejin.core.mr.assertion;

import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;

import java.util.Set;

public class LinksExceptAssertion implements TestAssertion {
    static {
        // 在 TestAssertionRegistry 中注册该断言，以便测试用例的序列化和反序列化
        TestAssertionRegistry.register(LinksExceptAssertion.class);
    }

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
        LinksExceptAssertion that = (LinksExceptAssertion) obj;
        return linkSet.equals(that.linkSet);
    }
}
