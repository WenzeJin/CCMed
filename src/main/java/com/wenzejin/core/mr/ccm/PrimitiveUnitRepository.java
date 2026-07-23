package com.wenzejin.core.mr.ccm;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;

public class PrimitiveUnitRepository {

    /** oriRule -> units, insertion-ordered for deterministic iteration. */
    private final Map<String, List<PrimitiveUnit>> closedUnits = new LinkedHashMap<>();
    private final Map<String, List<PrimitiveUnit>> openUnits   = new LinkedHashMap<>();

    /** Flat lists for unbiased uniform random across all units. */
    private final List<PrimitiveUnit> closedFlat = new ArrayList<>();
    private final List<PrimitiveUnit> openFlat   = new ArrayList<>();

    private final Random random = new Random();

    public int getSize() {
        return closedFlat.size() + openFlat.size();
    }

    public int getClosedSize() {
        return closedFlat.size();
    }

    public int getOpenSize() {
        return openFlat.size();
    }

    public void addClosedUnit(PrimitiveUnit unit) {
        closedUnits.computeIfAbsent(unit.oriRule, k -> new ArrayList<>()).add(unit);
        closedFlat.add(unit);
    }

    public void addOpenUnit(PrimitiveUnit unit) {
        openUnits.computeIfAbsent(unit.oriRule, k -> new ArrayList<>()).add(unit);
        openFlat.add(unit);
    }

    public PrimitiveUnit getRandomClosedUnit() {
        if (closedFlat.isEmpty()) return null;
        return closedFlat.get(random.nextInt(closedFlat.size()));
    }

    public PrimitiveUnit getRandomOpenUnit() {
        if (openFlat.isEmpty()) return null;
        return openFlat.get(random.nextInt(openFlat.size()));
    }

    public PrimitiveUnit getRandomClosedUnitExcept(List<String> exceptOriRules) {
        return getRandomUnitExcept(closedUnits, exceptOriRules);
    }

    public PrimitiveUnit getRandomOpenUnitExcept(List<String> exceptOriRules) {
        return getRandomUnitExcept(openUnits, exceptOriRules);
    }

    /** O(k) where k = number of distinct oriRules — much smaller than unit count. */
    private PrimitiveUnit getRandomUnitExcept(Map<String, List<PrimitiveUnit>> index,
                                              List<String> exceptOriRules) {
        Set<String> except = new HashSet<>(exceptOriRules);
        // collect candidate keys in one pass
        List<String> keys = new ArrayList<>();
        for (String key : index.keySet()) {
            if (!except.contains(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) return null;
        String pickedKey = keys.get(random.nextInt(keys.size()));
        List<PrimitiveUnit> bucket = index.get(pickedKey);
        return bucket.get(random.nextInt(bucket.size()));
    }

}
