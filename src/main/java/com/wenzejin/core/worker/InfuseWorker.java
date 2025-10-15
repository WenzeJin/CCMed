package com.wenzejin.core.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.*;
import java.util.*;

public class InfuseWorker extends StandardWorker {

    public InfuseWorker(String workspace, Map<String, Object> args) {
        super(workspace, args);
    }

    @Override
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path inc, List<String> args) {
        List<String> cmdLine = new ArrayList<>( List.of(
            "java", "-jar", target.toString(),
            "-test",
            "-rules", rule.toString(),
            "-bfuncs", bfc.toString(),
            "-contextpool", cp.toString(),
            "-incs", inc.toString(),
            "-cct", "cct.txt"
        ));
        cmdLine.addAll(args);
        return cmdLine;
    }

}
