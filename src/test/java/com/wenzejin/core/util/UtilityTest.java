package com.wenzejin.core.util;

import com.wenzejin.core.worker.WorkerFileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RandomSubsetTest {

    @Test
    @DisplayName("returns all elements when maxSize >= list size")
    void returnsAllWhenMaxSizeExceedsSize() {
        List<String> input = List.of("a", "b", "c");
        List<String> result = RandomSubset.getRandom(input, 10);
        assertEquals(3, result.size());
        assertTrue(result.containsAll(input));
    }

    @Test
    @DisplayName("returns subset of exact maxSize when maxSize < list size")
    void returnsSubsetWhenMaxSizeSmaller() {
        List<Integer> input = new ArrayList<>();
        for (int i = 0; i < 100; i++) input.add(i);

        List<Integer> result = RandomSubset.getRandom(input, 5);
        assertEquals(5, result.size());
        // All elements in result should be from the original
        for (Integer i : result) {
            assertTrue(input.contains(i));
        }
    }

    @Test
    @DisplayName("returns empty list for empty input")
    void returnsEmptyForEmptyInput() {
        List<Object> empty = List.of();
        List<Object> result = RandomSubset.getRandom(empty, 5);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("does not modify the original list")
    void doesNotModifyOriginal() {
        List<String> original = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
        List<String> copy = new ArrayList<>(original);
        RandomSubset.getRandom(original, 2);
        assertEquals(copy, original);
    }
}

class WorkerFileUtilsTest {

    @Test
    @DisplayName("should copy a file into workspace")
    void copyFileToWorkspace(@TempDir Path workspace) throws IOException {
        Path tmpFile = Files.createTempFile("source", ".txt");
        Files.writeString(tmpFile, "hello world");

        Path dest = WorkerFileUtils.copyFileToWorkspace(
                tmpFile.toString(), workspace, "Test file");

        assertTrue(Files.exists(dest));
        assertEquals("hello world", Files.readString(dest));
        assertEquals(tmpFile.getFileName(), dest.getFileName());
    }

    @Test
    @DisplayName("should throw IOException for nonexistent source")
    void throwsForNonexistentSource(@TempDir Path workspace) {
        assertThrows(IOException.class, () ->
                WorkerFileUtils.copyFileToWorkspace(
                        "/nonexistent/path/file.txt", workspace, "Missing file"));
    }
}
