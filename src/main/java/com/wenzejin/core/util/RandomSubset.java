package com.wenzejin.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class RandomSubset {
    public static <E> List<E> getRandom(List<E> list, int maxSize) {
        if (list.size() <= maxSize) {
            return list;
        }
        List<E> copy = new ArrayList<>(list);
        Collections.shuffle(copy);
        return copy.subList(0, maxSize);
    }
}
