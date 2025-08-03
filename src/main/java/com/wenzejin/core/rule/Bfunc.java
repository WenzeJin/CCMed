package com.wenzejin.core.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bfunc implements Cloneable {

    private final String name;

    private int paramNum;

    private List<String> paramList;

    private final Map<String, Boolean> valueMap;

    public Bfunc(String name, int paramNum) {
        this.name = name;
        this.paramNum = paramNum;
        this.valueMap = new HashMap<>();
        paramList =  new ArrayList<>();
    }

    public void addValue(String value, boolean result) {
        valueMap.put(value, result);
    }

    public Map<String, Boolean> getValueMap() {
        return valueMap;
    }

    public String getName() {
        return name;
    }

    public void setParamList(List<String> paramList) {
        this.paramList = paramList;
    }

    public List<String> getParamList() {
        return paramList;
    }

    public void rebindVariables(Map<String, String> bindMap) {
        for (String var: paramList) {
            if (bindMap.containsKey(var)) {
                paramList.set(paramList.indexOf(var), bindMap.get(var));
            }
        }
    }


    @Override
    public String toString() {
        return name + " : param=" + paramList + " valueMap=" + valueMap;
    }

    @Override
    public Bfunc clone() {
        Bfunc bfunc = new Bfunc(name, paramNum);
        bfunc.setParamList(new ArrayList<>(paramList));
        for (Map.Entry<String, Boolean> entry : valueMap.entrySet()) {
            bfunc.addValue(entry.getKey(), entry.getValue());
        }
        return bfunc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bfunc bfunc = (Bfunc) o;
        return name.equals(bfunc.name) && valueMap.equals(bfunc.valueMap) && paramList.equals(bfunc.paramList);
    }

    static private void generateCombinations(List<Pattern> patterns, int index, String current, List<String> result) {
        if (index == patterns.size()) {
            result.add("(" + current + ")");
        } else {
            for (String data : patterns.get(index).getData()) {
                if (index == 0) {
                    generateCombinations(patterns, index + 1, data, result);
                } else {
                    generateCombinations(patterns, index + 1, current + "," + data, result);
                }
            }
        }
    }

    static public List<Bfunc> enumerateBfuncs(String namePrefix, List<Pattern> patterns) {
        int paramNum = patterns.size();

        List<String> combinations = new ArrayList<>();
        generateCombinations(patterns, 0, "", combinations);
        int combinationNum = combinations.size();

        List<Bfunc> bfuncs = new ArrayList<>();

        for (int i = 0; i < Math.pow(2, combinationNum); i++) {
            String binary = Integer.toBinaryString(i);
            while (binary.length() < combinationNum) {
                binary = "0" + binary;
            }
            Bfunc bfunc = new Bfunc(namePrefix + "_" + i, paramNum);
            for (int j = 0; j < combinationNum; j++) {
                bfunc.addValue(combinations.get(j), binary.charAt(j) == '1');
            }
            bfuncs.add(bfunc);
        }

        return bfuncs;
    }

    static private void generateBinaryCombinations(int index, int paramNum, String current, List<String> result) {
        if (index == paramNum) {
            result.add("(" + current + ")");
        } else {
            if (index == 0) {
                generateBinaryCombinations(index + 1, paramNum, current + "0", result);
                generateBinaryCombinations(index + 1, paramNum, current + "1", result);
            } else {
                generateBinaryCombinations(index + 1, paramNum, current + ",0", result);
                generateBinaryCombinations(index + 1, paramNum, current + ",1", result);
            }
        }
    }

    static public List<Bfunc> enumerateBinaryBfunc(String namePrefix, int paramNum) {
        List<String> combinations = new ArrayList<>();

        generateBinaryCombinations(0, paramNum, "", combinations);
        int combinationNum = combinations.size();

        List<Bfunc> bfuncs = new ArrayList<>();

        for (int i = 0; i < Math.pow(2, combinationNum); i++) {
            String binary = Integer.toBinaryString(i);
            while (binary.length() < combinationNum) {
                binary = "0" + binary;
            }
            Bfunc bfunc = new Bfunc(namePrefix + "_" + i, paramNum);
            for (int j = 0; j < combinationNum; j++) {
                bfunc.addValue(combinations.get(j), binary.charAt(j) == '1');
            }
            bfuncs.add(bfunc);
        }

        return bfuncs;
    }

}
