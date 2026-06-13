package com.wenzejin.core.worker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;


public class MGWorker extends StandardWorker {
    
    public MGWorker(String workspace, Map<String, Object> args) {
        super(workspace, args);
    }

    @Override
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path inc, List<String> args) {
        target = target.resolve("bin").resolve("app");

        try {
            var perms = Files.getPosixFilePermissions(target);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(target, perms);
        } catch (UnsupportedOperationException | IOException e) {
            logger.warn("Failed to set execute permission for {}: {}", target, e.getMessage());
        }


        List<String> cmdLine = new ArrayList<>(List.of(
                target.toString(),
                "-m", "TEST",
                "-i", cp.toString(),
                "-o", inc.toString(),
                "-r", rule.toString(),
                "-j", bfc.toString()
        ));
        cmdLine.addAll(args);

        return cmdLine;
    }

}
