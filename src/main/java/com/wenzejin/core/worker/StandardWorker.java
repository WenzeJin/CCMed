package com.wenzejin.core.worker;


import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.result.VAPair;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Standard worker is for those cc which support the new interface for testing.
 */
public class StandardWorker implements TestWorker {

    static Logger logger = LogManager.getLogger("StandardWorker");

    private static final int PROCESS_TIMEOUT_SECONDS = 6;
    private static final int EXIT_CODE_TIMEOUT = 2;
    private static final int EXIT_CODE_ERROR = 1;
    private static final int EXIT_CODE_SUCCESS = 0;

    // Configurable Arguments
    private final String workspaceRoot;
    private final String jarTargetPath;
    private final String bfuncClassPath;
    private final List<String> arguments;

    // UUID
    private final UUID uuid;

    Path workspace;
    Path copiedJar;
    Path copiedBfuncClass;

    public StandardWorker(String workspace, Map<String, Object> args) {
        workspaceRoot = workspace;
        jarTargetPath = (String) args.get("std.jar-target-path");
        bfuncClassPath = (String) args.get("std.bfunc-class-path");
        Object argumentsObj = args.get("std.arguments");

        if (argumentsObj instanceof List<?>) {
            arguments = ((List<?>) argumentsObj).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } else {
            arguments = new ArrayList<>();
        }

        uuid = UUID.randomUUID();
    }

    /* A variant of std worker should implement the method below */

    /**
     * Builds the command line for executing the SUT.
     * <p>
     * Default implementation uses {@code java -jar} with standard flags
     * ({@code -rules}, {@code -bfuncs}, {@code -contextpool}, {@code -incs}).
     * Subclasses may override for SUTs that need different flags or a native binary.
     *
     * @param target path to the SUT jar or binary
     * @param rule   path to the generated rule XML
     * @param cp     path to the context pool JSON
     * @param bfc    path to the bfunc class file
     * @param bf     path to the bfunc input data file ({@code bfunc_in.txt})
     * @param inc    path for the output inconsistencies file
     * @param args   additional command-line arguments from config
     * @return the full command line as a list of strings
     */
    protected List<String> getCmdLine(Path target, Path rule, Path cp, Path bfc, Path bf, Path inc, List<String> args) {
        List<String> cmdLine = new ArrayList<>(List.of(
            "java", "-jar", target.toString(),
            "-rules", rule.toString(),
            "-bfuncs", bfc.toString(),
            "-contextpool", cp.toString(),
            "-incs", inc.toString()
        ));
        cmdLine.addAll(args);
        return cmdLine;
    }

    /**
     * Initialize temporary workspace for StandardWorker
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
        copiedJar = WorkerFileUtils.copyFileToWorkspace(jarTargetPath, workspace, "Jar target file");

        // Copy bfunc class file to workspace
        copiedBfuncClass = WorkerFileUtils.copyFileToWorkspace(bfuncClassPath, workspace, "Bfunc class file");
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
     * @return RawExecutionResult containing exit code, logs, and artifacts.
     */
    @Override
    public RawExecutionResult execute(TestCase tc) {
        cleanWorkspace();
        List<Rule> rules = List.of(tc.getRule());
        Set<Pattern> patterns = new HashSet<>(tc.getPatterns());
        List<Bfunc> bfuncs = tc.getBfuncs();

        int exitCode = 1;
        long duration = 0;
        String stdout = "";
        String stderr = "";
        Map<String, String> artifacts = new HashMap<>();

        try {
            generateRuleInput(rules);
            artifacts.put("rule.xml", Files.readString(workspace.resolve("rule.xml")));
            generateBfuncInput(bfuncs);
            artifacts.put("bfunc_in.txt", Files.readString(workspace.resolve("bfunc_in.txt")));

            Set<String> used = tc.getRule().collectPatterns();

            generatePatternAndContextInput(patterns, used);
            artifacts.put("context_pool.json", Files.readString(workspace.resolve("context_pool.json")));

            long startTime = System.currentTimeMillis();
            exitCode = execute();
            duration = System.currentTimeMillis() - startTime;

            // Read outputs
            Path logPath = workspace.resolve("stdout.txt");
            if (Files.exists(logPath)) {
                stdout = Files.readString(logPath);
            }

            Path errPath = workspace.resolve("stderr.txt");
            if (Files.exists(errPath)) {
                stderr = Files.readString(errPath);
            }

            Path incsPath = workspace.resolve("incs.json");
            if (Files.exists(incsPath)) {
                artifacts.put("incs.json", Files.readString(incsPath));
            }

            Path bfuncOutPath = workspace.resolve("bfunc_out.txt");
            if (Files.exists(bfuncOutPath)) {
                artifacts.put("bfunc_out.txt", Files.readString(bfuncOutPath));
            }

        } catch (IOException e) {
            logger.warn("IOException occurred when running test case: {}", tc.getId(), e);
            stderr += "\nException: " + e.getMessage();
        }

        return new RawExecutionResult(exitCode, stdout, stderr, duration, artifacts);
    }


    private void cleanWorkspace() {
        String[] toClean =
                {"context_pool.json", "rule.xml", "bfunc_in.txt", "bfunc_out.txt", "incs.json", "stdout.txt", "stderr.txt"};

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
        Path absoluteBfuncClass = copiedBfuncClass.toAbsolutePath();
        Path bfuncInput = workspace.resolve("bfunc_in.txt").toAbsolutePath();
        Path contextPool = workspace.resolve("context_pool.json").toAbsolutePath();
        Path ruleInput = workspace.resolve("rule.xml").toAbsolutePath();
        Path outputInc = workspace.resolve("incs.json").toAbsolutePath();
        Path logPath = workspace.resolve("stdout.txt").toAbsolutePath();
        Path errPath = workspace.resolve("stderr.txt").toAbsolutePath();

        try {
            Files.deleteIfExists(outputInc);
        } catch (IOException e) {
            logger.error("Failed to delete existing output file: {}", outputInc, e);
            return 1;
        }

        var args = getCmdLine(absoluteJar, ruleInput, contextPool, absoluteBfuncClass, bfuncInput, outputInc, arguments);

        ProcessBuilder builder = new ProcessBuilder(args)
                .redirectOutput(logPath.toFile())
                .redirectError(errPath.toFile());

        try {
            Process process = builder.start();

            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Execution time limit exceeded. Terminating process.");
                process.destroy();
                return EXIT_CODE_TIMEOUT;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Process exited with non-zero code: {}", exitCode);
                return EXIT_CODE_ERROR;
            }
        } catch (IOException e) {
            logger.error("IOException occurred during process execution.", e);
            return EXIT_CODE_ERROR;
        } catch (InterruptedException e) {
            logger.error("Process execution was interrupted.", e);
            return EXIT_CODE_ERROR;
        }

        return EXIT_CODE_SUCCESS;
    }

    private String getWorkspacePath() {
        return Paths.get(workspaceRoot, "std_worker_" + uuid.toString()).toString();
    }

    private void generatePatternAndContextInput(Set<Pattern> patterns, Set<String> used) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path contextPoolPath = Path.of(workspace.toString(), "context_pool.json");
        try (FileOutputStream fos = new FileOutputStream(contextPoolPath.toString())) {
            JSONArray contextPool = new JSONArray();
            int context_cnt = 0;
            for (Pattern pat: patterns) {
                String name = pat.getName();
                if (!used.contains(name)) {
                    continue;
                }
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

    @Override
    public Result parseResult(RawExecutionResult raw, TestCase tc) {
        String incsContent = raw.artifacts.get("incs.json");
        if (incsContent == null) {
            return null;
        }

        JSONObject incs = new JSONObject(incsContent);
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
                JSONArray link = (JSONArray) linkObj;
                Link.Type type = rule.getBoolean("truth") ? Link.Type.SATISFIED : Link.Type.VIOLATED;
                Set<VAPair> vaSet = new HashSet<>();
                for (Object vaObj: link) {
                    JSONObject va = (JSONObject) vaObj;
                    vaSet.add(new VAPair(va.getString("var"), va.getJSONObject("value").getJSONObject("fields").getString("data")));
                }
                links.add(new Link(type, vaSet));
            }
            result.links = links;
        }
        return result;
    }

}
