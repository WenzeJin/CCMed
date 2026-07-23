package com.wenzejin.core.worker;

import java.nio.file.*;
import java.util.*;

public class InfuseWorker extends StandardWorker {

    public InfuseWorker(String workspace, Map<String, Object> args) {
        super(workspace, args);
    }

    @Override
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path bf, Path inc, List<String> args) {
        Path cctPath = workspace.resolve("cct.txt");
        List<String> cmdLine = new ArrayList<>( List.of(
            "java", "-jar", target.toString(),
            "-test",
            "-rules", rule.toString(),
            "-bfuncs", bf.toString(),
            "-contextpool", cp.toString(),
            "-incs", inc.toString(),
            "-cct", cctPath.toString()
        ));
        cmdLine.addAll(args);
        return cmdLine;
    }

}
