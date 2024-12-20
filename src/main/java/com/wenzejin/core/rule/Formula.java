package com.wenzejin.core.rule;

public interface Formula extends Cloneable {

    boolean isValid();

    String getNodeType();

    String toXmlString();

    Formula clone();

}
