package com.wenzejin.core.mr.jin;

import java.util.List;

public interface Tag {
    /**
     * Adds a source ID to the tag.
     * @param sourceId The source ID to add.
     */
    void addSource(int sourceId);

    /**
     * Gets the list of source IDs associated with the tag.
     * @return List of source IDs.
     */
    java.util.List<Integer> getSource();

    /**
     * Adds multiple source IDs to the tag.
     * @param sourceIds The list of source IDs to add.
     */
    void addAllSources(List<Integer> sourceIds);
}
