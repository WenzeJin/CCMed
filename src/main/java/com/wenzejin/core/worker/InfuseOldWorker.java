package com.wenzejin.core.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

public class InfuseOldWorker extends StandardWorker {
    static Logger logger = LogManager.getLogger("InfuseOldWorker");

    public InfuseOldWorker(Map<String, Object> args) {
        super(args);
    }

    @Override
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path inc, List<String> args) {
        List<String> cmdLine = new ArrayList<>(List.of(
            "java", "-jar", target.toString(),
            "-t",
            "-rf", rule.toString(),
            "-bf", bfc.toString(),
            "-cp", cp.toString(),
            "-o", inc.toString()
        ));
        cmdLine.addAll(args);
        return cmdLine;
    }
}
