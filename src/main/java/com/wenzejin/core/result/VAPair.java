package com.wenzejin.core.result;

public class VAPair {
    private String key;
    private String value;

    public VAPair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "<" + key + "," + value + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VAPair vaPair = (VAPair) obj;
        return key.equals(vaPair.key) && value.equals(vaPair.value);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + value.hashCode();
    }
}
