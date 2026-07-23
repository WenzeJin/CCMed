package com.wenzejin.core.mr.ccm;

import com.wenzejin.core.result.Link;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClosedTag implements Tag {

    public enum Truth {
        TRUE, FALSE, UNKNOWN
    }

    public Truth truth;
    public Set<Link> links;
    public String ruleUsed;
    public List<Integer> source;

    public ClosedTag(Truth truth, Set<Link> links) {
        this.truth = truth;
        this.links = links;
        ruleUsed = "";
        source = new ArrayList<Integer>();
    }

    @Override
    public void addSource(int sourceId) {
        if (!source.contains(sourceId)) {
            source.add(sourceId);
        }
    }

    @Override
    public List<Integer> getSource() {
        return source;
    }

    @Override
    public void addAllSources(List<Integer> sourceIds) {
        for (Integer sourceId : sourceIds) {
            if (!source.contains(sourceId)) {
                source.add(sourceId);
            }
        }
    }

    public void flip() {
        if (truth == Truth.TRUE) {
            truth = Truth.FALSE;
        } else if (truth == Truth.FALSE) {
            truth = Truth.TRUE;
        }
    }

    @Override
    public String toString() {
        return "OperatorTag{" +
                "truth=" + truth +
                ", links=" + links +
                ", according to rule: " + ruleUsed +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ClosedTag that = (ClosedTag) obj;
        return truth == that.truth && links.equals(that.links);
    }

    @Override
    public int hashCode() {
        int result = truth.hashCode();
        result = 31 * result + links.hashCode();
        return result;
    }
}
