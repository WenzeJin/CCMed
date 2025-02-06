package com.wenzejin.core.mr.crc;

import com.wenzejin.core.result.Link;

import java.util.Set;

public class BinaryTag implements Tag {

    public enum Truth {
        TRUE, FALSE, UNKNOWN
    }

    public Truth truth;
    public Set<Link> links;

    public BinaryTag(Truth truth, Set<Link> links) {
        this.truth = truth;
        this.links = links;
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
        BinaryTag that = (BinaryTag) obj;
        return truth == that.truth && links.equals(that.links);
    }

    @Override
    public int hashCode() {
        int result = truth.hashCode();
        result = 31 * result + links.hashCode();
        return result;
    }
}
