package com.wenzejin.core.worker;

import com.wenzejin.core.db.TestCaseDB;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.result.VAPair;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;
import com.wenzejin.core.util.TestCase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

import org.json.JSONObject;
import org.json.JSONArray;

public class InfuseWorker implements TestWorker{

    // Configurable Arguments
    private final String workspaceRoot;
    private final String jarTargetPath;
    private final String bfuncClassPath;
    private final List<String> arguments;

    // UUID
    private final UUID uuid;

    private static final Logger logger = LogManager.getLogger("InfuseWorker");

    private Path workspace;
    private Path copiedJar;
    private Path copiedBfuncClass;

    // 匹配 context 的名字和它其中我们关心的 data 字段的值
    private final Map<Integer, String> contextMap;

    public InfuseWorker(String workspaceRoot, String jarTargetPath, String bfuncClassPath, List<String> args) {
        this.workspaceRoot = workspaceRoot;
        this.jarTargetPath = jarTargetPath;
        this.bfuncClassPath = bfuncClassPath;
        arguments = args;
        uuid = UUID.randomUUID();
        contextMap = new HashMap<>();
    }

    public InfuseWorker(Map<String, Object> args) {
        workspaceRoot = (String) args.get("infuse.workspace-root");
        jarTargetPath = (String) args.get("infuse.jar-target-path");
        bfuncClassPath = (String) args.get("infuse.bfunc-class-path");
        arguments = (List<String>) args.get("infuse.arguments");
        uuid = UUID.randomUUID();
        contextMap = new HashMap<>();
    }


    /**
     * Initialize temporary workspace for InfuseWorker
     * the workspace path will be "[workspaceRoot]/infuse_worker_[UUID]"
     * @throws IOException if initialize failed.
     */
    @Override
    public void initWorkspace() throws IOException {
        String workspacePath = getWorkspacePath();
        File workspaceDir = new File(workspacePath);
        if (!workspaceDir.exists()) {
            if (!workspaceDir.mkdirs()) {
                logger.fatal("Failed to create workspace directory: {}", workspacePath);
                throw new IOException("Failed to create workspace directory: " + workspacePath);
            }
        }
        workspace = workspaceDir.toPath();

        // 拷贝 jar 文件到工作目录
        File sourceJar = new File(jarTargetPath);
        if (!sourceJar.exists()) {
            logger.fatal("Jar target file not found at: {}", jarTargetPath);
            throw new FileNotFoundException("Jar target file not found at: " + jarTargetPath);
        }
        File destJar = new File(workspacePath, sourceJar.getName());
        Files.copy(sourceJar.toPath(), destJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        copiedJar = destJar.toPath();

        // 拷贝 bfunc class 文件到工作目录
        File sourceBfunc = new File(bfuncClassPath);
        if (!sourceBfunc.exists()) {
            logger.fatal("Bfunc class file not found at: {}", bfuncClassPath);
            throw new FileNotFoundException("Bfunc class file not found at: " + bfuncClassPath);
        }
        File destBfunc = new File(workspacePath, sourceBfunc.getName());
        Files.copy(sourceBfunc.toPath(), destBfunc.toPath(), StandardCopyOption.REPLACE_EXISTING);
        copiedBfuncClass = destBfunc.toPath();

        logger.info("Workspace created at: {}", workspace);
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
            // 使用递归删除工作目录及其内容
            Files.walk(workspace)
                    .sorted(Comparator.reverseOrder()) // 确保目录在文件后删除
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.fatal("Failed to delete: {} due to: {}", path, e.getMessage());
                        }
                    });

            logger.info("Workspace destroyed at: {}", workspace);
        }

        workspace = null;
    }

    /**
     * @param tc
     */
    @Override
    public void singleRun(TestCase tc) {
        contextMap.clear();
        List<Rule> rules = List.of(tc.getRule());
        Set<Pattern> patterns = new HashSet<>(tc.getPatterns());
        List<Bfunc> bfuncs = tc.getBfuncs();

        try {
            generateRuleInput(rules);
            generateBfuncInput(bfuncs);
            generatePatternAndContextInput(patterns);
        } catch (IOException e) {
            logger.fatal("IOException occurred when trying to create files for running test: {}", tc.getId());
        }

        execute();

        analyzeSingleResult(tc);
    }

    private void execute() {
        Path absoluteJar = copiedJar.toAbsolutePath();
        Path absoluteBfunc = copiedBfuncClass.toAbsolutePath();
        Path patternInput = Path.of(workspace.toString(), "pattern.xml").toAbsolutePath();
        Path contextInput = Path.of(workspace.toString(), "data.txt").toAbsolutePath();
        Path contextPool = Path.of(workspace.toString(), "context_pool.json").toAbsolutePath();
        Path ruleInput = Path.of(workspace.toString(), "rule.xml").toAbsolutePath();
        Path output = Path.of(workspace.toString(), "incs.txt").toAbsolutePath();
        Path outputIncs = Path.of(workspace.toString(), "incs.json").toAbsolutePath();
        Path outputCCT = Path.of(workspace.toString(), "cct.txt").toAbsolutePath();
        Path logPath = Path.of(workspace.toString(), "infuse_log.txt").toAbsolutePath();
        Path errPath = Path.of(workspace.toString(), "infuse_err.txt").toAbsolutePath();

        try {
            Files.delete(outputIncs);
        } catch (NoSuchFileException _) {

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> args = new ArrayList<>(List.of(
                "java", "-jar", absoluteJar.toString(),
                "-test",
                "-rules", ruleInput.toString(),
                "-bfuncs", absoluteBfunc.toString(),
                "-contextpool", contextPool.toString(),
                "-incs", outputIncs.toString(),
                "-cct", outputCCT.toString()
        ));

        args.addAll(arguments);

        ProcessBuilder builder = new ProcessBuilder(args);

        builder.redirectOutput(new File(logPath.toString()));
        builder.redirectError(new File(errPath.toString()));

        try {
            // 启动进程
            Process process = builder.start();

            // 等待进程完成并获取退出码
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("INFUSE exit with non-zero code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.fatal("Exception occurred when executing.");
        }
    }

    private String getWorkspacePath() {
        return Paths.get(workspaceRoot, "infuse_worker_" + uuid.toString()).toString();
    }

    private void generatePatternAndContextInput(Set<Pattern> patterns) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path contextPoolPath = Path.of(workspace.toString(), "context_pool.json");
        try (FileOutputStream fos = new FileOutputStream(contextPoolPath.toString())) {
            JSONArray contextPool = new JSONArray();
            int context_cnt = 0;
            for (Pattern pat: patterns) {
                String name = pat.getName();
                JSONArray contextArray = new JSONArray();
                for (String data: pat.getData()) {
                    context_cnt++;
                    JSONObject context = new JSONObject();
                    context.put("ctx_id", "" + context_cnt);
                    context.put("fields", new JSONObject().put("data", data));
                    contextArray.put(context);
                }
                contextPool.put(new JSONObject().put("pat_id", name).put("contexts", contextArray));
            }
            fos.write(contextPool.toString(2).getBytes());
        }
    }

    // TODO: change List<Bfunc> to Set<Bfunc>
    private void generateBfuncInput(List<Bfunc> bfuncs) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path bfuncInputPath = Path.of(workspace.toString(), "bfunc_in.txt");
        try (FileOutputStream fos = new FileOutputStream(bfuncInputPath.toString())) {
            for (Bfunc bfunc: bfuncs) {
                String name = "#" + bfunc.getName() + "\n";
                StringBuilder vb = new StringBuilder("@");
                for (String param: bfunc.getParamList()) {
                    vb.append(param).append(",");
                }
                vb.append("\n");
                StringBuilder mb = new StringBuilder();
                for (String key: bfunc.getValueMap().keySet()) {
                    mb.append(key).append(" ").append(bfunc.getValueMap().get(key));
                    mb.append("\n");
                }
                fos.write((name + vb + mb).getBytes());
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

    private void analyzeSingleResult(TestCase tc) {

        // Inc example: {"rule_3_365_73":{"truth":true,"links":[{"vaSet":[{"var":"v2","value":{"ctx_id":"1","fields":{"data":"0"}}}],"type":"SATISFIED"},{"vaSet":[{"var":"v1","value":{"ctx_id":"7","fields":{"data":"0"}}}],"type":"SATISFIED"}]}}
        // Parse the incs.json file to Result

        Path incsPath = Path.of(workspace.toString(), "incs.json");

        try (BufferedReader reader = new BufferedReader(new FileReader(incsPath.toString()))) {
            // read lines
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            // parse json
            JSONObject incs = new JSONObject(sb.toString());
            Result result = null;
            for (String ruleId: incs.keySet()) {
                if (!ruleId.equals(tc.getRule().id)) {
                    continue;
                }
                JSONObject rule = incs.getJSONObject(ruleId);
                result = new Result(rule.getBoolean("truth"));

                // parse links
                Set<Link> links = new HashSet<>();
                for (Object linkObj: rule.getJSONArray("links")) {
                    JSONObject link = (JSONObject) linkObj;
                    Link.Type type = link.getString("type").equals("SATISFIED") ? Link.Type.SATISFIED : Link.Type.VIOLATED;
                    Set<VAPair> vaSet = new HashSet<>();
                    for (Object vaObj: link.getJSONArray("vaSet")) {
                        JSONObject va = (JSONObject) vaObj;
                        vaSet.add(new VAPair(va.getString("var"), va.getJSONObject("value").getJSONObject("fields").getString("data")));
                    }
                    links.add(new Link(type, vaSet));
                }
                result.links = links;
            }
            tc.setResult(result);
            tc.setChecked(true);
        } catch (IOException e) {
            logger.warn("IOException occurred when trying to read incs.json, maybe INFUSE failed to generate incs.json");
        }
    }
}
