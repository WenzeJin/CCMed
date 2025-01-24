package com.wenzejin.core.result;

import java.util.HashSet;
import java.util.Set;

public class Result {

    public boolean truth;

    public Set<Link> links;

    public Result(boolean truth, Set<Link> links) {
        this.truth = truth;
        this.links = links;
    }

    public Result(boolean truth) {
        this.truth = truth;
        links = new HashSet<>();
    }

    @Override
    public String toString() {
        return "{" + truth + ", " + links + "}";
    }
}
