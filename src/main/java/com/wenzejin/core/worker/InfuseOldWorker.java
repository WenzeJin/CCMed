package com.wenzejin.core.worker;

import java.nio.file.Path;
import java.util.*;

public class InfuseOldWorker extends StandardWorker {

    public InfuseOldWorker(String workspace, Map<String, Object> args) {
        super(workspace, args);
    }

    @Override
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path bf, Path inc, List<String> args) {
        List<String> cmdLine = new ArrayList<>(List.of(
            "java", "-jar", target.toString(),
            "-t",
            "-rf", rule.toString(),
            "-bf", bf.toString(),
            "-cp", cp.toString(),
            "-o", inc.toString()
        ));
        cmdLine.addAll(args);
        return cmdLine;
    }
}
