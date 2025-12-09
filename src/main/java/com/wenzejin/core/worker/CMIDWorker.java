package com.wenzejin.core.worker;


import com.fasterxml.jackson.databind.JsonNode;
import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;


public class CMIDWorker implements TestWorker {

    static Logger logger = LogManager.getLogger("CMIDWorker");

    // Configurable Arguments
    private final String workspaceRoot;
    private final String jarTargetPath;
    private final Map<String, String> properties;

    // UUID
    private final UUID uuid;

    Path workspace;
    Path copiedJar;

    public CMIDWorker(String workspace, Map<String, Object> args) {
        workspaceRoot = workspace;
        jarTargetPath = (String) args.get("std.jar-target-path");
        var argsNode = (JsonNode) args.get("cmid");
        JSONObject cmidArgs = new JSONObject(argsNode.toString());
        JSONObject cmidProps = cmidArgs.getJSONObject("properties");
        properties = new HashMap<>();
        for (String key: cmidProps.keySet()) {
            properties.put(key, cmidProps.get(key).toString());
        }

        uuid = UUID.randomUUID();

        logger.info("Created CMIDWorker with UUID: {}", uuid.toString());
    }

    /* A variant of std worker should implement the method below */

    /**
     * Use the given path to generate the cmd line for execution.
     */
    private List<String> getCmdLine(Path target) {
        generateConfigFile();

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(target.toString());
        cmd.add("cn.edu.nju.Main");
        cmd.add(workspace.resolve("config.properties").toString());
        cmd.add("auto");
        cmd.add("total");

        return cmd;
    }

    private void generateConfigFile() {
        /*
            ruleFilePath=./rules.xml
            patternFilePath=./pattern.xml
            dataFilePath=./data.txt
            oracleFilePath=./data_oracle.txt
            logFilePath=./result.txt
            processor=xlinkit
            analysisFilePath=./analysis.txt
            changeHandlerType=static-change-based
            schedule=Immed
            taskNum=4
            randomFunctionInputPath=./bfunc_in.txt
            isInitRandomFunction=true
            randomFunctionOutputPath=./bfunc_out.txt
         */
        Map<String, String> config = new HashMap<>();
        config.put("ruleFilePath", workspace.resolve("rule.xml").toString());
        config.put("dataFilePath", workspace.resolve("data.txt").toString());
        config.put("patternFilePath", workspace.resolve("pattern.xml").toString());
        config.put("oracleFilePath", workspace.resolve("data_oracle.txt").toString());
        //create empty oracle file
        try {
            Path oraclePath = workspace.resolve("data_oracle.txt");
            Files.createFile(oraclePath);
        } catch (IOException e) {
            logger.fatal("Failed to create oracle file in workspace.", e);
        }
        config.put("logFilePath", workspace.resolve("incs.txt").toString());
        config.put("analysisFilePath", workspace.resolve("analysis.txt").toString());
        config.put("changeHandlerType", "static-change-based");
        config.put("schedule", "Immed");
        config.put("taskNum", "4");
        config.put("randomFunctionInputPath", workspace.resolve("bfunc_in.txt").toString());
        config.put("isInitRandomFunction", "true");
        config.put("randomFunctionOutputPath", workspace.resolve("bfunc_out.txt").toString());

        for (String key : properties.keySet()) {
            config.put(key, properties.get(key));
        }

        Path configPath = workspace.resolve("config.properties");
        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            for (Map.Entry<String, String> entry : config.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.fatal("Failed to write config file in workspace.", e);
        }
    }


    private Path copyFileToWorkspace(String sourcePath, Path workspace, String fileDescription) throws IOException {
        Path sourceFile = Paths.get(sourcePath);
        if (!Files.exists(sourceFile)) {
            logger.fatal("{} not found at: {}", fileDescription, sourcePath);
            throw new FileNotFoundException(fileDescription + " not found at: " + sourcePath);
        }

        Path destinationFile = workspace.resolve(sourceFile.getFileName());
        if (Files.isDirectory(sourceFile)) {
            // Recursively copy directory with try-with-resources
            try (var paths = Files.walk(sourceFile)) {
                paths.forEach(path -> {
                    Path destPath = destinationFile.resolve(sourceFile.relativize(path));
                    try {
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(destPath);
                        } else {
                            Files.copy(path, destPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        logger.fatal("Failed to copy {} to workspace: {}", fileDescription, destPath, e);
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } else {
            try {
                Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.fatal("Failed to copy {} to workspace: {}", fileDescription, destinationFile, e);
                throw new IOException("Failed to copy " + fileDescription + " to workspace: " + destinationFile, e);
            }
        }

        return destinationFile;
    }

    /**
     * Initialize temporary workspace for InfuseWorker
     * the workspace path will be "[workspaceRoot]/infuse_worker_[UUID]"
     * @throws IOException if initialize failed.
     */
    @Override
    public void initWorkspace() throws IOException {
        String workspacePath = getWorkspacePath();
        workspace = Paths.get(workspacePath);

        // Create workspace directory if it doesn't exist
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            logger.fatal("Failed to create workspace directory: {}", workspacePath, e);
            throw new IOException("Failed to create workspace directory: " + workspacePath, e);
        }

        // Copy jar file to workspace
        copiedJar = copyFileToWorkspace(jarTargetPath, workspace, "Jar target file");
    }



    /**
     * Destroy temporary workspace.
     * @throws IOException if destroy failed.
     */
    @Override
    public void destroyWorkspace() throws IOException {
        if (workspace == null) {
            logger.warn("Workspace not initialized yet but trying to destroy it.");
            return;
        }

        if (Files.exists(workspace)) {
            try {
                // Recursively delete the workspace directory and its contents
                Files.walk(workspace)
                        .sorted(Comparator.reverseOrder()) // Ensure directories are deleted after their contents
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.debug("Deleted: {}", path);
                            } catch (IOException e) {
                                logger.error("Failed to delete: {} due to: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                logger.error("Failed to clean up workspace: {}", workspace, e);
                throw new IOException("Failed to clean up workspace: " + workspace, e);
            }
        } else {
            logger.info("Workspace does not exist: {}", workspace);
        }

        workspace = null;
    }

    /**
     * Run a single test case.
     * @param tc TestCase to run
     *           The result will be stored in the TestCase object.
     * @return 0 if success, 1 if failed.
     */
    @Override
    public int execute(TestCase tc, boolean source) {
        int error = 0;
        cleanWorkspace();
        List<Rule> rules = List.of(tc.getRule());
        Set<Pattern> patterns = new HashSet<>(tc.getPatterns());
        List<Bfunc> bfuncs = tc.getBfuncs();

        try {
            generateRuleInput(rules);
            generateBfuncInput(bfuncs);

            Set<String> used = tc.getRule().collectPatterns();

            generatePatternAndContextInput(patterns, used);
            int ret = execute();

            if (ret != 0) {
                return ret;
            }

            Result result;

            try {
                result = analyzeResult(tc.getRule());
            } catch (IllegalStateException e) {
                logger.warn("Exception occurred when analyzing result for test case: {}", tc.getId(), e);
                result = null;
            }

            if (result != null) {
                tc.setResult(analyzeResult(tc.getRule()));
                tc.setExecuted(true);
            } else {
                logger.warn("Failed to analyze result for test case: {}", tc.getId());
                return 1;
            }

        } catch (IOException e) {
            logger.warn("IOException occurred when running test case: {}", tc.getId());
            error = 1;
        }
        return error;
    }

    @Override
    public void logTestCase(Path logDir) {
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            return; // 创建目录失败就不继续拷贝
        }

        String[] toCopy =
                {"config.properties", "data.txt", "pattern.xml", "rule.xml", "bfunc_in.txt", "bfunc_out.txt", "incs.txt", "stdout.txt", "stderr.txt"};

        for (String path : toCopy) {
            try {
                Path from = workspace.resolve(path);
                if (!Files.exists(from)) {
                    continue;
                }
                Files.copy(from, logDir.resolve(path), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {}
        }
    }

    private void cleanWorkspace() {
        String[] toClean =
                {"data_oracle.txt","config.properties", "data.txt", "pattern.xml", "rule.xml", "bfunc_in.txt", "bfunc_out.txt", "incs.txt", "stdout.txt", "stderr.txt"};

        for (String path : toClean) {
            try {
                Path delete = workspace.resolve(path);
                if (!Files.exists(delete)) {
                    continue;
                }
                Files.delete(delete);
            } catch (IOException ignored) {}
        }
    }

    private int execute() {
        Path absoluteJar = copiedJar.toAbsolutePath();
        Path outputInc = workspace.resolve("incs.json").toAbsolutePath();
        Path logPath = workspace.resolve("stdout.txt").toAbsolutePath();
        Path errPath = workspace.resolve("stderr.txt").toAbsolutePath();

        try {
            Files.deleteIfExists(outputInc);
        } catch (IOException e) {
            logger.error("Failed to delete existing output file: {}", outputInc, e);
            return 1;
        }

        var args = getCmdLine(absoluteJar);

        ProcessBuilder builder = new ProcessBuilder(args)
                .redirectOutput(logPath.toFile())
                .redirectError(errPath.toFile());

        try {
            Process process = builder.start();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                logger.warn("Execution time limit exceeded. Terminating process.");
                process.destroy();
                return 2;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Process exited with non-zero code: {}", exitCode);
                return 1;
            }
        } catch (IOException e) {
            logger.error("IOException occurred during process execution.", e);
            return 1;
        } catch (InterruptedException e) {
            logger.error("Process execution was interrupted.", e);
            return 1;
        }

        return 0;
    }

    private String getWorkspacePath() {
        return Paths.get(workspaceRoot, "cmid_worker_" + uuid.toString()).toString();
    }

    private void generatePatternAndContextInput(Set<Pattern> patterns, Set<String> used) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path contextPath = workspace.resolve("data.txt");
        Path patternPath = workspace.resolve("pattern.xml");

        try (BufferedWriter cw = new BufferedWriter(new FileWriter(contextPath.toString()))) {
            try (BufferedWriter pw = new BufferedWriter(new FileWriter(patternPath.toString()))) {
                pw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                pw.write("<patterns>\n");

                int cnt = 0;
                for (Pattern pat: patterns) {
                    if (!used.contains(pat.getName())) {
                        continue;
                    }

                    String patString = String.format("<pattern>\n" +
                            "\t<id>%s</id>\n" +
                            "\t<freshness>20000</freshness>\n" +
                            "\t<category>location</category>\n" +
                            "\t<subject>any</subject>\n" +
                            "\t<predicate>any</predicate>\n" +
                            "\t<object>any</object>\n" +
                            "\t<site>any</site>\n" +
                            "</pattern>\n", pat.getName());
                    pw.write(patString);

                    for (String data: pat.getData()) {
                        String dataLine = String.format(
                                "+,%s,%s,%s,%s,%.4f,%.4f,%d,0",
                                pat.getName(),
                                data,
                                getTimeString(),
                                getRandomString(),
                                114 + Math.random(),
                                22 + Math.random(),
                                (int) (100 * Math.random())
                        );
                        cw.write(dataLine);
                        cw.newLine();
                        cnt++;
                    }

                }

                pw.write("</patterns>\n");
            }
        }
    }

    private void generateBfuncInput(List<Bfunc> bfuncs) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path bfuncInputPath = Path.of(workspace.toString(), "bfunc_in.txt");
        try (FileOutputStream fos = new FileOutputStream(bfuncInputPath.toString())) {
            for (Bfunc bfunc: bfuncs) {
                String name = "#" + bfunc.getName() + "\n";
                StringBuilder mb = new StringBuilder();
                for (String key: bfunc.getValueMap().keySet()) {
                    mb.append(key).append(" ").append(bfunc.getValueMap().get(key));
                    mb.append("\n");
                }
                fos.write((name + mb).getBytes());
            }
        }
    }


    private void generateRuleInput(List<Rule> rules) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        Path ruleInputPath = Path.of(workspace.toString(), "rule.xml");
        try (FileOutputStream fos = new FileOutputStream(ruleInputPath.toString())) {
            fos.write("<?xml version=\"1.0\"?>\n<rules>\n".getBytes());
            for (Rule rule: rules) {
                fos.write(rule.toXmlString().getBytes());
            }
            fos.write("</rules>".getBytes());
        }
    }

    private Result analyzeResult(Rule rule) throws IOException {
        String ruleId = rule.getId();
        Path incsPath = workspace.resolve("incs.txt");
        var lines = Files.readAllLines(incsPath);

        List<Integer> sectionIndices = new ArrayList<>();

        // 1 找出所有 START 段的索引
        var startPattern = java.util.regex.Pattern.compile("START\\[(\\d+)]");
        for (String line : lines) {
            Matcher m = startPattern.matcher(line);
            if (m.find()) {
                sectionIndices.add(Integer.parseInt(m.group(1)));
            }
        }

        if (sectionIndices.isEmpty()) {
            throw new IllegalStateException("文件中未找到任何 START 段");
        }

        // 2 找到最大编号
        int lastIndex = Collections.max(sectionIndices);

        // 3 找到该段的起止行
        int startLine = -1, endLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("START[" + lastIndex + "]")) startLine = i;
            if (lines.get(i).contains("END[" + lastIndex + "]")) { endLine = i; break; }
        }

        if (startLine == -1 || endLine == -1) {
            throw new IllegalStateException("找不到最后一段 [" + lastIndex + "]");
        }

        // 4 提取该段内容
        List<String> sectionLines = lines.subList(startLine, endLine + 1);

        boolean truth = false;
        var links = new HashSet<Link>();

        boolean foundTruth = false;
        // 5 查找该段的 rule true/false 信息
        var ruleFlagPattern = java.util.regex.Pattern.compile(
                "\\[" + java.util.regex.Pattern.quote(ruleId) + ",(true|false)]"
        );
        for (String line : sectionLines) {
            Matcher m = ruleFlagPattern.matcher(line);
            if (m.find()) {
                truth = Boolean.parseBoolean(m.group(1));
                foundTruth = true;
                break;
            }
        }
        if (!foundTruth) {
            throw new IllegalStateException("在最后一段中找不到规则 " + ruleId + " 的真值信息");
        }

        // 6 找出 TOTAL[n] 和 END[n] 之间的部分
        int totalLine = -1;
        for (int i = 0; i < sectionLines.size(); i++) {
            if (sectionLines.get(i).contains("TOTAL[" + lastIndex + "]")) {
                totalLine = i;
                break;
            }
        }

        var var2pat = rule.formula.collectVariablePatternPairs();
        var pat2var = new HashMap<String, String>();
        for (var entry : var2pat.entrySet()) {
            pat2var.put(entry.getValue(), entry.getKey());
        }

        if (totalLine != -1) {
            for (int i = totalLine + 1; i < sectionLines.size(); i++) {
                String line = sectionLines.get(i);

                if (line.startsWith(ruleId)) {
                    // 提取 rule 后的内容
                    var link = new Link(truth ? Link.Type.SATISFIED : Link.Type.VIOLATED);

                    String content = line.substring(ruleId.length()).trim();
                    var pairs = content.split(" ");
                    for (String pair : pairs) {
                        if (pair.startsWith("(")) {
                            pair = pair.substring(1, pair.length() - 1); // 去掉括号
                            var kv = pair.split(",");
                            if (kv.length == 2) {
                                String varName = pat2var.get(kv[0]);
                                String value = kv[1];
                                if (value.startsWith("ctx_")) {
                                    value = value.substring(4);
                                }
                                link.addVaPair(varName, value);
                            }
                        }
                    }

                    links.add(link);
                }

            }
        }


        return new Result(truth, links);
    }


    private String getRandomString() {
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            StringBuilder sb = new StringBuilder();
            Random random = new Random();

            for (int i = 0; i < 6; i++) {
                int index = random.nextInt(chars.length());
                sb.append(chars.charAt(index));
            }

            return sb.toString();
    }

    private String getTimeString() {
        LocalDateTime now = LocalDateTime.now();

        // 格式化到微秒（6 位）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSSSSS");

        return now.format(formatter);
    }
}
